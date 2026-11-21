package dev.deftu.screencapper.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditDocTest {
    private val red = 0xFFFF0000.toInt()

    private fun line(id: Int, width: Float = 2f) = Drawing(id, listOf(ImagePoint(10f, 10f), ImagePoint(50f, 10f)), red, width)

    @Test
    fun `a thin line is hit within the slop and missed outside it`() {
        val drawing = line(1)
        assertTrue(drawing.hits(ImagePoint(30f, 10f), 0f))
        assertTrue(drawing.hits(ImagePoint(30f, 15f), 5f))
        assertFalse(drawing.hits(ImagePoint(30f, 17f), 5f))
        assertFalse(drawing.hits(ImagePoint(70f, 10f), 5f))
    }

    @Test
    fun `a thick line is hit across its whole width`() {
        assertTrue(line(1, 20f).hits(ImagePoint(30f, 19f), 0f))
    }

    @Test
    fun `a single dot can be hit`() {
        assertTrue(Drawing(1, listOf(ImagePoint(5f, 5f)), red, 2f).hits(ImagePoint(7f, 5f), 2f))
    }

    @Test
    fun `the topmost item under the pointer wins`() {
        val box = TextBox(2, 0, 0, "hi", red, 10f, null, 1000, PixelRect(0, 0, 60, 20))
        val doc = EditDoc(items = listOf(line(1), box))
        assertEquals(box, doc.itemAt(ImagePoint(30f, 10f), 2f))
        assertEquals(line(1), EditDoc(items = listOf(box, line(1))).itemAt(ImagePoint(30f, 10f), 2f))
        assertNull(doc.itemAt(ImagePoint(200f, 200f), 2f))
    }

    @Test
    fun `moving keeps the item and shifts it`() {
        val moved = line(1).moved(5f, -2f)
        assertEquals(ImagePoint(15f, 8f), moved.points.first())
        val box = TextBox(2, 0, 0, "hi", red, 10f, null, 1000, PixelRect(0, 0, 10, 10)).moved(3.4f, 2.6f)
        assertEquals(PixelRect(3, 3, 13, 13), box.area)
    }

    @Test
    fun `new items get fresh ids and can be replaced or removed`() {
        val (first, a) = EditDoc().adding { line(it) }
        val (second, b) = first.adding { line(it) }
        assertEquals(1, a.id)
        assertEquals(2, b.id)
        assertEquals(listOf(b), second.without(1).items)
        val moved = b.moved(1f, 1f)
        assertEquals(moved, second.replacing(moved).item(2))
    }

    @Test
    fun `history steps back and forward and a new edit clears redo`() {
        val a = EditDoc(crop = PixelRect(0, 0, 1, 1))
        val b = EditDoc(crop = PixelRect(0, 0, 2, 2))
        val c = EditDoc(crop = PixelRect(0, 0, 3, 3))
        val history = DocHistory().push(a).push(b).undo()
        assertEquals(a, history.current)
        assertEquals(b, history.redo().current)
        val branched = history.push(c)
        assertEquals(c, branched.current)
        assertFalse(branched.canRedo)
        assertEquals(EditDoc(), branched.undo().undo().current)
    }

    @Test
    fun `pushing the same document adds no step`() {
        val history = DocHistory().push(EditDoc(crop = PixelRect(0, 0, 1, 1)))
        assertEquals(history, history.push(history.current))
    }
}
