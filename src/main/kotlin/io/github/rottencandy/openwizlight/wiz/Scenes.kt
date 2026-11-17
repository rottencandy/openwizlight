package io.github.rottencandy.openwizlight.wiz

/** `sceneId` reported while a Rhythm schedule (`schdPsetId`) drives the light. */
const val RHYTHM_SCENE_ID = 1000

/** Effect speed limits accepted by `setPilot`; pywizlight validates the same range. */
const val MIN_SPEED = 10
const val MAX_SPEED = 200

/** Built-in scenes by `sceneId`, from pywizlight's `scenes.py`. */
private val BUILT_IN_SCENES = mapOf(
    1 to "Ocean", 2 to "Romance", 3 to "Sunset", 4 to "Party", 5 to "Fireplace",
    6 to "Cozy", 7 to "Forest", 8 to "Pastel colors", 9 to "Wake-up", 10 to "Bedtime",
    11 to "Warm white", 12 to "Daylight", 13 to "Cool white", 14 to "Night light", 15 to "Focus",
    16 to "Relax", 17 to "True colors", 18 to "TV time", 19 to "Plantgrowth", 20 to "Spring",
    21 to "Summer", 22 to "Fall", 23 to "Deep dive", 24 to "Jungle", 25 to "Mojito",
    26 to "Club", 27 to "Christmas", 28 to "Halloween", 29 to "Candlelight", 30 to "Golden white",
    31 to "Pulse", 32 to "Steampunk", 33 to "Diwali", 34 to "White", 35 to "Alarm",
    36 to "Snowy sky", 40 to "Dim-to-warm",
)

/**
 * Names for every `sceneId` a device may report. Rhythm, custom modes (set up in the WiZ app)
 * and sync-box scenes are only named here, not offered by [scenesFor].
 */
val SCENE_NAMES: Map<Int, String> =
    BUILT_IN_SCENES +
        (RHYTHM_SCENE_ID to "Rhythm") +
        (1..10).associate { 255 + it to "Custom Mode $it" } +
        mapOf(201 to "Cinematic", 202 to "Vibrant", 203 to "Rhythmic", 204 to "Relaxation")

private val TW_SCENES = listOf(6, 9, 10, 11, 12, 13, 14, 15, 16, 18, 29, 30, 31, 32, 33, 35, 40)
private val DW_SCENES = listOf(9, 10, 14, 29, 31, 32, 34, 35)

/** Scene IDs a device of [deviceClass] can be switched to, ordered by name. */
fun scenesFor(deviceClass: DeviceClass): List<Int> =
    when (deviceClass) {
        DeviceClass.RGB -> BUILT_IN_SCENES.keys.toList()
        DeviceClass.TUNABLE_WHITE -> TW_SCENES
        DeviceClass.DIMMABLE_WHITE -> DW_SCENES
        else -> emptyList()
    }.sortedBy { BUILT_IN_SCENES.getValue(it) }
