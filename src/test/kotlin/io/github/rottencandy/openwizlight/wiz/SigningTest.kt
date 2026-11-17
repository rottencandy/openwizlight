package io.github.rottencandy.openwizlight.wiz

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SigningTest {

    private val keyHex = "00112233445566778899aabbccddeeff"

    private val key = decodeSigningKey(keyHex)!!

    /**
     * Expected HMAC computed with Python's hmac module, using pywizlight's test key and
     * timestamp. Empty params keep the signed JSON independent of key order, which org.json
     * implementations don't agree on.
     */
    @Test
    fun `signature matches a reference HMAC`() {
        val message = signedMessage("getPilot", JSONObject(), key, timestampSec = 1790441723)
        assertEquals(
            """{"method":"getPilot","env":"pro","params":{"sigTs":1790441723},""" +
                """"hmac":"3ozvewuakaU3A3nJHhEaiMLpzgXq2fMoQqscMPuu1bI="}""",
            message,
        )
    }

    /** Whatever order the params serialize in, the HMAC must cover exactly the bytes sent. */
    @Test
    fun `signature covers the params as sent`() {
        val message = signedMessage("setPilot", JSONObject().put("state", true).put("dimming", 50), key, 1790441723)
        val sentParams = Regex(""""params":(\{.*?\}),"hmac"""").find(message)!!.groupValues[1]
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val expected = Base64.getEncoder().encodeToString(mac.doFinal(sentParams.toByteArray()))
        assertEquals(expected, JSONObject(message).getString("hmac"))
        val params = JSONObject(sentParams)
        assertEquals(true, params.getBoolean("state"))
        assertEquals(50, params.getInt("dimming"))
        assertEquals(1790441723L, params.getLong("sigTs"))
    }

    @Test
    fun `signing leaves the caller's params alone`() {
        val params = JSONObject().put("dimming", 50)
        signedMessage("setPilot", params, key, timestampSec = 0)
        assertEquals("""{"dimming":50}""", params.toString())
    }

    @Test
    fun `plain message`() {
        assertEquals("""{"method":"getPilot","params":{}}""", plainMessage("getPilot", JSONObject()))
    }

    @Test
    fun `decodeSigningKey accepts 32 hex characters in either case`() {
        assertEquals(16, decodeSigningKey(keyHex.uppercase())?.size)
        assertEquals(0x11.toByte(), decodeSigningKey(" $keyHex ")?.get(1))
    }

    @Test
    fun `decodeSigningKey rejects other input`() {
        assertNull(decodeSigningKey(keyHex.dropLast(2)))
        assertNull(decodeSigningKey(keyHex + "00"))
        assertNull(decodeSigningKey(keyHex.replaceFirst('0', 'g')))
    }

    @Test
    fun `findSigningKeys searches nested objects and arrays, normalising and deduplicating`() {
        val export = """
            {"homes":[{"name":"Home","udp_signing_key":"${keyHex.uppercase()}"},
                      {"name":"Copy","config":{"udp_signing_key":"$keyHex"}}],
             "other":{"udp_signing_key":"not-a-key"}}
        """
        assertEquals(listOf(keyHex), findSigningKeys(export))
    }

    @Test
    fun `findSigningKeys returns every distinct key`() {
        val other = "ffeeddccbbaa99887766554433221100"
        val export = """[{"udp_signing_key":"$keyHex"},{"udp_signing_key":"$other"}]"""
        assertEquals(setOf(keyHex, other), findSigningKeys(export).toSet())
    }

    @Test(expected = JSONException::class)
    fun `findSigningKeys rejects malformed JSON`() {
        findSigningKeys("""{"udp_signing_key":""")
    }

    /** Android's JSON parser is lenient: bare words parse as a string, which holds no keys. */
    @Test
    fun `findSigningKeys finds nothing in plain text`() {
        assertEquals(emptyList<String>(), findSigningKeys("not json"))
    }
}
