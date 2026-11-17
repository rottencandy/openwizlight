package io.github.rottencandy.openwizlight.wiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenesTest {

    @Test
    fun `scene counts per class`() {
        assertEquals(37, scenesFor(DeviceClass.RGB).size)
        assertEquals(17, scenesFor(DeviceClass.TUNABLE_WHITE).size)
        assertEquals(8, scenesFor(DeviceClass.DIMMABLE_WHITE).size)
        assertEquals(emptyList<Int>(), scenesFor(DeviceClass.SOCKET))
        assertEquals(emptyList<Int>(), scenesFor(DeviceClass.FAN_DIMMABLE))
    }

    @Test
    fun `scenes are sorted by name`() {
        DeviceClass.entries.forEach { deviceClass ->
            val names = scenesFor(deviceClass).map(SCENE_NAMES::getValue)
            assertEquals(names.sorted(), names)
        }
    }

    @Test
    fun `Rhythm, custom and sync-box scenes are named but not offered`() {
        val unlisted = listOf(RHYTHM_SCENE_ID, 256, 265, 201, 204)
        unlisted.forEach { id ->
            assertTrue("$id named", id in SCENE_NAMES)
            assertFalse("$id offered", id in scenesFor(DeviceClass.RGB))
        }
        assertEquals("Custom Mode 10", SCENE_NAMES[265])
    }
}
