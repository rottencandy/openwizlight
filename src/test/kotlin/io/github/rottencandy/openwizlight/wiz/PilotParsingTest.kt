package io.github.rottencandy.openwizlight.wiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers `getPilot` results and `syncPilot` pushes, which share a format. */
class PilotParsingTest {

    @Test
    fun `colour mode, from pywizlight's fake bulb`() {
        val pilot = parsePilot(
            JSONObject(
                """{"mac":"ABCABCABCABC","rssi":-62,"src":"udp","state":false,"sceneId":0,
                   "r":255,"g":127,"b":0,"c":0,"w":0,"temp":0,"dimming":13}""",
            ),
        )
        assertEquals(WizPilot(on = false, dimming = 13, color = rgbCwToHs(255, 127, 0, 0)), pilot)
    }

    @Test
    fun `white mode, from pywizlight's dimmable white capture`() {
        val pilot = parsePilot(
            JSONObject("""{"mac":"a8bb509f71d1","rssi":-54,"cnx":"0000","src":"","state":true,"sceneId":0,"temp":2700,"dimming":100}"""),
        )
        assertEquals(WizPilot(on = true, dimming = 100, temp = 2700), pilot)
    }

    @Test
    fun `scene with speed`() {
        val pilot = parsePilot(JSONObject("""{"state":true,"sceneId":5,"speed":120,"dimming":50}"""))
        assertEquals(5, pilot.scene)
        assertEquals(120, pilot.speed)
    }

    @Test
    fun `a Rhythm schedule overrides sceneId`() {
        assertEquals(RHYTHM_SCENE_ID, parsePilot(JSONObject("""{"state":true,"sceneId":0,"schdPsetId":3}""")).scene)
    }

    @Test
    fun `socket reports only on and off`() {
        assertEquals(WizPilot(on = true), parsePilot(JSONObject("""{"mac":"a8bb50000000","state":true}""")))
    }

    @Test
    fun `syncPilot push, from pywizlight's dimmable white capture`() {
        val update = WizPush.toUpdate(
            JSONObject(
                """{"method":"syncPilot","id":25,"env":"pro","params":{"mac":"a8bb509f71d1","rssi":-55,
                   "cnx":"0000","src":"udp","state":true,"sceneId":0,"temp":2700,"dimming":100}}""",
            ),
        )
        assertEquals(PushUpdate("a8bb509f71d1", WizPilot(on = true, dimming = 100, temp = 2700)), update)
    }

    @Test
    fun `other messages on the push port are ignored`() {
        assertNull(WizPush.toUpdate(JSONObject("""{"method":"firstBeat","params":{"mac":"a8bb509f71d1"}}""")))
        assertNull(WizPush.toUpdate(JSONObject("""{"method":"syncPilot","params":{"state":true}}""")))
    }
}
