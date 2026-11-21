package dev.deftu.screencapper.editor

import dev.deftu.screencapper.screenshot.capture.CapturedImage
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.polyfrost.compose.backend.PolyCompose

class EditRendererTest {
    init {
        PolyCompose.install()
    }

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()
    private val grey = 0xFF808080.toInt()

    private fun numbered(width: Int, height: Int) = CapturedImage(IntArray(width * height) { 0xFF000000.toInt() or it }, width, height)

    private fun solid(width: Int, height: Int, color: Int) = CapturedImage(IntArray(width * height) { color }, width, height)

    private fun CapturedImage.at(x: Int, y: Int): Int = argb[y * width + x]

    private fun render(source: CapturedImage, doc: EditDoc) = EditRenderer.apply(source, doc)

    private fun items(vararg items: Item) = EditDoc(items = items.toList())

    private fun p(x: Number, y: Number) = ImagePoint(x.toFloat(), y.toFloat())

    private fun difference(a: Int, b: Int): Int = (0..24 step 8).maxOf { abs((a shr it and 0xFF) - (b shr it and 0xFF)) }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 2, message: String? = null) =
        assertTrue(difference(expected, actual) <= tolerance, "${message ?: ""} expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}")

    @Test
    fun `crop keeps only the chosen area`() {
        val out = render(numbered(4, 5), EditDoc(crop = PixelRect(1, 1, 3, 4)))
        assertEquals(2, out.width)
        assertEquals(3, out.height)
        assertEquals(numbered(4, 5).at(1, 1), out.at(0, 0))
        assertEquals(numbered(4, 5).at(2, 3), out.at(1, 2))
    }

    @Test
    fun `crop outside the image is clamped to the image`() {
        assertEquals(PixelRect(0, 2, 4, 4), EditRenderer.visibleArea(EditDoc(crop = PixelRect(-5, 2, 10, 4)), 4, 5))
    }

    @Test
    fun `an empty crop or no crop means the whole image`() {
        assertEquals(PixelRect(0, 0, 4, 4), EditRenderer.visibleArea(EditDoc(crop = PixelRect(10, 10, 12, 12)), 4, 4))
        assertEquals(PixelRect(0, 0, 4, 4), EditRenderer.visibleArea(EditDoc(), 4, 4))
    }

    @Test
    fun `a cropped drawing lands at the same image position`() {
        val doc = EditDoc(crop = PixelRect(4, 4, 16, 16), items = listOf(Drawing(1, listOf(p(10, 10)), red, 6f)))
        val out = render(solid(20, 20, black), doc)
        assertEquals(12, out.width)
        assertNear(red, out.at(6, 6))
        assertEquals(black, out.at(0, 0))
    }

    @Test
    fun `blur turns its area into even blocks and leaves the rest alone`() {
        val source = numbered(4, 4)
        val out = render(source, items(BlurArea(1, PixelRect(2, -3, 9, 2), 8)))
        val inside = out.at(2, 0)
        for (y in 0 until 2) for (x in 2 until 4) assertEquals(inside, out.at(x, y))
        val average = listOf(source.at(2, 0), source.at(3, 0), source.at(2, 1), source.at(3, 1)).sumOf { it and 0xFF }.plus(2) / 4
        assertTrue(abs(average - (inside and 0xFF)) <= 1)
        for (y in 0 until 4) for (x in 0 until 4) if (x < 2 || y >= 2) assertEquals(source.at(x, y), out.at(x, y))
    }

    @Test
    fun `a large blur is made of blocks of the chosen size`() {
        val source = CapturedImage(IntArray(64 * 64) { 0xFF000000.toInt() or (it * 2654435 and 0xFFFFFF) }, 64, 64)
        val out = render(source, items(BlurArea(1, PixelRect(0, 0, 64, 64), 16)))
        for (block in 0 until 16) {
            val left = block % 4 * 16
            val top = block / 4 * 16
            val color = out.at(left, top)
            for (y in top until top + 16) for (x in left until left + 16) assertEquals(color, out.at(x, y), "block $block at $x,$y")
        }
        assertTrue(out.at(0, 0) != out.at(16, 0) || out.at(0, 0) != out.at(0, 16))
    }

    @Test
    fun `blur fully outside the image changes nothing`() {
        val source = numbered(4, 4)
        assertContentEquals(source.argb, render(source, items(BlurArea(1, PixelRect(10, 10, 20, 20), 2))).argb)
    }

    @Test
    fun `a blur leaves drawings added after it sharp`() {
        val doc = items(BlurArea(1, PixelRect(0, 0, 10, 10), 5), Drawing(2, listOf(p(0, 5), p(10, 5)), white, 4f))
        val out = render(solid(10, 10, black), doc)
        assertNear(white, out.at(5, 5))
        assertEquals(black, out.at(5, 0))
    }

    @Test
    fun `a moved area lands at its new place and the hole takes the fill`() {
        val source = numbered(6, 6)
        val out = render(source, items(CutArea(1, PixelRect(0, 0, 2, 2), 3, 1, grey)))
        assertEquals(source.at(0, 0), out.at(3, 1))
        assertEquals(source.at(1, 1), out.at(4, 2))
        assertEquals(grey, out.at(0, 0))
        assertEquals(grey, out.at(1, 0))
        assertEquals(source.at(5, 5), out.at(5, 5))
    }

    @Test
    fun `an area moved partly off the image is clipped`() {
        val source = numbered(4, 4)
        val out = render(source, items(CutArea(1, PixelRect(0, 0, 2, 2), 3, 3, grey)))
        assertEquals(source.at(0, 0), out.at(3, 3))
        assertEquals(grey, out.at(0, 0))
    }

    @Test
    fun `an area moved onto itself keeps its own pixels`() {
        val source = numbered(6, 6)
        val out = render(source, items(CutArea(1, PixelRect(0, 0, 4, 4), 1, 0, grey)))
        assertEquals(source.at(0, 0), out.at(1, 0))
        assertEquals(source.at(3, 3), out.at(4, 3))
        assertEquals(grey, out.at(0, 2))
    }

    @Test
    fun `a dropped area is only filled`() {
        val source = numbered(4, 4)
        val out = render(source, items(CutArea(1, PixelRect(1, 1, 3, 3), 0, 0, grey, dropped = true)))
        assertEquals(grey, out.at(1, 1))
        assertEquals(grey, out.at(2, 2))
        assertEquals(source.at(0, 0), out.at(0, 0))
    }

    @Test
    fun `pixel edits apply in order`() {
        val source = numbered(8, 8)
        val blurred = render(source, items(BlurArea(1, PixelRect(0, 0, 2, 2), 2)))
        val out = render(source, items(BlurArea(1, PixelRect(0, 0, 2, 2), 2), CutArea(2, PixelRect(0, 0, 2, 2), 4, 4, grey)))
        assertEquals(blurred.at(0, 0), out.at(4, 4))
    }

    @Test
    fun `the hole fill is the average of the ring around the area`() {
        val image = solid(5, 5, white)
        for (y in 1 until 4) for (x in 1 until 4) image.argb[y * 5 + x] = black
        assertEquals(white, EditRenderer.ringAverage(image, PixelRect(1, 1, 4, 4)))
        val split = CapturedImage(IntArray(16) { if (it % 4 < 2) black else white }, 4, 4)
        val mixed = EditRenderer.ringAverage(split, PixelRect(1, 1, 3, 3))
        assertTrue((mixed and 0xFF) in 120..135)
    }

    @Test
    fun `the whole image as area averages itself`() {
        assertEquals(red, EditRenderer.ringAverage(solid(3, 3, red), PixelRect(0, 0, 3, 3)))
    }

    @Test
    fun `a drawing paints pixels near its line only`() {
        val out = render(solid(20, 20, black), items(Drawing(1, listOf(p(4, 10), p(16, 10)), white, 4f)))
        assertNear(white, out.at(10, 9))
        assertNear(white, out.at(10, 10))
        assertEquals(black, out.at(10, 14))
        assertEquals(black, out.at(10, 5))
        assertEquals(black, out.at(0, 10))
    }

    @Test
    fun `a single point drawing paints a dot`() {
        val out = render(solid(10, 10, black), items(Drawing(1, listOf(p(5, 5)), white, 4f)))
        assertNear(white, out.at(5, 5))
        assertNear(white, out.at(4, 4))
        assertEquals(black, out.at(0, 0))
    }

    @Test
    fun `later items draw over earlier ones`() {
        val doc = items(Drawing(1, listOf(p(5, 5)), white, 6f), Drawing(2, listOf(p(5, 5)), red, 6f))
        assertNear(red, render(solid(10, 10, black), doc).at(5, 5))
    }

    @Test
    fun `the source image is left untouched`() {
        val source = solid(4, 4, black)
        val doc = items(
            BlurArea(1, PixelRect(0, 0, 4, 4), 2),
            CutArea(2, PixelRect(0, 0, 2, 2), 1, 1, white),
            Drawing(3, listOf(p(0, 0), p(4, 4)), white, 3f),
        )
        render(source, doc)
        EditRenderer.render(source, doc.items)
        assertTrue(source.argb.all { it == black })
    }

    @Test
    fun `blur blocks grow with the image`() {
        assertTrue(EditRenderer.blurBlock(3840, 2160) > EditRenderer.blurBlock(854, 480))
        assertTrue(EditRenderer.blurBlock(16, 16) >= 4)
    }

    @Test
    fun `the eyedropper sees the picture with every object on top`() {
        val source = solid(30, 30, 0xFF336699.toInt())
        val doc = EditDoc(
            items = listOf(
                Drawing(1, listOf(p(5, 5), p(25, 5)), white, 4f),
                Shape(2, ShapeKind.RECTANGLE, p(10, 10), p(20, 20), red, 2f, filled = true),
                Drawing(3, listOf(p(0, 15), p(30, 15)), 0xFFFFC53D.toInt(), 8f, marker = true),
            ),
        )
        val full = EditRenderer.apply(source, doc)
        assertTrue(full.at(5, 5) != source.at(5, 5))
        for ((x, y) in listOf(5 to 5, 15 to 15, 2 to 15, 28 to 28, 15 to 12, 6 to 4)) {
            assertNear(full.at(x, y), EditRenderer.sample(source, doc.items, x, y), message = "at $x,$y")
        }
    }

    @Test
    fun `a see-through hole fill replaces the pixels under it`() {
        val fill = 0x80FFFFFF.toInt()
        val out = render(solid(4, 4, black), items(CutArea(1, PixelRect(1, 1, 3, 3), 0, 0, fill, dropped = true)))
        assertNear(fill, out.at(1, 1))
        assertEquals(black, out.at(0, 0))
    }

    @Test
    fun `a marker that crosses itself is evenly see-through`() {
        val marker = Drawing(1, listOf(p(4, 4), p(36, 36), p(36, 4), p(4, 36)), white, 6f, marker = true)
        val out = render(solid(40, 40, black), items(marker))
        assertNear(out.at(10, 10), out.at(20, 20), tolerance = 3)
        assertTrue(out.at(10, 10) != white && out.at(10, 10) != black)
    }

    @Test
    fun `renders on several threads at once match a single render`() {
        val source = numbered(64, 64)
        val doc = items(
            BlurArea(1, PixelRect(0, 0, 20, 20), 4),
            CutArea(2, PixelRect(30, 30, 40, 40), 5, 5, grey),
            Drawing(3, listOf(p(0, 60), p(60, 0)), red, 3f, marker = true),
        )
        val expected = render(source, doc).argb
        val results = arrayOfNulls<IntArray>(8)
        (0 until 8).map { i -> thread { results[i] = render(source, doc).argb } }.forEach { it.join() }
        for (result in results) assertContentEquals(expected, result)
    }
}
