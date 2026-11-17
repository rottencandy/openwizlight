package io.github.rottencandy.openwizlight.wiz

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Port of pywizlight's rgbcw.py. WiZ bulbs mix r/g/b LEDs with white, so a hue/saturation
 * colour is sent as saturated RGB plus a warm white amount: above 50% saturation the white
 * fades out, below it the RGB fades out leaving white.
 */

/**
 * @property hue colour angle in degrees, 0–359
 * @property saturation 0 (white) to 100 (full colour)
 */
data class HueSat(val hue: Int, val saturation: Int)

/** Channel values for `setPilot`: r/g/b 0–255 and warm white [w] 0–[CW_MAX]. */
data class RgbCw(val r: Int, val g: Int, val b: Int, val w: Int)

/** Highest white value pywizlight sends alongside a colour. */
private const val CW_MAX = 128
private const val EPSILON = 1.0e-5

private data class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec(x * s, y * s)
    infix fun dot(o: Vec) = x * o.x + y * o.y

    /** Like pywizlight's `vecLen`, treats near-zero vectors as length 0. */
    fun length(): Double = (this dot this).let { if (it > EPSILON) sqrt(it) else 0.0 }
}

private fun vecFromAngle(radians: Double) = Vec(cos(radians), sin(radians))

/** Directions of red, green and blue on the hue plane, 120° apart. */
private val BASIS = List(3) { vecFromAngle(it * 2 * PI / 3) }

fun hsToRgbCw(color: HueSat): RgbCw =
    trapezoid(vecFromAngle((color.hue % 360) / 360.0 * 2 * PI), color.saturation / 100.0)

/** Inverse of [hsToRgbCw], for reading a bulb's colour back from its r/g/b and warm white. */
fun rgbCwToHs(r: Int, g: Int, b: Int, w: Int): HueSat {
    val hueVec = BASIS[0] * (r / 255.0) + BASIS[1] * (g / 255.0) + BASIS[2] * (b / 255.0)
    val cw = minOf(w, CW_MAX).toDouble() / CW_MAX
    // At full white the RGB part was scaled down with saturation; otherwise it's saturated
    // and the white amount encodes how far above 50% the saturation is.
    val saturation = if (cw == 1.0) hueVec.length() * 0.5 else 1 - cw / 2
    val hue = Math.floorMod((atan2(hueVec.y, hueVec.x) * 180 / PI).roundToInt(), 360)
    return HueSat(hue, (saturation * 100).roundToInt().coerceIn(0, 100))
}

/**
 * Expresses the unit [hueVec] as a mix of at most two basis colours, scaled so the larger
 * is 1, then splits [saturation] (0–1) between that colour and warm white.
 */
private fun trapezoid(hueVec: Vec, saturation: Double): RgbCw {
    val saturated = if (saturation <= EPSILON) List(3) { 0.0 } else saturatedRgb(hueVec)
    val (rgb, cw) =
        if (saturation >= 0.5) saturated to 1 - (saturation - 0.5) * 2
        else saturated.map { it * saturation * 2 } to 1.0
    val (r, g, b) = rgb.map { (it * 255).toInt() }
    return RgbCw(r, g, b, maxOf(0.0, cw * CW_MAX).toInt())
}

/** Fully saturated r/g/b (0–1 each) for a hue direction, using the two nearest basis colours. */
private fun saturatedRgb(hueVec: Vec): List<Double> {
    val maxAngle = cos(2 * PI / 3 - EPSILON)
    val mask = BASIS.map { (hueVec dot it) > maxAngle }
    if (mask.count { it } == 1) return mask.map { if (it) 1.0 else 0.0 }
    val (first, second) = BASIS.filterIndexed { i, _ -> mask[i] }
    // Intersect the ray from the hue point back along `first` with the line through `second`.
    val normal = Vec(second.y, -second.x)
    val coeff0 = (hueVec dot normal) / (first dot normal)
    val coeff1 = (hueVec - first * coeff0) dot second
    val scale = maxOf(coeff0, coeff1)
    val coeffs = listOf(coeff0 / scale, coeff1 / scale).iterator()
    return mask.map { if (it) minOf(coeffs.next(), 1.0) else 0.0 }
}
