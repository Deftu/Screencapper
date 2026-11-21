package dev.deftu.screencapper.editor

import androidx.compose.ui.unit.IntSize
import dev.deftu.screencapper.gui.editor.EditTool
import dev.deftu.screencapper.gui.editor.EditorState
import dev.deftu.screencapper.gui.editor.Selection
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import org.polyfrost.compose.backend.PolyCompose
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LayersTest {
    init {
        PolyCompose.install()
    }

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()
    private val grey = 0xFF808080.toInt()

    private fun solid(width: Int, height: Int, color: Int) = CapturedImage(IntArray(width * height) { color }, width, height)

    private fun CapturedImage.at(x: Int, y: Int): Int = argb[y * width + x]

    private fun p(x: Number, y: Number) = ImagePoint(x.toFloat(), y.toFloat())

    private fun render(vararg items: Item) = EditRenderer.apply(solid(20, 20, black), EditDoc(items = items.toList()))

    private fun assertNear(expected: Int, actual: Int, message: String = "") {
        val worst = (0..24 step 8).maxOf { abs((expected shr it and 0xFF) - (actual shr it and 0xFF)) }
        assertTrue(worst <= 2, "$message expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}")
    }

    private val leftHalf = Shape(1, ShapeKind.RECTANGLE, p(0, 0), p(9, 20), white, 2f, filled = true)

    @Test
    fun `a blur added after an object blurs that object too`() {
        val out = render(leftHalf, BlurArea(2, PixelRect(0, 0, 20, 20), 20))
        assertEquals(out.at(2, 10), out.at(17, 10))
        assertTrue((out.at(2, 10) and 0xFF) in 90..170)
        assertTrue((out.at(2, 10) and 0xFF) != 0xFF)
    }

    @Test
    fun `an object added after a blur stays sharp on top of it`() {
        val out = render(BlurArea(2, PixelRect(0, 0, 20, 20), 20), leftHalf)
        assertNear(white, out.at(2, 10))
        assertEquals(black, out.at(17, 10))
    }

    @Test
    fun `a cut moves objects below it along with the picture`() {
        val out = render(Shape(1, ShapeKind.RECTANGLE, p(0, 0), p(4, 4), white, 1f, filled = true), CutArea(2, PixelRect(0, 0, 5, 5), 10, 0, grey))
        assertNear(white, out.at(12, 2))
        assertEquals(grey, out.at(2, 2))
        assertEquals(black, out.at(17, 2))
    }

    @Test
    fun `the baked layers plus the live objects on top match the saved picture`() {
        val source = CapturedImage(IntArray(40 * 30) { 0xFF000000.toInt() or (it * 2654435) }, 40, 30)
        val items = listOf(
            Shape(1, ShapeKind.ELLIPSE, p(2, 2), p(30, 20), red, 3f),
            BlurArea(2, PixelRect(5, 5, 25, 25), 4),
            Drawing(3, listOf(p(0, 10), p(39, 12)), white, 3f),
            Drawing(4, listOf(p(10, 0), p(12, 29)), 0xFFFFC53D.toInt(), 8f, marker = true),
            Shape(5, ShapeKind.ARROW, p(35, 25), p(5, 3), white, 2f),
        )
        val split = EditRenderer.layered(items)
        assertEquals(2, split)
        val base = EditRenderer.render(source, items.take(split))
        val full = EditRenderer.apply(source, EditDoc(items = items))
        for (y in 0 until 30) for (x in 0 until 40) {
            assertNear(full.at(x, y), EditRenderer.sample(base, items.drop(split), x, y), "at $x,$y")
        }
    }

    @Test
    fun `nothing needs baking until a blur or cut is added`() {
        assertEquals(0, EditRenderer.layered(listOf(leftHalf, Drawing(2, listOf(p(1, 1)), red, 2f))))
        assertEquals(3, EditRenderer.layered(listOf(leftHalf, CutArea(2, PixelRect(0, 0, 2, 2), 1, 1, grey), BlurArea(3, PixelRect(0, 0, 2, 2), 2), leftHalf.copy(id = 4))))
    }

    private fun editor(): EditorState = EditorState().apply {
        source = CapturedImage(IntArray(200 * 100) { black }, 200, 100)
        viewSize = IntSize(200, 100)
    }

    private fun EditorState.drag(from: ImagePoint, to: ImagePoint) {
        press(from, pointerScale)
        move(to, pointerScale)
        release(to, pointerScale)
    }

    @Test
    fun `a new blur is selected and can be moved, resized and deleted`() {
        val state = editor()
        state.select(EditTool.BLUR)
        state.drag(p(10, 10), p(60, 40))
        val blur = assertIs<BlurArea>(state.doc.items.single())
        assertEquals(PixelRect(10, 10, 60, 40), blur.area)
        assertEquals(Selection.Object(blur.id), state.selection)
        state.drag(p(30, 20), p(40, 30))
        assertEquals(PixelRect(20, 20, 70, 50), (state.doc.items.single() as BlurArea).area)
        state.drag(p(70, 50), p(90, 60))
        assertEquals(PixelRect(20, 20, 90, 60), (state.doc.items.single() as BlurArea).area)
        state.deleteSelected()
        assertTrue(state.doc.items.isEmpty())
    }

    @Test
    fun `a cut out area becomes a layer, and delete clears it`() {
        val state = editor()
        state.select(EditTool.SELECT)
        state.drag(p(10, 10), p(30, 30))
        val rect = PixelRect(10, 10, 30, 30)
        assertEquals(rect, state.pendingLift)
        state.lifted(Selection.Area(rect, grey, solid(20, 20, white)))
        state.drag(p(20, 20), p(50, 25))
        val cut = assertIs<CutArea>(state.doc.items.single())
        assertEquals(30 to 5, cut.dx to cut.dy)
        state.drag(p(60, 30), p(70, 30))
        assertEquals(40, (state.doc.items.single() as CutArea).dx)
        state.deleteSelected()
        assertTrue((state.doc.items.single() as CutArea).dropped)
        assertNotEquals(0, abs(cut.dx))
    }
}
