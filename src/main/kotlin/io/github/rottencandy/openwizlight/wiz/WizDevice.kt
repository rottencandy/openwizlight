package io.github.rottencandy.openwizlight.wiz

/**
 * A WiZ device found on the local network.
 *
 * @property moduleName hardware module identifier (e.g. "ESP01_SHRGB1C_31"), null until fetched
 * @property fwVersion firmware version, null until fetched
 */
data class WizDevice(
    val ip: String,
    val mac: String,
    val moduleName: String? = null,
    val fwVersion: String? = null,
)
