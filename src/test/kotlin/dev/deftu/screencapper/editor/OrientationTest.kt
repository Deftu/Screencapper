package dev.deftu.screencapper.editor

import androidx.compose.ui.unit.IntSize
import dev.deftu.screencapper.gui.editor.EditorState
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import org.polyfrost.compose.backend.PolyCompose
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrientationTest {
    init {
        PolyCompose.install()
    }

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()
    private val grey = 0xFF808080.toInt()

    private fun numbered(width: Int, height: Int) = CapturedImage(IntArray(width * height) { 0xFF000000.toInt() or (it * 2654435) }, width, height)

    private fun CapturedImage.at(x: Int, y: Int): Int = argb[y * width + x]

    private fun p(x: Number, y: Number) = ImagePoint(x.toFloat(), y.toFloat())

    @Test
    fun `turning the picture moves each pixel where the point mapping says`() {
        val source = numbered(3, 2)
        var points = List(6) { p(it % 3 + 0.5f, it / 3 + 0.5f) }
        var size = PixelRect.of(3, 2)
        var orientation = Orientation()
        for (turn in listOf(Turn.RIGHT, Turn.FLIP_HORIZONTAL, Turn.LEFT, Turn.FLIP_VERTICAL, Turn.RIGHT)) {
            points = points.map { turn.point(it, size.width, size.height) }
            size = turn.size(size.width, size.height)
            orientation = orientation.then(turn)
            val image = orientation.apply(source)
            assertEquals(size, PixelRect.of(image.width, image.height))
            points.forEachIndexed { i, point -> assertEquals(source.argb[i], image.at(floor(point.x).toInt(), floor(point.y).toInt()), "$turn pixel $i") }
        }
    }

    @Test
    fun `four quarter turns or two flips change nothing`() {
        val none = Orientation()
        assertEquals(none, none.then(Turn.RIGHT).then(Turn.RIGHT).then(Turn.RIGHT).then(Turn.RIGHT))
        assertEquals(none, none.then(Turn.RIGHT).then(Turn.LEFT))
        assertEquals(none, none.then(Turn.FLIP_HORIZONTAL).then(Turn.FLIP_HORIZONTAL))
        assertEquals(none, none.then(Turn.FLIP_VERTICAL).then(Turn.FLIP_VERTICAL))
        assertEquals(none.then(Turn.RIGHT).then(Turn.RIGHT), none.then(Turn.FLIP_HORIZONTAL).then(Turn.FLIP_VERTICAL))
    }

    private val doc = EditDoc(
        crop = PixelRect(10, 20, 50, 40),
        items = listOf(
            CutArea(5, PixelRect(0, 0, 10, 10), 5, 2, grey),
            Drawing(1, listOf(p(10, 20), p(30, 25)), white, 3f),
            Shape(2, ShapeKind.ARROW, p(12, 22), p(45, 35), red, 4f),
            Shape(3, ShapeKind.RECTANGLE, p(20, 21), p(40, 38), white, 2f),
            Drawing(4, listOf(p(10, 30), p(50, 30)), red, 6f, marker = true),
        ),
    )

    @Test
    fun `turning the document carries the crop, edits and objects with the picture`() {
        val right = doc.turned(Turn.RIGHT, 60, 50)
        assertEquals(Orientation().then(Turn.RIGHT), right.orientation)
        assertEquals(PixelRect(10, 10, 30, 50), right.crop)
        val move = right.items[0] as CutArea
        assertEquals(PixelRect(40, 0, 50, 10), move.area)
        assertEquals(-2 to 5, move.dx to move.dy)
        assertEquals(p(30, 10), (right.items[1] as Drawing).points.first())
    }

    @Test
    fun `a turned document renders like the turned saved picture`() {
        val source = numbered(60, 50)
        for (turn in Turn.entries) {
            val size = turn.size(60, 50)
            val expected = Orientation().then(turn).apply(EditRenderer.apply(source, doc.copy(crop = null)))
            val actual = EditRenderer.apply(source, doc.copy(crop = null).turned(turn, 60, 50))
            assertEquals(size, PixelRect.of(actual.width, actual.height))
            val plain = Orientation().then(turn).apply(source)
            assertTrue(plain.argb.indices.count { actual.argb[it] != plain.argb[it] } > 100, "$turn drew nothing")
            val worst = expected.argb.indices.maxOf { i -> (0..24 step 8).maxOf { shift -> abs((expected.argb[i] shr shift and 0xFF) - (actual.argb[i] shr shift and 0xFF)) } }
            assertTrue(worst <= 8, "$turn differs by $worst")
        }
    }

    @Test
    fun `four quarter turns give back the same document`() {
        var turned = doc
        repeat(4) { i -> turned = turned.turned(Turn.RIGHT, if (i % 2 == 0) 60 else 50, if (i % 2 == 0) 50 else 60) }
        assertEquals(doc, turned)
    }

    @Test
    fun `text stays upright and keeps its size while its centre follows the picture`() {
        val box = TextBox(1, 10, 5, "hi", red, 10f, null, 1000, PixelRect(10, 5, 40, 15))
        val turned = EditDoc(items = listOf(box)).turned(Turn.RIGHT, 60, 50).items.single() as TextBox
        assertEquals(30, turned.area.width)
        assertEquals(10, turned.area.height)
        val centre = Turn.RIGHT.point(p(25, 10), 60, 50)
        assertTrue(abs((turned.area.left + turned.area.right) / 2f - centre.x) <= 1f)
        assertTrue(abs((turned.area.top + turned.area.bottom) / 2f - centre.y) <= 1f)
    }

    @Test
    fun `after turning, the pointer maps into the turned picture and undo turns it back`() {
        val state = EditorState().apply {
            source = CapturedImage(IntArray(1000 * 500) { black }, 1000, 500)
            viewSize = IntSize(1000, 500)
        }
        state.turn(Turn.RIGHT)
        assertEquals(PixelRect.of(500, 1000), state.area)
        assertEquals(p(250, 500), state.pointer(500f, 250f))
        assertEquals(p(0, 0), state.pointer(375f, 0f))
        state.undo()
        assertEquals(PixelRect.of(1000, 500), state.area)
    }
}
