package io.github.rottencandy.openwizlight.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rottencandy.openwizlight.wiz.HueSat
import io.github.rottencandy.openwizlight.wiz.MIN_DIMMING
import io.github.rottencandy.openwizlight.wiz.WizClient
import io.github.rottencandy.openwizlight.wiz.WizDevice
import io.github.rottencandy.openwizlight.wiz.WizDiscovery
import io.github.rottencandy.openwizlight.wiz.WizErrorException
import io.github.rottencandy.openwizlight.wiz.WizPilot
import io.github.rottencandy.openwizlight.wiz.decodeSigningKey
import io.github.rottencandy.openwizlight.wiz.findSigningKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import io.github.rottencandy.openwizlight.wiz.WizPush
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

/**
 * @property signingKey the home's `udp_signing_key` as hex, null if none is set
 */
data class ScanState(
    val devices: List<WizDevice> = emptyList(),
    val scanning: Boolean = false,
    val error: String? = null,
    val signingKey: String? = null,
)

private const val PREFS_NAME = "settings"
private const val PREF_SIGNING_KEY = "udp_signing_key"

class DeviceListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        ScanState(signingKey = prefs.getString(PREF_SIGNING_KEY, null)),
    )
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        scan()
    }

    /** Starts a fresh scan unless one (including its pending detail fetches) is still running. */
    fun scan() {
        if (scanJob?.isActive == true) return
        _state.update { it.copy(devices = emptyList(), scanning = true, error = null) }
        scanJob = viewModelScope.launch {
            val error = try {
                WizDiscovery.discover().collect { device ->
                    _state.update { it.withDevice(device) }
                    launch { fetchSystemConfig(device) }
                    launch { fetchPilot(device) }
                }
                null
            } catch (e: IOException) {
                "Scan failed: ${e.message}"
            }
            _state.update { it.copy(scanning = false, error = error) }
        }
    }

    /**
     * Stores [hex] as the signing key, or clears it when null. Returns false, changing nothing,
     * if [hex] isn't a valid key.
     */
    fun setSigningKey(hex: String?): Boolean {
        val normalized = hex?.trim()?.lowercase()
        if (normalized != null && decodeSigningKey(normalized) == null) return false
        prefs.edit { if (normalized == null) remove(PREF_SIGNING_KEY) else putString(PREF_SIGNING_KEY, normalized) }
        _state.update { it.copy(signingKey = normalized, error = null) }
        return true
    }

    /**
     * Reads the signing key from a WiZ app local-integration export at [uri]. Fails with a
     * user-facing message if the file can't be read or doesn't hold exactly one key.
     */
    suspend fun readSigningKey(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext Result.failure(IOException("Couldn't open the file"))
            when (val keys = findSigningKeys(text)) {
                emptyList<String>() -> Result.failure(IOException("No udp_signing_key in this file"))
                else -> keys.singleOrNull()?.let { Result.success(it) }
                    ?: Result.failure(IOException("File has ${keys.size} different keys; paste the right one"))
            }
        } catch (_: JSONException) {
            Result.failure(IOException("Not a JSON file"))
        } catch (e: IOException) {
            Result.failure(IOException("Couldn't read the file: ${e.message}"))
        }
    }

    /** Re-reads a device's state and details, e.g. when its screen is opened. */
    fun refresh(device: WizDevice) {
        viewModelScope.launch {
            launch { fetchSystemConfig(device) }
            launch { fetchPilot(device) }
        }
    }

    fun setPower(device: WizDevice, on: Boolean) =
        control(device, { it.copy(on = on) }) { key -> WizClient.setPower(device.ip, on, key) }

    fun setBrightness(device: WizDevice, percent: Int) =
        control(device, { it.copy(on = true, dimming = percent) }) { key ->
            val minDimming = device.capabilities?.minDimming ?: MIN_DIMMING
            WizClient.setBrightness(device.ip, percent, minDimming, key)
        }

    /** Only offered for devices whose Kelvin range is known, so it's always clamped to it. */
    fun setColorTemp(device: WizDevice, kelvin: Int) {
        val range = device.capabilities?.kelvinRange ?: return
        control(device, { it.copy(on = true, temp = kelvin.coerceIn(range.min, range.max), color = null, scene = null) }) { key ->
            WizClient.setColorTemp(device.ip, kelvin, range, key)
        }
    }

    fun setColor(device: WizDevice, color: HueSat) =
        control(device, { it.copy(on = true, color = color, temp = null, scene = null) }) { key ->
            WizClient.setColor(device.ip, color, key)
        }

    fun setScene(device: WizDevice, sceneId: Int) =
        control(device, { it.copy(on = true, scene = sceneId, temp = null, color = null) }) { key ->
            WizClient.setScene(device.ip, sceneId, key)
        }

    fun setSpeed(device: WizDevice, speed: Int) =
        control(device, { it.copy(speed = speed) }) { key -> WizClient.setSpeed(device.ip, speed, key) }

    /**
     * Keeps every listed device registered for push updates and applies the states they push,
     * until cancelled. Meant to run only while the app is visible, since registration traffic
     * every [WizPush.KEEP_ALIVE_MS] isn't worth it in the background. If the push port can't be
     * opened, returns quietly and states only update on refresh, as before.
     */
    suspend fun receivePushUpdates() {
        try {
            coroutineScope {
                launch { keepRegistered() }
                WizPush.updates().collect { (mac, pilot) ->
                    _state.update { it.updateDevice(mac) { d -> d.copy(pilot = pilot) } }
                }
            }
        } catch (_: IOException) {
            // Port in use or network unavailable; push is best-effort.
        }
    }

    /**
     * Registers all listed devices now and then every keep-alive interval. A changed device
     * list (e.g. a new scan result) registers the new set right away.
     */
    private suspend fun keepRegistered() {
        _state.map { s -> s.devices.map { it.ip } }.distinctUntilChanged().collectLatest { ips ->
            while (true) {
                coroutineScope {
                    ips.forEach { ip ->
                        launch {
                            try {
                                WizPush.register(ip, currentKey())
                            } catch (_: IOException) {
                                // Retried on the next round.
                            }
                        }
                    }
                }
                delay(WizPush.KEEP_ALIVE_MS)
            }
        }
    }

    private fun currentKey(): ByteArray? = _state.value.signingKey?.let(::decodeSigningKey)

    /**
     * Shows [optimistic] in the UI immediately, then runs [send] with the current signing key;
     * if the device rejects or doesn't answer, restores the previous pilot and reports the error.
     */
    private fun control(
        device: WizDevice,
        optimistic: (WizPilot) -> WizPilot,
        send: suspend (ByteArray?) -> Unit,
    ) {
        val previous = _state.value.devices.find { it.mac == device.mac }?.pilot ?: return
        _state.update { it.updateDevice(device.mac) { d -> d.copy(pilot = optimistic(previous)) } }
        viewModelScope.launch {
            try {
                send(currentKey())
                _state.update { it.copy(error = null) }
            } catch (e: IOException) {
                _state.update {
                    it.updateDevice(device.mac) { d -> d.copy(pilot = previous) }
                        .copy(error = controlErrorMessage(e, it.signingKey != null))
                }
            }
        }
    }

    /**
     * Fills in model and firmware details, then detects capabilities; the device stays listed
     * without whatever couldn't be fetched.
     */
    private suspend fun fetchSystemConfig(device: WizDevice) {
        try {
            val config = WizClient.request(device.ip, "getSystemConfig", signingKey = currentKey())
            _state.update {
                it.updateDevice(device.mac) { d ->
                    d.copy(
                        moduleName = config.optString("moduleName").ifEmpty { null },
                        fwVersion = config.optString("fwVersion").ifEmpty { null },
                        systemConfig = config.keys().asSequence()
                            .associateWith { key -> config.get(key).toString() },
                    )
                }
            }
            val capabilities = WizClient.getCapabilities(device.ip, config, currentKey())
            _state.update { it.updateDevice(device.mac) { d -> d.copy(capabilities = capabilities) } }
        } catch (_: IOException) {
            // Device answered discovery but not a config query; keep what was fetched.
        }
    }

    /** Fetches on/off and brightness; without it the device is shown but not controllable. */
    private suspend fun fetchPilot(device: WizDevice) {
        try {
            val pilot = WizClient.getPilot(device.ip, currentKey())
            _state.update { it.updateDevice(device.mac) { d -> d.copy(pilot = pilot) } }
        } catch (_: IOException) {
            // Leave pilot null; controls stay hidden.
        }
    }
}

/** Turns signing-related device errors into actionable messages. */
private fun controlErrorMessage(e: IOException, hasKey: Boolean): String? =
    when {
        e is WizErrorException && e.code == WizErrorException.INVALID_PARAMS && !hasKey ->
            "This device requires a signing key"
        e is WizErrorException && e.code == WizErrorException.SECURITY_ERROR ->
            "Signing key rejected: wrong key, or the phone's clock is off"
        else -> e.message
    }

/** Adds or replaces (by MAC) a device, keeping the list ordered by IP. */
private fun ScanState.withDevice(device: WizDevice): ScanState =
    copy(devices = (devices.filterNot { it.mac == device.mac } + device).sortedBy { ipKey(it.ip) })

/** Applies [transform] to the device with [mac], leaving the list unchanged if it's absent. */
private fun ScanState.updateDevice(mac: String, transform: (WizDevice) -> WizDevice): ScanState =
    copy(devices = devices.map { if (it.mac == mac) transform(it) else it })

/** Sort key that orders dotted IPv4 addresses numerically. */
private fun ipKey(ip: String): Long =
    ip.split('.').fold(0L) { acc, part -> acc * 256 + (part.toLongOrNull() ?: 0) }
