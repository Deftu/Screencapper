package dev.deftu.screencapper.gui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TapTrackerTest {
    private val tracker = TapTracker(slop = 8f)

    @Test
    fun `a press and release in place is a tap`() {
        tracker.press(10f, 10f)
        assertFalse(tracker.move(13f, 12f))
        assertTrue(tracker.release())
    }

    @Test
    fun `moving past the slop turns the press into a drag`() {
        tracker.press(10f, 10f)
        assertTrue(tracker.move(30f, 10f))
        assertTrue(tracker.dragging)
    }

    @Test
    fun `a drag that comes back is still not a tap`() {
        tracker.press(10f, 10f)
        tracker.move(40f, 10f)
        tracker.move(11f, 10f)
        assertTrue(tracker.dragging)
        assertFalse(tracker.release())
    }

    @Test
    fun `a release without a press is not a tap`() {
        assertFalse(tracker.release())
    }

    @Test
    fun `release ends the gesture`() {
        tracker.press(10f, 10f)
        tracker.move(40f, 10f)
        tracker.release()
        assertFalse(tracker.dragging)
        assertFalse(tracker.move(80f, 10f))
    }
}
