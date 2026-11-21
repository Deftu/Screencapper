package dev.deftu.screencapper.gui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoubleClickDetectorTest {
    private val detector = DoubleClickDetector(timeoutMillis = 300, slop = 8f)

    @Test
    fun `two quick presses in place are a double click`() {
        assertFalse(detector.press(1000, 10f, 10f))
        assertTrue(detector.press(1200, 12f, 11f))
    }

    @Test
    fun `presses further apart in time are not a double click`() {
        assertFalse(detector.press(1000, 10f, 10f))
        assertFalse(detector.press(1301, 10f, 10f))
    }

    @Test
    fun `presses far apart on screen are not a double click`() {
        assertFalse(detector.press(1000, 10f, 10f))
        assertFalse(detector.press(1100, 40f, 10f))
    }

    @Test
    fun `a slow second press starts a new pair`() {
        assertFalse(detector.press(1000, 10f, 10f))
        assertFalse(detector.press(2000, 10f, 10f))
        assertTrue(detector.press(2100, 10f, 10f))
    }

    @Test
    fun `a third quick press does not fire again`() {
        assertFalse(detector.press(1000, 10f, 10f))
        assertTrue(detector.press(1100, 10f, 10f))
        assertFalse(detector.press(1200, 10f, 10f))
        assertTrue(detector.press(1300, 10f, 10f))
    }

    @Test
    fun `reset forgets the pending press`() {
        assertFalse(detector.press(1000, 10f, 10f))
        detector.reset()
        assertFalse(detector.press(1100, 10f, 10f))
    }
}
