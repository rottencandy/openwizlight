package io.github.rottencandy.openwizlight.wiz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/** Finds WiZ devices by UDP broadcast, as in pywizlight's `discovery.find_wizlights`. */
object WizDiscovery {

    private const val BROADCAST_ADDRESS = "255.255.255.255"
    private const val PROBE_INTERVAL_MS = 1000L

    /**
     * Unregistering a phone is harmless; devices answer it with their MAC, which is all we need.
     * The phoneMac / phoneIp values are irrelevant because register is false.
     */
    private const val REGISTER_MESSAGE =
        """{"method":"registration","params":{"phoneMac":"AAAAAAAAAAAA","register":false,"phoneIp":"1.2.3.4","id":"1"}}"""

    /**
     * Broadcasts a registration probe every second for [durationMs] and emits each device
     * the first time its MAC is seen.
     */
    fun discover(durationMs: Long = 5000): Flow<WizDevice> = flow {
        val broadcast = InetAddress.getByName(BROADCAST_ADDRESS)
        val seen = mutableSetOf<String>()
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(WIZ_PORT))

            val end = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < end) {
                currentCoroutineContext().ensureActive()
                socket.sendJson(REGISTER_MESSAGE, broadcast)
                val windowEnd = minOf(end, System.currentTimeMillis() + PROBE_INTERVAL_MS)
                while (true) {
                    val device = socket.receiveUntil(windowEnd, ::toDevice) ?: break
                    if (seen.add(device.mac)) emit(device)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Our own broadcast also loops back to the socket; it has no "result" and yields null. */
    private fun toDevice(json: JSONObject, sender: InetAddress): WizDevice? =
        json.optJSONObject("result")
            ?.optString("mac")
            ?.takeIf { it.isNotEmpty() }
            ?.let { WizDevice(ip = sender.hostAddress.orEmpty(), mac = it) }
}
