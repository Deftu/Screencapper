package dev.deftu.screencapper.gui.wheel

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WheelSteeringTest {
    private fun steering(count: Int = 5, square: Boolean = false) = WheelSteering().apply {
        configure(radius = 100f, square = square, deadZone = 20f, enabled = List(count) { true })
    }

    @Test
    fun insideTheDeadZoneAimsNothing() {
        val wheel = steering()
        assertEquals(-1, wheel.steer(10f, -10f))
    }

    @Test
    fun firstWedgeIsStraightUpAndTheRestGoClockwise() {
        val wheel = steering()
        assertEquals(0, wheel.steer(0f, -60f))
        wheel.reset()
        assertEquals(1, wheel.steer(60f, -10f))
        wheel.reset()
        assertEquals(4, wheel.steer(-60f, -10f))
    }

    @Test
    fun aimSticksAcrossABorderUntilPastTheHysteresis() {
        val wheel = steering(count = 4)
        assertEquals(0, wheel.steer(0f, -80f))
        val r = 80f
        val inside = Math.toRadians(48.0 - 90.0)
        wheel.reset()
        assertEquals(0, wheel.steer(0f, -80f))
        assertEquals(0, wheel.steer((Math.cos(inside) * r).toFloat(), (Math.sin(inside) * r + 80f).toFloat()))
        val past = Math.toRadians(60.0 - 90.0)
        val (x, y) = wheel.pointer
        assertEquals(1, wheel.steer((Math.cos(past) * r).toFloat() - x, (Math.sin(past) * r).toFloat() - y))
    }

    @Test
    fun pointerIsHeldInsideTheRim() {
        val wheel = steering()
        wheel.steer(0f, -10_000f)
        val (x, y) = wheel.pointer
        assertTrue(hypot(x, y) <= 100f, "pointer at ($x, $y)")
        assertEquals(0, wheel.steer(0f, 0f))
    }

    @Test
    fun pullingBackFromFarOutTakesEffectAtOnce() {
        val wheel = steering()
        wheel.steer(0f, -10_000f)
        wheel.steer(0f, 10_000f)
        val (_, y) = wheel.pointer
        assertTrue(y > 0f, "pointer should cross to the other side, y = $y")
    }

    @Test
    fun squareWheelMeasuresReachAlongTheSquare() {
        val wheel = steering(square = true)
        wheel.steer(90f, -90f)
        val (x, y) = wheel.pointer
        assertTrue(max(abs(x), abs(y)) <= 100f)
        assertTrue(hypot(x, y) > 100f, "a square wheel reaches into its corners")
        wheel.reset()
        assertEquals(-1, wheel.steer(15f, 15f))
    }

    @Test
    fun disabledWedgeAimsNothing() {
        val wheel = WheelSteering().apply { configure(100f, false, 20f, listOf(false, true, true, true)) }
        assertEquals(-1, wheel.steer(0f, -60f))
    }
}
