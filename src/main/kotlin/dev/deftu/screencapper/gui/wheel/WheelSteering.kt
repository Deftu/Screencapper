package dev.deftu.screencapper.gui.wheel

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Turns mouse movement into a pointer on the wheel and the wedge it aims at. Units are whatever [configure]'s radius is
 * in (dp for the wheel).
 *
 * The pointer eases into the rim instead of stopping dead, the raw position is capped a little past the rim so pulling
 * back takes effect at once, the aim holds across a border for a few degrees so it doesn't flicker, and a square wheel
 * measures reach along the square. Wedge 0 points straight up; the rest follow clockwise.
 *
 * Adapted from PolyPlus's radial wheel (Polyfrost, GPL-3.0).
 */
internal class WheelSteering {
    private var radius = 0f
    private var square = false
    private var deadZone = 0f
    private var enabled: List<Boolean> = emptyList()

    private var rawX = 0f
    private var rawY = 0f
    private var aimed = -1

    var pointer: Pair<Float, Float> = 0f to 0f
        private set

    /** Returns whether anything changed */
    fun configure(radius: Float, square: Boolean, deadZone: Float, enabled: List<Boolean>): Boolean {
        if (this.radius == radius && this.square == square && this.deadZone == deadZone && this.enabled == enabled) return false
        this.radius = radius
        this.square = square
        this.deadZone = deadZone
        this.enabled = enabled
        return true
    }

    /** Moves the pointer by [dx], [dy] and returns the aimed wedge, or -1 */
    fun steer(dx: Float, dy: Float): Int {
        var x = rawX + dx
        var y = rawY + dy
        if (radius > 0f) {
            val reach = reach(x, y)
            val cap = radius * RAW_CAP
            if (reach > cap) {
                x *= cap / reach
                y *= cap / reach
            }
        }
        rawX = x
        rawY = y
        pointer = if (radius > 0f) rubberBand(x, y) else x to y
        return aim(pointer.first, pointer.second)
    }

    fun reset() {
        rawX = 0f
        rawY = 0f
        aimed = -1
        pointer = 0f to 0f
    }

    private fun reach(x: Float, y: Float): Float = if (square) max(abs(x), abs(y)) else hypot(x, y)

    private fun rubberBand(x: Float, y: Float): Pair<Float, Float> {
        val reach = reach(x, y)
        val knee = radius * KNEE_SHARE
        if (reach <= knee) return x to y
        val band = radius - knee
        val soft = knee + band * (1f - exp(-(reach - knee) / band))
        return x * (soft / reach) to y * (soft / reach)
    }

    private fun aim(x: Float, y: Float): Int {
        val count = enabled.size
        if (count == 0 || reach(x, y) < deadZone) {
            aimed = -1
            return -1
        }
        val sector = 360f / count
        val angle = (Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat() + 90f + 720f) % 360f
        val previous = aimed
        val keep = previous in 0 until count && run {
            var diff = abs(angle - previous * sector) % 360f
            if (diff > 180f) diff = 360f - diff
            diff <= sector / 2f + min(HYSTERESIS_DEG, sector * HYSTERESIS_SHARE)
        }
        if (!keep) aimed = ((angle + sector / 2f) / sector).toInt() % count
        return if (enabled[aimed]) aimed else -1
    }

    private companion object {
        const val RAW_CAP = 1.25f
        const val KNEE_SHARE = 0.85f
        const val HYSTERESIS_DEG = 5f
        const val HYSTERESIS_SHARE = 0.2f
    }
}
