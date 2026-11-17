package io.github.rottencandy.openwizlight.wiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Config replies and expected types come from pywizlight's fake_bulb.py and test_bulb_*.py. */
class WizCapabilitiesTest {

    private fun detect(systemConfig: String, modelConfig: String? = null, userConfig: String? = null) =
        detectCapabilities(JSONObject(systemConfig), modelConfig?.let(::JSONObject), userConfig?.let(::JSONObject))

    private fun system(moduleName: String) = """{"mac":"a8bb50000000","moduleName":"$moduleName"}"""

    @Test
    fun `RGB light strip with model config`() {
        val caps = detect(
            system("ESP20_SHRGB_01ABI"),
            modelConfig = """{"wcr":80,"nowc":2,"cctRange":[2700,2700,6500,6500]}""",
        )
        assertEquals(
            WizCapabilities(DeviceClass.RGB, effects = true, dualHead = false, KelvinRange(2700, 6500), MIN_DIMMING),
            caps,
        )
    }

    @Test
    fun `dual head RGB on old firmware reads range from user config`() {
        val caps = detect(
            system("ESP20_DHRGB_01B"),
            userConfig = """{"whiteRange":[2700,6500],"extRange":[2200,6500]}""",
        )
        assertEquals(
            WizCapabilities(DeviceClass.RGB, effects = true, dualHead = true, KelvinRange(2200, 6500), MIN_DIMMING),
            caps,
        )
    }

    @Test
    fun `tunable white`() {
        val caps = detect(system("ESP05_SHTW_21"), userConfig = """{"whiteRange":[2700,6500],"extRange":[2700,6500]}""")
        assertEquals(DeviceClass.TUNABLE_WHITE, caps?.deviceClass)
        assertEquals(true, caps?.effects)
        assertEquals(KelvinRange(2700, 6500), caps?.kelvinRange)
    }

    @Test
    fun `dimmable white single head supports effects`() {
        val caps = detect(system("ESP06_SHDW9_01"))
        assertEquals(DeviceClass.DIMMABLE_WHITE, caps?.deviceClass)
        assertEquals(true, caps?.effects)
    }

    @Test
    fun `wall switch dims but has no effects`() {
        val caps = detect(system("ESP01_DIMTRIACS_01"))
        assertEquals(DeviceClass.DIMMABLE_WHITE, caps?.deviceClass)
        assertEquals(false, caps?.effects)
    }

    @Test
    fun `socket has no brightness or effects`() {
        val caps = detect(system("ESP10_SOCKET_06"), modelConfig = """{"cctRange":[2700,2700,2700,2700]}""")
        assertEquals(DeviceClass.SOCKET, caps?.deviceClass)
        assertEquals(false, caps?.deviceClass?.brightness)
        assertEquals(false, caps?.effects)
    }

    @Test
    fun `fan with dimmable light`() {
        val caps = detect(system("ESP03_FANDIMS_31"), modelConfig = """{"cctRange":[2700,2700,2700,2700],"fanSpeed":6}""")
        assertEquals(DeviceClass.FAN_DIMMABLE, caps?.deviceClass)
        assertEquals(false, caps?.effects)
        assertEquals(KelvinRange(2700, 2700), caps?.kelvinRange)
    }

    @Test
    fun `DDTW is a tunable white fan, not a plain tunable white`() {
        assertEquals(DeviceClass.FAN_TUNABLE_WHITE, detect(system("ESP03_DDTW_01"))?.deviceClass)
    }

    @Test
    fun `minDimLevel from model config`() {
        val caps = detect(system("ESP25_SHRGB_01"), modelConfig = """{"minDimLevel":1,"cctRange":[2200,2700,6500,6500]}""")
        assertEquals(1, caps?.minDimming)
    }

    @Test
    fun `missing minDimLevel falls back to the default`() {
        assertEquals(MIN_DIMMING, detect(system("ESP01_SHRGB_03"), modelConfig = "{}")?.minDimming)
    }

    @Test
    fun `typeId without module name is a dimmable white with effects`() {
        val caps = detect(
            """{"mac":"a8bb502054e3","typeId":0,"fwVersion":"1.8.0"}""",
            userConfig = """{"whiteRange":[2700,2700]}""",
        )
        // Like pywizlight, whiteRange alone doesn't give a Kelvin range.
        assertEquals(
            WizCapabilities(DeviceClass.DIMMABLE_WHITE, effects = true, dualHead = false, kelvinRange = null, MIN_DIMMING),
            caps,
        )
    }

    @Test
    fun `unidentifiable devices yield null`() {
        assertNull(detect(system("INVALID")))
        assertNull(detect("""{"mac":"a8bb50000000"}"""))
    }
}
