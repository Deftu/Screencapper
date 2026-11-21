package dev.deftu.screencapper.editor

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextLayoutTest {
    private val mono = object : TextMetrics {
        override fun width(text: String, size: Float): Float = text.length * 0.5f * size
        override fun ascent(size: Float): Float = 0.8f * size
        override fun lineHeight(size: Float): Float = 1.25f * size
    }
    private val white = 0xFFFFFFFF.toInt()
    private val chrome = 2 * TextLayout.PAD + TextLayout.SHADOW
    private val sentence = "the quick brown fox jumps over the lazy dog"

    private fun fixed(text: String, width: Int = 200, size: Float = 20f, x: Int = 10, y: Int = 10) =
        TextLayout.create(1, x, y, text, white, size, width, 1000, mono)

    private fun auto(text: String, maxWidth: Int = 300, size: Float = 20f) =
        TextLayout.create(1, 10, 10, text, white, size, null, maxWidth, mono)

    private fun expectedHeight(lines: Int, size: Float) = ceil(lines * 1.25f * size + chrome * size).toInt()

    @Test
    fun `height follows the content with no spare room`() {
        val one = fixed("one")
        val many = fixed(sentence)
        assertEquals(1, TextLayout.lines(one, mono).size)
        assertEquals(expectedHeight(1, 20f), one.area.height)
        val lines = TextLayout.lines(many, mono).size
        assertTrue(lines > 1)
        assertEquals(expectedHeight(lines, 20f), many.area.height)
        assertEquals(200, many.area.width)
    }

    @Test
    fun `an auto width box grows as you type then wraps at its max`() {
        val short = auto("Hi")
        val longer = auto("Hello there")
        assertTrue(longer.area.width > short.area.width)
        assertEquals(1, TextLayout.lines(longer, mono).size)
        val wrapped = auto("a".repeat(10) + " " + "b".repeat(10) + " " + "c".repeat(10) + " " + "d".repeat(10))
        assertTrue(TextLayout.lines(wrapped, mono).size > 1)
        assertTrue(wrapped.area.width <= 300)
        assertNull(wrapped.widthEm)
    }

    @Test
    fun `corner scaling keeps the layout and scales the box`() {
        val box = fixed(sentence)
        val scaled = TextLayout.scaled(box, 2f, Handle.BOTTOM_RIGHT, 200f, mono)
        assertEquals(40f, scaled.size)
        assertEquals(TextLayout.lines(box, mono), TextLayout.lines(scaled, mono))
        assertEquals(400, scaled.area.width)
        assertEquals(box.area.left, scaled.area.left)
        assertEquals(box.area.top, scaled.area.top)
        assertEquals(expectedHeight(TextLayout.lines(box, mono).size, 40f), scaled.area.height)
    }

    @Test
    fun `scaling from the top left keeps the bottom right corner`() {
        val box = fixed(sentence, x = 300, y = 300)
        val scaled = TextLayout.scaled(box, 0.5f, Handle.TOP_LEFT, 200f, mono)
        assertEquals(box.area.right, scaled.area.right)
        assertEquals(box.area.bottom, scaled.area.bottom)
        assertEquals(TextLayout.lines(box, mono), TextLayout.lines(scaled, mono))
    }

    @Test
    fun `scaling is clamped to sensible font sizes`() {
        val box = fixed(sentence)
        assertEquals(TextLayout.MIN_SIZE, TextLayout.scaled(box, 0.01f, Handle.BOTTOM_RIGHT, 60f, mono).size)
        assertEquals(60f, TextLayout.scaled(box, 100f, Handle.BOTTOM_RIGHT, 60f, mono).size)
    }

    @Test
    fun `an auto box keeps its look when scaled`() {
        val box = auto("Hello there")
        val scaled = TextLayout.scaled(box, 1.5f, Handle.BOTTOM_RIGHT, 200f, mono)
        assertEquals(TextLayout.lines(box, mono), TextLayout.lines(scaled, mono))
        assertNotNull(scaled.widthEm)
    }

    @Test
    fun `side handles reflow the text and keep the font size`() {
        val box = fixed(sentence)
        val narrow = TextLayout.widened(box, 100f, Handle.RIGHT, mono)
        assertEquals(20f, narrow.size)
        assertEquals(100, narrow.area.width)
        assertEquals(box.area.left, narrow.area.left)
        assertTrue(TextLayout.lines(narrow, mono).size > TextLayout.lines(box, mono).size)
        assertTrue(narrow.area.height > box.area.height)
        val fromLeft = TextLayout.widened(box, 100f, Handle.LEFT, mono)
        assertEquals(box.area.right, fromLeft.area.right)
        assertEquals(box.area.top, fromLeft.area.top)
    }

    @Test
    fun `a box can't be made narrower than its minimum`() {
        val box = TextLayout.widened(fixed("hi"), 1f, Handle.RIGHT, mono)
        assertEquals((TextLayout.MIN_WIDTH_EM * 20f).toInt(), box.area.width)
    }

    @Test
    fun `changing the font size keeps a fixed width and leaves auto width alone`() {
        val bigger = TextLayout.withSize(fixed(sentence), 40f, mono)
        assertEquals(200, bigger.area.width)
        assertEquals(40f, bigger.size)
        assertTrue(TextLayout.lines(bigger, mono).size > TextLayout.lines(fixed(sentence), mono).size)
        val autoBigger = TextLayout.withSize(auto("Hi"), 40f, mono)
        assertNull(autoBigger.widthEm)
        assertTrue(autoBigger.area.width > auto("Hi").area.width)
    }

    @Test
    fun `moving keeps the layout`() {
        val box = fixed(sentence)
        val moved = box.moved(5f, 7f)
        assertEquals(box.area.offset(5, 7), moved.area)
        assertEquals(box.look, moved.look)
    }

    @Test
    fun `every text style takes the same box`() {
        val plain = auto("Hello there")
        for (effect in TextEffect.entries) assertEquals(plain.area, TextLayout.fit(plain.copy(effect = effect, backdrop = 0xFFFF0000.toInt()), mono).area, "$effect")
    }
}
