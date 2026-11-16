package io.github.rottencandy.openwizlight.wiz

import org.json.JSONException
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** UDP port WiZ devices listen on for their local JSON protocol. */
const val WIZ_PORT = 38899

internal const val MAX_DATAGRAM_SIZE = 4096

/**
 * Blocks receiving datagrams until [deadline] (epoch millis) and returns the first one that
 * [select] maps to a non-null value, or null on timeout. Non-JSON datagrams are skipped.
 */
internal inline fun <T : Any> DatagramSocket.receiveUntil(
    deadline: Long,
    select: (json: JSONObject, sender: InetAddress) -> T?,
): T? {
    val buf = ByteArray(MAX_DATAGRAM_SIZE)
    while (true) {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) return null
        soTimeout = remaining.toInt()
        val packet = DatagramPacket(buf, buf.size)
        try {
            receive(packet)
        } catch (_: SocketTimeoutException) {
            return null
        }
        packet.json()?.let { select(it, packet.address) }?.let { return it }
    }
}

internal fun DatagramSocket.sendJson(json: String, address: InetAddress) {
    val data = json.toByteArray()
    send(DatagramPacket(data, data.size, address, WIZ_PORT))
}

internal fun DatagramPacket.json(): JSONObject? =
    try {
        JSONObject(String(data, offset, length, Charsets.UTF_8))
    } catch (_: JSONException) {
        null
    }
