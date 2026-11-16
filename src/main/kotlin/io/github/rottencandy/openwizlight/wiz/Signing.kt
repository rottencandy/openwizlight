package io.github.rottencandy.openwizlight.wiz

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

private const val SIGNING_KEY_BYTES = 16
private const val HMAC_ALGORITHM = "HmacSHA256"

/**
 * Decodes a `udp_signing_key` (32 hex characters, from the WiZ app's local integration export)
 * into raw key bytes, or returns null if it isn't valid hex of the right length.
 */
fun decodeSigningKey(hex: String): ByteArray? =
    hex.trim()
        .takeIf { it.length == SIGNING_KEY_BYTES * 2 && it.all { c -> c.isHexDigit() } }
        ?.chunked(2)
        ?.map { it.toInt(16).toByte() }
        ?.toByteArray()

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/** Serializes an unsigned request. */
internal fun plainMessage(method: String, params: JSONObject): String =
    JSONObject().put("method", method).put("params", params).toString()

/**
 * Serializes a request signed for `"env":"pro"` firmware, as pywizlight's `sign_request`:
 * `sigTs` (unix seconds) is added to params, and `hmac` is the base64 HMAC-SHA256 of the
 * compact params JSON. The message is assembled around the exact params string that was
 * signed so the bytes the device sees match the signature.
 */
internal fun signedMessage(
    method: String,
    params: JSONObject,
    key: ByteArray,
    timestampSec: Long,
): String {
    val signedParams = JSONObject(params.toString()).put("sigTs", timestampSec).toString()
    val hmac = Base64.encode(hmacSha256(key, signedParams.toByteArray(Charsets.UTF_8)))
    return """{"method":${JSONObject.quote(method)},"env":"pro","params":$signedParams,"hmac":"$hmac"}"""
}

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
    Mac.getInstance(HMAC_ALGORITHM)
        .apply { init(SecretKeySpec(key, HMAC_ALGORITHM)) }
        .doFinal(data)

private const val SIGNING_KEY_FIELD = "udp_signing_key"

/**
 * Finds the distinct, valid `udp_signing_key` values anywhere in a WiZ app export. The
 * export's layout isn't documented, so this searches every nested object and array.
 *
 * @throws org.json.JSONException if [json] isn't valid JSON
 */
fun findSigningKeys(json: String): List<String> =
    signingKeyCandidates(JSONTokener(json).nextValue())
        .map { it.trim().lowercase() }
        .filter { decodeSigningKey(it) != null }
        .distinct()
        .toList()

private fun signingKeyCandidates(value: Any?): Sequence<String> =
    when (value) {
        is JSONObject -> value.keys().asSequence().flatMap { name ->
            val child = value.opt(name)
            if (name == SIGNING_KEY_FIELD && child is String) sequenceOf(child)
            else signingKeyCandidates(child)
        }
        is JSONArray -> (0 until value.length()).asSequence().flatMap { signingKeyCandidates(value.opt(it)) }
        else -> emptySequence()
    }
