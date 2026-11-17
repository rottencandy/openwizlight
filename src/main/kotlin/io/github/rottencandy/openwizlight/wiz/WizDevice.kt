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
 */
data class WizPilot(
    val on: Boolean,
    val dimming: Int? = null,
)

/** Lowest brightness most lights accept; pywizlight uses the same fallback when the model doesn't report `minDimLevel`. */
const val MIN_DIMMING = 10
const val MAX_DIMMING = 100
