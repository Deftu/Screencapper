package dev.deftu.screencapper.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewRulesTest {
    private fun PreviewOrder.shown(name: String, savedAt: Long, fromPixels: Boolean): Boolean {
        val ticket = claim(name, savedAt, fromPixels) ?: return false
        return commit(ticket) {}
    }

    @Test
    fun lateFileOfAnEarlierCaptureDoesNotReplaceTheNewerCapture() {
        val order = PreviewOrder()
        assertTrue(order.shown("a.png", 100, fromPixels = true))
        assertTrue(order.shown("b.png", 200, fromPixels = true))
        assertNull(order.claim("a.png", 150, fromPixels = false))
    }

    @Test
    fun lateFileOfAnEarlierCaptureIsIgnoredEvenWithACoarseTimestamp() {
        val order = PreviewOrder()
        assertTrue(order.shown("a.png", 100, fromPixels = true))
        assertTrue(order.shown("b.png", 200, fromPixels = true))
        assertNull(order.claim("a.png", 2_000, fromPixels = false))
    }

    @Test
    fun olderFileArrivingLateDoesNotReplaceANewerFile() {
        val order = PreviewOrder()
        assertNotNull(order.claim("b.png", 200, fromPixels = false))
        assertNull(order.claim("a.png", 100, fromPixels = false))
    }

    @Test
    fun newerFileReplacesAnOlderOne() {
        val order = PreviewOrder()
        assertNotNull(order.claim("a.png", 100, fromPixels = false))
        assertNotNull(order.claim("b.png", 200, fromPixels = false))
    }

    @Test
    fun theSameFileIsOnlyShownOnce() {
        val order = PreviewOrder()
        assertNotNull(order.claim("a.png", 100, fromPixels = false))
        assertNull(order.claim("a.png", 100, fromPixels = false))
    }

    @Test
    fun capturedPixelsAlwaysReplaceThePreview() {
        val order = PreviewOrder()
        assertNotNull(order.claim("b.png", 200, fromPixels = true))
        assertNotNull(order.claim("c.png", 150, fromPixels = true))
        assertNotNull(order.claim("c.png", 150, fromPixels = true))
    }

    @Test
    fun aCaptureThatFailedToShowFallsBackToItsFile() {
        val order = PreviewOrder()
        assertNotNull(order.claim("a.png", 100, fromPixels = true))
        assertNotNull(order.claim("a.png", 120, fromPixels = false))
    }

    @Test
    fun aSupersededLoadCannotCommit() {
        val order = PreviewOrder()
        val older = order.claim("a.png", 100, fromPixels = false)!!
        val newer = order.claim("b.png", 200, fromPixels = true)!!
        var shown = ""
        assertFalse(order.commit(older) { shown = "a" })
        assertTrue(order.commit(newer) { shown = "b" })
        assertEquals("b", shown)
    }

    @Test
    fun aForgottenNameCanBeShownAgain() {
        val order = PreviewOrder()
        assertTrue(order.shown("a.png", 10, fromPixels = false))
        assertFalse(order.shown("a.png", 20, fromPixels = false))
        order.forget("a.png")
        assertTrue(order.shown("a.png", 30, fromPixels = false))
    }
}
