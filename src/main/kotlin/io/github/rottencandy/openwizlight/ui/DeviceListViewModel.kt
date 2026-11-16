package io.github.rottencandy.openwizlight.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rottencandy.openwizlight.wiz.WizClient
import io.github.rottencandy.openwizlight.wiz.WizDevice
import io.github.rottencandy.openwizlight.wiz.WizDiscovery
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class ScanState(
    val devices: List<WizDevice> = emptyList(),
    val scanning: Boolean = false,
    val error: String? = null,
)

class DeviceListViewModel : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        scan()
    }

    /** Starts a fresh scan unless one (including its pending config fetches) is still running. */
    fun scan() {
        if (scanJob?.isActive == true) return
        _state.value = ScanState(scanning = true)
        scanJob = viewModelScope.launch {
            val error = try {
                WizDiscovery.discover().collect { device ->
                    _state.update { it.withDevice(device) }
                    launch { fetchSystemConfig(device) }
                }
                null
            } catch (e: IOException) {
                "Scan failed: ${e.message}"
            }
            _state.update { it.copy(scanning = false, error = error) }
        }
    }

    /** Fills in model and firmware details; the device stays listed without them on failure. */
    private suspend fun fetchSystemConfig(device: WizDevice) {
        try {
            val config = WizClient.request(device.ip, "getSystemConfig")
            val detailed = device.copy(
                moduleName = config.optString("moduleName").ifEmpty { null },
                fwVersion = config.optString("fwVersion").ifEmpty { null },
            )
            _state.update { it.withDevice(detailed) }
        } catch (_: IOException) {
            // Device answered discovery but not the config query; keep the basic entry.
        }
    }
}

/** Adds or replaces (by MAC) a device, keeping the list ordered by IP. */
private fun ScanState.withDevice(device: WizDevice): ScanState =
    copy(devices = (devices.filterNot { it.mac == device.mac } + device).sortedBy { ipKey(it.ip) })

/** Sort key that orders dotted IPv4 addresses numerically. */
private fun ipKey(ip: String): Long =
    ip.split('.').fold(0L) { acc, part -> acc * 256 + (part.toLongOrNull() ?: 0) }
