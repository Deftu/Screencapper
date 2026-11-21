package dev.deftu.screencapper.editor

import androidx.compose.ui.unit.IntSize
import dev.deftu.screencapper.gui.editor.EditTool
import dev.deftu.screencapper.gui.editor.EditorState
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EditorPointerTest {
    private fun EditorState.at(x: Float, y: Float): ImagePoint = assertNotNull(pointer(x, y))

    private fun editor(): EditorState = EditorState().apply {
        source = CapturedImage(IntArray(1000 * 500) { 0xFF000000.toInt() }, 1000, 500)
        viewSize = IntSize(1000, 500)
    }

    @Test
    fun `the pointer maps through the canvas size it has right now`() {
        val state = editor()
        assertEquals(ImagePoint(250f, 100f), state.at(250f, 100f))
        state.viewSize = IntSize(1000, 450)
        assertEquals(ImagePoint(250f, 100f), state.at(275f, 90f))
        assertEquals(0.9f, state.pointerScale, 0.0001f)
    }

    @Test
    fun `a drag that spans a canvas resize moves by the distance in the image`() {
        val state = editor()
        state.select(EditTool.DRAW)
        state.press(state.at(100f, 100f), state.pointerScale)
        state.move(state.at(200f, 100f), state.pointerScale)
        state.release(state.at(200f, 100f), state.pointerScale)

        state.select(EditTool.SELECT)
        state.press(state.at(150f, 100f), state.pointerScale)
        state.viewSize = IntSize(1000, 450)
        state.move(state.at(275f, 90f), state.pointerScale)
        state.release(state.at(275f, 90f), state.pointerScale)

        val drawing = state.doc.items.single() as Drawing
        assertEquals(ImagePoint(200f, 100f), drawing.points.first())
        assertEquals(ImagePoint(300f, 100f), drawing.points.last())
    }
}
