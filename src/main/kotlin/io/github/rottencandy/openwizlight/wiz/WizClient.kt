package io.github.rottencandy.openwizlight.wiz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** The device answered a request with an `error` object instead of a `result`. */
class WizErrorException(ip: String, method: String, error: JSONObject) :
    IOException("$ip: $method failed: $error") {
    val code: Int = error.optInt("code")

    companion object {
        /** Unsigned write to firmware that requires signing. */
        const val INVALID_PARAMS = -32602

        /** Bad signature, or a `sigTs` too far from the device's clock. */
        const val SECURITY_ERROR = -32605
    }
}

/** Request/response calls to a single WiZ device. */
object WizClient {

    private const val FIRST_SEND_INTERVAL_MS = 500L
    private const val MAX_BACKOFF_MS = 2000L
    private const val MAX_SENDS = 4

    /**
     * Sends `{"method": method, "params": params}` to the device and returns the reply's "result".
     * Resends with exponential backoff until a reply with the same method arrives, mirroring
     * pywizlight's `_send_udp_message_with_retry`. With a [signingKey] the request is signed
     * (see [signedMessage]); newer firmware rejects unsigned writes.
     *
     * @throws SocketTimeoutException if the device never answers
     * @throws WizErrorException if the device replies with an error
     */
    suspend fun request(
        ip: String,
        method: String,
        params: JSONObject = JSONObject(),
        signingKey: ByteArray? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val message = signingKey
            ?.let { signedMessage(method, params, it, System.currentTimeMillis() / 1000) }
            ?: plainMessage(method, params)
        val address = InetAddress.getByName(ip)
        val reply = DatagramSocket().use { socket ->
            backoffIntervals().firstNotNullOfOrNull { wait ->
                ensureActive()
                socket.sendJson(message, address)
                socket.receiveUntil(System.currentTimeMillis() + wait) { json, _ ->
                    json.takeIf { it.optString("method") == method }
                }
            } ?: throw SocketTimeoutException("$ip: no response to $method")
        }
        reply.optJSONObject("error")?.let { throw WizErrorException(ip, method, it) }
        reply.optJSONObject("result") ?: throw IOException("$ip: $method reply has no result")
    }

    suspend fun getPilot(ip: String, signingKey: ByteArray? = null): WizPilot =
        parsePilot(request(ip, "getPilot", signingKey = signingKey))

    suspend fun setPower(ip: String, on: Boolean, signingKey: ByteArray? = null) {
        request(ip, "setPilot", JSONObject().put("state", on), signingKey)
    }

    /** Sets brightness in percent; like pywizlight's `turn_on`, this also switches the light on. */
    suspend fun setBrightness(ip: String, percent: Int, signingKey: ByteArray? = null) {
        val dimming = percent.coerceIn(MIN_DIMMING, MAX_DIMMING)
        request(ip, "setPilot", JSONObject().put("state", true).put("dimming", dimming), signingKey)
    }

    private fun parsePilot(result: JSONObject): WizPilot =
        WizPilot(
            on = result.optBoolean("state"),
            dimming = result.takeIf { it.has("dimming") }?.optInt("dimming"),
        )

    private fun backoffIntervals(): Sequence<Long> =
        generateSequence(FIRST_SEND_INTERVAL_MS) { minOf(it * 2, MAX_BACKOFF_MS) }.take(MAX_SENDS)
}
