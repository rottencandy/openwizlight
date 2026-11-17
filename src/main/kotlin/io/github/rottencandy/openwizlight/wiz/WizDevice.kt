package io.github.rottencandy.openwizlight.wiz

/**
 * A WiZ device found on the local network.
 *
 * @property moduleName hardware module identifier (e.g. "ESP01_SHRGB1C_31"), null until fetched
 * @property fwVersion firmware version, null until fetched
 * @property pilot current light state, null until fetched
 * @property systemConfig every field of the `getSystemConfig` reply, as display strings
 * @property capabilities supported controls, null until detected or if detection failed
 */
data class WizDevice(
    val ip: String,
    val mac: String,
    val moduleName: String? = null,
    val fwVersion: String? = null,
    val pilot: WizPilot? = null,
    val systemConfig: Map<String, String> = emptyMap(),
    val capabilities: WizCapabilities? = null,
)

/**
 * Light state as reported by `getPilot`.
 *
 * @property dimming brightness in percent, null for devices that don't dim (e.g. sockets)
 * @property temp white colour temperature in Kelvin, null when the light isn't in white
 *   mode (e.g. showing an RGB colour or a scene) or doesn't support it
 * @property color current colour, null when the light isn't showing one
 * @property scene running scene's `sceneId` (see [SCENE_NAMES]), null when none is
 * @property speed effect speed, [MIN_SPEED]–[MAX_SPEED], null if not reported
 */
data class WizPilot(
    val on: Boolean,
    val dimming: Int? = null,
    val temp: Int? = null,
    val color: HueSat? = null,
    val scene: Int? = null,
    val speed: Int? = null,
)

/** Lowest brightness most lights accept; pywizlight uses the same fallback when the model doesn't report `minDimLevel`. */
const val MIN_DIMMING = 10
const val MAX_DIMMING = 100

/** Colour temperature limits pywizlight clamps to when the device's own range is unknown. */
const val MIN_KELVIN = 1000
const val MAX_KELVIN = 10000
