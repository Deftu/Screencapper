package dev.deftu.screencapper.gui.wheel

import kotlin.test.Test
import kotlin.test.assertEquals

class WheelInputTest {
    private val left = 1
    private val right = 3

    @Test
    fun leftPicksAndRightCancels() {
        assertEquals(WheelClick.PICK, wheelClick(left, left, right, swapped = false))
        assertEquals(WheelClick.CANCEL, wheelClick(right, left, right, swapped = false))
    }

    @Test
    fun swappedButtonsUseThePhysicalButton() {
        assertEquals(WheelClick.CANCEL, wheelClick(left, left, right, swapped = true))
        assertEquals(WheelClick.PICK, wheelClick(right, left, right, swapped = true))
    }

    @Test
    fun otherButtonsDoNothing() {
        assertEquals(WheelClick.NONE, wheelClick(2, left, right, swapped = false))
        assertEquals(WheelClick.NONE, wheelClick(2, left, right, swapped = true))
    }

    @Test
    fun steeringFollowsTheLookCurveAndDensity() {
        assertEquals(1.9, steerScale(0.5, 1f), 1e-9)
        assertEquals(0.95, steerScale(0.5, 2f), 1e-9)
        assertEquals(1.9 * 8 * 0.8 * 0.8 * 0.8, steerScale(1.0, 1f), 1e-9)
    }

    @Test
    fun aZeroDensityDoesNotBlowUp() {
        assertEquals(190.0, steerScale(0.5, 0f), 1e-4)
    }
}
