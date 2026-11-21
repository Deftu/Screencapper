package dev.deftu.screencapper.editor

import androidx.compose.ui.unit.IntSize
import dev.deftu.screencapper.gui.editor.EditTool
import dev.deftu.screencapper.gui.editor.EditorState
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CropAspectTest {
    private val bounds = PixelRect.of(200, 100)

    private fun p(x: Number, y: Number) = ImagePoint(x.toFloat(), y.toFloat())

    @Test
    fun `presets give their ratio and original follows the picture`() {
        assertNull(CropAspect.FREE.ratio(bounds))
        assertEquals(2f, CropAspect.ORIGINAL.ratio(bounds))
        assertEquals(16f / 9f, CropAspect.WIDE.ratio(bounds))
        assertEquals(9f / 16f, CropAspect.TALL.ratio(bounds))
        assertEquals(1f, CropAspect.SQUARE.ratio(bounds))
    }

    @Test
    fun `a locked drag fits the ratio inside the dragged box`() {
        assertEquals(PixelRect(0, 0, 100, 56), CropAspect.lock(p(0, 0), p(100, 100), 16f / 9f, bounds))
        assertEquals(PixelRect(60, 0, 150, 90), CropAspect.lock(p(150, 90), p(50, 0), 1f, bounds))
    }

    @Test
    fun `a locked drag that runs past the edge shrinks and keeps its ratio`() {
        assertEquals(PixelRect(150, 10, 200, 60), CropAspect.lock(p(150, 10), p(400, 300), 1f, bounds))
        assertEquals(PixelRect(0, 0, 200, 100), CropAspect.lock(p(0, 0), p(900, 900), 2f, bounds))
    }

    @Test
    fun `a free drag is just the dragged box`() {
        assertEquals(PixelRect(20, 0, 60, 100), CropAspect.lock(p(60, -10), p(20, 130), null, bounds))
    }

    @Test
    fun `cropping with a preset keeps the ratio`() {
        val state = EditorState().apply {
            source = CapturedImage(IntArray(400 * 200) { 0xFF000000.toInt() }, 400, 200)
            viewSize = IntSize(400, 200)
        }
        state.select(EditTool.CROP)
        state.aspect = CropAspect.SQUARE
        state.press(p(10, 10), state.pointerScale)
        state.move(p(200, 90), state.pointerScale)
        state.release(p(200, 90), state.pointerScale)
        assertEquals(PixelRect(10, 10, 90, 90), assertNotNull(state.doc.crop))
    }
}
