package dev.deftu.screencapper.editor

import dev.deftu.screencapper.gui.ZoomTransform
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorViewportTest {
    private val fit = ZoomTransform()

    private fun assertNear(expected: Float, actual: Float) = assertEquals(expected, actual, 0.01f)

    @Test
    fun `view corners map to image corners at fit`() {
        val viewport = EditorViewport(1000f, 500f, PixelRect.of(4000, 2000))
        assertEquals(0.25f, viewport.scale(fit))
        assertEquals(ImagePoint(0f, 0f), viewport.toImage(fit, 0f, 0f))
        assertEquals(ImagePoint(2000f, 1000f), viewport.toImage(fit, 500f, 250f))
        assertEquals(ImagePoint(4000f, 2000f), viewport.toImage(fit, 1000f, 500f))
    }

    @Test
    fun `a cropped view maps into the original image`() {
        val viewport = EditorViewport(1000f, 500f, PixelRect(1000, 500, 3000, 1500))
        assertEquals(0.5f, viewport.scale(fit))
        assertEquals(ImagePoint(1000f, 500f), viewport.toImage(fit, 0f, 0f))
        assertEquals(ImagePoint(2000f, 1000f), viewport.toImage(fit, 500f, 250f))
    }

    @Test
    fun `letterboxed space maps outside the image`() {
        val viewport = EditorViewport(1000f, 1000f, PixelRect.of(2000, 1000))
        assertNear(250f, viewport.toViewY(fit, 0f))
        assertNear(-500f, viewport.toImage(fit, 500f, 0f).y)
    }

    @Test
    fun `view and image mapping round trips when zoomed and panned`() {
        val viewport = EditorViewport(800f, 600f, PixelRect(100, 50, 1700, 950))
        val transform = ZoomTransform(3f, 120f, -40f)
        val point = viewport.toImage(transform, 312f, 207f)
        assertNear(312f, viewport.toViewX(transform, point.x))
        assertNear(207f, viewport.toViewY(transform, point.y))
        assertEquals(viewport.scale(fit) * 3f, viewport.scale(transform))
    }
}
