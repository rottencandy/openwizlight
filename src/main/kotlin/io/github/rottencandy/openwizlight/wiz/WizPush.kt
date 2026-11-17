package io.github.rottencandy.openwizlight.wiz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom

/** A device's new state, pushed by it in a `syncPilot` message. */
data class PushUpdate(val mac: String, val pilot: WizPilot)

/**
 * Push updates, as in pywizlight's `PushManager`: a device that receives `registration` with
 * `register:true` sends `syncPilot` to the registered phone's [PUSH_PORT] whenever its state
 * changes, for as long as registration is renewed every [KEEP_ALIVE_MS].
 */
object WizPush {

    const val PUSH_PORT = 38900
    const val KEEP_ALIVE_MS = 20_000L

    /** How often the receive loop checks for cancellation. */
    private const val RECEIVE_POLL_MS = 1000L

    /** Devices only need some MAC to address; pywizlight also makes up a random one. */
    private val phoneMac: String = SecureRandom().let { rng -> (1..12).map { "0123456789abcdef"[rng.nextInt(16)] } }
        .joinToString("")

    /**
     * Listens on [PUSH_PORT] and emits every `syncPilot` received, while collected.
     *
     * @throws java.net.BindException if the port is already in use
     */
    fun updates(): Flow<PushUpdate> = flow {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(PUSH_PORT))
            while (true) {
                currentCoroutineContext().ensureActive()
                socket.receiveUntil(System.currentTimeMillis() + RECEIVE_POLL_MS) { json, _ -> toUpdate(json) }
                    ?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Asks the device at [ip] to push its state changes to this phone for the next
     * [KEEP_ALIVE_MS] or so; call again before then to keep receiving them.
     */
    suspend fun register(ip: String, signingKey: ByteArray? = null) {
        val params = JSONObject()
            .put("phoneIp", sourceIpFor(ip))
            .put("register", true)
            .put("phoneMac", phoneMac)
        WizClient.request(ip, "registration", params, signingKey)
    }

    /**
     * The local address that routes to [ip], i.e. where the device should send pushes.
     * Connecting a UDP socket sends nothing; it only selects the route.
     */
    private suspend fun sourceIpFor(ip: String): String = withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName(ip), WIZ_PORT)
            socket.localAddress.hostAddress.orEmpty()
        }
    }

    /** Parses a `syncPilot` message; anything else (e.g. the WiZ app's probes) yields null. */
    internal fun toUpdate(json: JSONObject): PushUpdate? {
        if (json.optString("method") != "syncPilot") return null
        val params = json.optJSONObject("params") ?: return null
        val mac = params.optString("mac").ifEmpty { return null }
        return PushUpdate(mac, parsePilot(params))
    }
}
