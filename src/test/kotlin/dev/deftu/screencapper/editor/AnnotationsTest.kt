package dev.deftu.screencapper.editor

import androidx.compose.ui.unit.IntSize
import dev.deftu.screencapper.gui.editor.EditSize
import dev.deftu.screencapper.gui.editor.EditTool
import dev.deftu.screencapper.gui.editor.EditorState
import dev.deftu.screencapper.gui.editor.Selection
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import org.polyfrost.compose.backend.PolyCompose
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnnotationsTest {
    init {
        PolyCompose.install()
    }

    private val mono = object : TextMetrics {
        override fun width(text: String, size: Float): Float = text.length * 0.5f * size
        override fun ascent(size: Float): Float = 0.8f * size
        override fun lineHeight(size: Float): Float = 1.25f * size
    }

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()

    private fun solid(width: Int, height: Int, color: Int) = CapturedImage(IntArray(width * height) { color }, width, height)

    private fun CapturedImage.at(x: Int, y: Int): Int = argb[y * width + x]

    private fun render(source: CapturedImage, vararg items: Item) = EditRenderer.apply(source, EditDoc(items = items.toList()))

    private fun difference(a: Int, b: Int): Int = (0..24 step 8).maxOf { abs((a shr it and 0xFF) - (b shr it and 0xFF)) }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 2) =
        assertTrue(difference(expected, actual) <= tolerance, "expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}")

    private fun p(x: Number, y: Number) = ImagePoint(x.toFloat(), y.toFloat())

    @Test
    fun `a rectangle outline is hit on its edge but not in its middle`() {
        val box = Shape(1, ShapeKind.RECTANGLE, p(10, 10), p(50, 40), red, 4f)
        assertTrue(box.hits(p(10, 25), 0f))
        assertTrue(box.hits(p(30, 41.5), 0f))
        assertFalse(box.hits(p(30, 25), 2f))
        assertFalse(box.hits(p(60, 25), 2f))
        assertTrue(box.copy(filled = true).hits(p(30, 25), 0f))
    }

    @Test
    fun `an ellipse outline is hit on its rim but not in its middle or the box corners`() {
        val oval = Shape(1, ShapeKind.ELLIPSE, p(0, 0), p(100, 50), red, 4f)
        assertTrue(oval.hits(p(50, 0), 0f))
        assertTrue(oval.hits(p(100, 25), 0f))
        assertTrue(oval.hits(p(1, 25), 0f))
        assertFalse(oval.hits(p(50, 25), 2f))
        assertFalse(oval.hits(p(5, 5), 2f))
        assertTrue(oval.copy(filled = true).hits(p(50, 25), 0f))
    }

    @Test
    fun `a line is hit along its length only`() {
        val line = Shape(1, ShapeKind.LINE, p(0, 0), p(100, 0), red, 4f)
        assertTrue(line.hits(p(50, 1.5), 0f))
        assertFalse(line.hits(p(50, 6), 2f))
        assertFalse(line.hits(p(110, 0), 2f))
    }

    @Test
    fun `the arrow head ends at the tip and grows with the stroke`() {
        val thin = ShapeGeometry.head(p(0, 0), p(200, 0), 2f)
        val thick = ShapeGeometry.head(p(0, 0), p(200, 0), 8f)
        assertEquals(p(200, 0), thick.tip)
        assertTrue(thick.length > thin.length * 2)
        val half = abs(thick.left.y)
        assertTrue(half > 4f)
        val beside = p(200 - thick.length * 0.75f, half * 0.75f * 0.5f)
        assertTrue(Shape(1, ShapeKind.ARROW, p(0, 0), p(200, 0), red, 8f).hits(beside, 0f))
        assertFalse(Shape(1, ShapeKind.LINE, p(0, 0), p(200, 0), red, 8f).hits(beside, 0f))
    }

    @Test
    fun `shift keeps boxes even and lines on 45 degree steps`() {
        assertEquals(p(30, -30), ShapeGeometry.constrained(ShapeKind.RECTANGLE, p(0, 0), p(30, -12)))
        assertEquals(p(-20, 20), ShapeGeometry.constrained(ShapeKind.ELLIPSE, p(0, 0), p(-5, 20)))
        val flat = ShapeGeometry.constrained(ShapeKind.LINE, p(0, 0), p(100, 8))
        assertEquals(0f, flat.y, 0.001f)
        assertEquals(hypot(100f, 8f), flat.x, 0.01f)
        val diagonal = ShapeGeometry.constrained(ShapeKind.ARROW, p(0, 0), p(50, 45))
        assertEquals(diagonal.x, diagonal.y, 0.01f)
    }

    @Test
    fun `a rectangle outline paints its edge and leaves the middle alone unless filled`() {
        val box = Shape(1, ShapeKind.RECTANGLE, p(5, 5), p(25, 25), white, 2f)
        val out = render(solid(30, 30, black), box)
        assertNear(white, out.at(5, 15))
        assertNear(white, out.at(4, 15))
        assertEquals(black, out.at(2, 15))
        assertEquals(black, out.at(15, 15))
        assertNear(white, render(solid(30, 30, black), box.copy(filled = true)).at(15, 15))
    }

    @Test
    fun `an arrow paints its head wider than its shaft`() {
        val head = ShapeGeometry.head(p(2, 30), p(98, 30), 4f)
        val x = (98 - head.length * 0.7f).toInt()
        val y = 30 + (abs(head.left.y - 30) * 0.4f).toInt()
        assertNear(white, render(solid(100, 60, black), Shape(1, ShapeKind.ARROW, p(2, 30), p(98, 30), white, 4f)).at(x, y))
        assertEquals(black, render(solid(100, 60, black), Shape(1, ShapeKind.LINE, p(2, 30), p(98, 30), white, 4f)).at(x, y))
    }

    @Test
    fun `the marker is see through and does not build up where it crosses itself`() {
        val loop = Drawing(1, listOf(p(5, 15), p(25, 15), p(25, 5), p(15, 5), p(15, 25)), red, 6f, marker = true)
        val out = render(solid(30, 30, white), loop)
        val once = out.at(10, 15)
        assertNear(once, out.at(15, 15))
        assertNear(once, out.at(15, 10))
        assertEquals(255, once shr 16 and 0xFF)
        assertTrue((once shr 8 and 0xFF) in 140..170)
        assertNear(red, render(solid(30, 30, white), loop.copy(marker = false)).at(10, 15))
    }

    @Test
    fun `deleting a step renumbers the rest in the order they were added`() {
        var doc = EditDoc()
        repeat(3) { i -> doc = doc.adding { Badge(it, p(10 * i, 0), 0, red, 8f) }.first }
        assertEquals(listOf(1, 2, 3), doc.items.map { (it as Badge).number })
        val after = doc.without(doc.items[1].id)
        assertEquals(listOf(1, 3), after.items.map { it.id })
        assertEquals(listOf(1, 2), after.items.map { (it as Badge).number })
        val more = after.adding { Badge(it, p(0, 0), 0, red, 8f) }.first
        assertEquals(listOf(1, 2, 3), more.items.map { (it as Badge).number })
    }

    @Test
    fun `a step is hit inside its circle only`() {
        val badge = Badge(1, p(20, 20), 7, white, 12f)
        assertTrue(badge.hits(p(30, 20), 0f))
        assertFalse(badge.hits(p(35, 35), 2f))
    }

    @Test
    fun `step numbers contrast with the step colour`() {
        assertEquals(black, contrastOf(white))
        assertEquals(black, contrastOf(0xFFFFC53D.toInt()))
        assertEquals(white, contrastOf(black))
        assertEquals(white, contrastOf(0xFFE35B5B.toInt()))
    }

    private fun editor(): EditorState = EditorState(mono).apply {
        source = CapturedImage(IntArray(200 * 100) { black }, 200, 100)
        viewSize = IntSize(200, 100)
    }

    private fun EditorState.drag(from: ImagePoint, to: ImagePoint) {
        press(from, pointerScale)
        move(to, pointerScale)
        release(to, pointerScale)
    }

    @Test
    fun `holding shift while dragging a box makes it square`() {
        val state = editor()
        state.select(EditTool.SHAPES)
        state.pickShape(ShapeKind.RECTANGLE)
        state.shift = true
        state.drag(p(10, 10), p(60, 30))
        val shape = state.doc.items.single() as Shape
        assertEquals(p(60, 60), shape.end)
    }

    @Test
    fun `clicking with the steps tool drops the next number`() {
        val state = editor()
        state.select(EditTool.STEPS)
        state.drag(p(20, 20), p(20, 20))
        state.drag(p(60, 20), p(60, 20))
        assertEquals(listOf(1, 2), state.doc.items.map { (it as Badge).number })
        assertEquals(p(60, 20), (state.doc.items.last() as Badge).center)
    }

    @Test
    fun `a new shape is selected and can be moved straight away`() {
        val state = editor()
        state.select(EditTool.SHAPES)
        state.drag(p(10, 50), p(100, 50))
        val shape = state.doc.items.single() as Shape
        assertEquals(Selection.Object(shape.id), state.selection)
        state.drag(p(50, 50), p(50, 70))
        assertEquals(p(10, 70), (state.doc.items.single() as Shape).start)
        state.drag(p(150, 20), p(190, 20))
        assertEquals(2, state.doc.items.size)
        assertEquals(Selection.Object(state.doc.items.last().id), state.selection)
    }

    @Test
    fun `strokes, steps and text are selected once finished`() {
        val state = editor()
        state.select(EditTool.DRAW)
        state.drag(p(10, 10), p(60, 10))
        assertEquals(Selection.Object(state.doc.items.last().id), state.selection)
        state.select(EditTool.STEPS)
        state.drag(p(100, 50), p(100, 50))
        assertEquals(Selection.Object(state.doc.items.last().id), state.selection)
        state.select(EditTool.TEXT)
        state.drag(p(20, 70), p(20, 70))
        state.typeText("hi")
        state.commitDraft()
        assertEquals(Selection.Object(state.doc.items.last().id), state.selection)
    }

    @Test
    fun `double clicking text with the select tool edits it as one undo step`() {
        val state = EditorState(mono).apply {
            source = CapturedImage(IntArray(1000 * 500) { black }, 1000, 500)
            viewSize = IntSize(1000, 500)
        }
        state.select(EditTool.TEXT)
        state.drag(p(20, 40), p(20, 40))
        state.typeText("hi")
        state.commitDraft()
        val box = state.doc.items.single() as TextBox
        state.select(EditTool.SELECT)
        val middle = p((box.area.left + box.area.right) / 2f, (box.area.top + box.area.bottom) / 2f)
        state.drag(middle, middle)
        assertEquals(null, state.draft)
        state.press(middle, state.pointerScale, double = true)
        state.release(middle, state.pointerScale)
        assertEquals(box.id, state.draft?.id)
        state.typeText("hello")
        state.commitDraft()
        assertEquals("hello", (state.doc.items.single() as TextBox).text)
        state.undo()
        assertEquals("hi", (state.doc.items.single() as TextBox).text)
    }

    @Test
    fun `text ignores the size chips and starts at a size that fits the picture`() {
        val state = EditorState(mono).apply {
            source = CapturedImage(IntArray(1000 * 500) { black }, 1000, 500)
            viewSize = IntSize(1000, 500)
        }
        state.select(EditTool.TEXT)
        state.pickSize(EditSize.LARGE)
        state.drag(p(20, 40), p(20, 40))
        state.typeText("hi")
        state.commitDraft()
        val box = state.doc.items.single() as TextBox
        assertEquals(25f, box.size)
        state.pickSize(EditSize.SMALL)
        assertEquals(box, state.doc.items.single())
    }

    @Test
    fun `picking a colour recolours the selected object`() {
        val state = editor()
        state.select(EditTool.SHAPES)
        state.drag(p(10, 50), p(150, 50))
        state.select(EditTool.SELECT)
        state.drag(p(80, 50), p(80, 50))
        state.pickColor(red)
        assertEquals(red, assertNotNull(state.doc.items.single()).color)
    }
}
