package dev.deftu.screencapper.screenshot.capture.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureGestureTest {
    @Test
    fun releaseBeforeThresholdIsATap() {
        val gesture = HoldGesture { 300L }
        gesture.press(1_000L)
        assertEquals(HoldGesture.Outcome.NONE, gesture.update(1_100L, down = true))
        assertEquals(HoldGesture.Outcome.TAP, gesture.update(1_250L, down = false))
        assertFalse(gesture.active)
        assertEquals(HoldGesture.Outcome.NONE, gesture.update(1_300L, down = false))
    }

    @Test
    fun holdingPastThresholdFiresHoldOnceThenRelease() {
        val gesture = HoldGesture { 300L }
        gesture.press(0L)
        assertEquals(HoldGesture.Outcome.NONE, gesture.update(299L, down = true))
        assertEquals(HoldGesture.Outcome.HOLD, gesture.update(300L, down = true))
        assertEquals(HoldGesture.Outcome.NONE, gesture.update(800L, down = true))
        assertEquals(HoldGesture.Outcome.RELEASED, gesture.update(900L, down = false))
        assertFalse(gesture.active)
    }

    @Test
    fun releaseSeenLateStillCountsAsTap() {
        // A long frame can hide the release; the key was never seen held past the threshold, so it stays a tap.
        val gesture = HoldGesture { 300L }
        gesture.press(0L)
        assertEquals(HoldGesture.Outcome.TAP, gesture.update(2_000L, down = false))
    }

    @Test
    fun pendingShotSavedWhenDecisionComesFirst() {
        val kept = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        val shot = PendingShot<String>({ kept += it }, { dropped += it })
        assertTrue(shot.decide(save = true))
        assertTrue(kept.isEmpty())
        shot.arrive("frame")
        assertEquals(listOf("frame"), kept)
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun pendingShotSavedWhenCaptureComesFirst() {
        val kept = mutableListOf<String>()
        val shot = PendingShot<String>({ kept += it }, { error("dropped") })
        shot.arrive("frame")
        assertTrue(kept.isEmpty())
        shot.decide(save = true)
        assertEquals(listOf("frame"), kept)
    }

    @Test
    fun pendingShotDiscardReleasesExactlyOnce() {
        val dropped = mutableListOf<String>()
        val shot = PendingShot<String>({ error("kept") }, { dropped += it })
        shot.arrive("frame")
        assertTrue(shot.decide(save = false))
        assertFalse(shot.decide(save = true))
        assertEquals(listOf("frame"), dropped)

        val late = PendingShot<String>({ error("kept") }, { dropped += it })
        late.decide(save = false)
        late.arrive("late")
        assertEquals(listOf("frame", "late"), dropped)
    }
}
