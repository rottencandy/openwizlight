package io.github.rottencandy.openwizlight.wiz

import org.json.JSONObject

/**
 * Kind of WiZ device, with the controls every device of that kind supports.
 * Mirrors pywizlight's `BulbClass` and its base feature map.
 */
enum class DeviceClass(
    val label: String,
    val brightness: Boolean,
    val colorTemp: Boolean,
    val color: Boolean,
    val fan: Boolean = false,
) {
    RGB("RGB tunable", brightness = true, colorTemp = true, color = true),
    TUNABLE_WHITE("Tunable white", brightness = true, colorTemp = true, color = false),
    DIMMABLE_WHITE("Dimmable white", brightness = true, colorTemp = false, color = false),
    SOCKET("Socket", brightness = false, colorTemp = false, color = false),
    FAN_DIMMABLE("Fan, dimmable", brightness = true, colorTemp = false, color = false, fan = true),
    FAN_TUNABLE_WHITE("Fan, tunable white", brightness = true, colorTemp = true, color = false, fan = true),
}

data class KelvinRange(val min: Int, val max: Int)

/**
 * What a device can do, derived from its config replies.
 *
 * @property effects whether the device runs scenes (`sceneId`)
 * @property dualHead whether it has separately controllable up/down lights
 * @property kelvinRange supported colour temperatures, null if the device didn't report them
 * @property minDimming lowest brightness percentage the device accepts
 */
data class WizCapabilities(
    val deviceClass: DeviceClass,
    val effects: Boolean,
    val dualHead: Boolean,
    val kelvinRange: KelvinRange?,
    val minDimming: Int,
)

/**
 * Derives capabilities like pywizlight's `BulbType.from_data`. The class comes from the
 * `moduleName` in [systemConfig] (e.g. "ESP01_SHRGB1C_31"), or its `typeId` on firmware
 * that doesn't report one. Ranges come from [modelConfig] (firmware 1.22+), else [userConfig].
 * Returns null if neither field identifies the device.
 */
fun detectCapabilities(
    systemConfig: JSONObject,
    modelConfig: JSONObject?,
    userConfig: JSONObject?,
): WizCapabilities? {
    val moduleName = systemConfig.optString("moduleName").ifEmpty { null }
    val (deviceClass, effects, dualHead) = when {
        moduleName != null -> classFromModuleName(moduleName) ?: return null
        systemConfig.has("typeId") -> Triple(DeviceClass.DIMMABLE_WHITE, true, false)
        else -> return null
    }
    return WizCapabilities(
        deviceClass = deviceClass,
        effects = effects,
        dualHead = dualHead,
        kelvinRange = (modelConfig ?: userConfig)?.let(::parseKelvinRange),
        minDimming = modelConfig?.optInt("minDimLevel", MIN_DIMMING) ?: MIN_DIMMING,
    )
}

/**
 * Parses the second `_`-separated part of a module name into (class, effects, dual head).
 * The checks run in pywizlight's order, since e.g. "DDTW" also contains "TW".
 */
private fun classFromModuleName(moduleName: String): Triple<DeviceClass, Boolean, Boolean>? {
    val id = moduleName.split('_').getOrNull(1) ?: return null
    val dualHead = "DH" in id
    return when {
        "RGB" in id -> Triple(DeviceClass.RGB, true, dualHead)
        "DDTW" in id -> Triple(DeviceClass.FAN_TUNABLE_WHITE, true, dualHead)
        "TW" in id -> Triple(DeviceClass.TUNABLE_WHITE, true, dualHead)
        "SOCKET" in id -> Triple(DeviceClass.SOCKET, false, dualHead)
        "FANDIM" in id -> Triple(DeviceClass.FAN_DIMMABLE, false, dualHead)
        else -> Triple(DeviceClass.DIMMABLE_WHITE, dualHead || "SH" in id, dualHead)
    }
}

/** Reads `extRange`, or `cctRange` on firmware 1.22+ (e.g. `[2200, 2700, 6500, 6500]`). */
private fun parseKelvinRange(config: JSONObject): KelvinRange? {
    val array = config.optJSONArray("extRange") ?: config.optJSONArray("cctRange") ?: return null
    val kelvins = (0 until array.length()).mapNotNull { array.optDouble(it).takeUnless(Double::isNaN) }
    return kelvins.takeIf { it.isNotEmpty() }?.let { KelvinRange(it.min().toInt(), it.max().toInt()) }
}
