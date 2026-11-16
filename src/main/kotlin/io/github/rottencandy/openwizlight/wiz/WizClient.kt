package io.github.rottencandy.openwizlight.wiz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Request/response calls to a single WiZ device. */
object WizClient {

    private const val FIRST_SEND_INTERVAL_MS = 500L
    private const val MAX_BACKOFF_MS = 2000L
    private const val MAX_SENDS = 4

    /**
     * Sends `{"method": method, "params": {}}` to the device and returns the reply's "result".
     * Resends with exponential backoff until a reply with the same method arrives, mirroring
     * pywizlight's `_send_udp_message_with_retry`.
     *
     * @throws SocketTimeoutException if the device never answers
     */
    suspend fun request(ip: String, method: String): JSONObject = withContext(Dispatchers.IO) {
        val message = JSONObject().put("method", method).put("params", JSONObject()).toString()
        val address = InetAddress.getByName(ip)
        DatagramSocket().use { socket ->
            backoffIntervals().firstNotNullOfOrNull { wait ->
                ensureActive()
                socket.sendJson(message, address)
                socket.receiveUntil(System.currentTimeMillis() + wait) { json, _ ->
                    json.takeIf { it.optString("method") == method }?.optJSONObject("result")
                }
            } ?: throw SocketTimeoutException("$ip: no response to $method")
        }
    }

    private fun backoffIntervals(): Sequence<Long> =
        generateSequence(FIRST_SEND_INTERVAL_MS) { minOf(it * 2, MAX_BACKOFF_MS) }.take(MAX_SENDS)
}
