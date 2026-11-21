package dev.deftu.screencapper.editor

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun clampTo(bounds: PixelRect): PixelRect {
        val l = left.coerceIn(bounds.left, bounds.right)
        val t = top.coerceIn(bounds.top, bounds.bottom)
        return PixelRect(l, t, right.coerceIn(l, bounds.right), bottom.coerceIn(t, bounds.bottom))
    }

    fun offset(dx: Int, dy: Int): PixelRect = PixelRect(left + dx, top + dy, right + dx, bottom + dy)

    fun contains(x: Float, y: Float, slop: Float = 0f): Boolean =
        x >= left - slop && x <= right + slop && y >= top - slop && y <= bottom + slop

    companion object {
        fun of(width: Int, height: Int): PixelRect = PixelRect(0, 0, width, height)

        fun spanning(x0: Float, y0: Float, x1: Float, y1: Float): PixelRect =
            PixelRect(min(x0, x1).roundToInt(), min(y0, y1).roundToInt(), max(x0, x1).roundToInt(), max(y0, y1).roundToInt())
    }
}

data class ImagePoint(val x: Float, val y: Float) {
    fun offset(dx: Float, dy: Float): ImagePoint = ImagePoint(x + dx, y + dy)
}

sealed interface Item {
    val id: Int
    val color: Int
    val left: Int
    val top: Int
    val look: Item

    fun moved(dx: Float, dy: Float): Item

    fun hits(point: ImagePoint, slop: Float): Boolean

    fun bounds(): PixelRect

    fun recoloured(color: Int): Item

    fun turned(turn: Turn, width: Int, height: Int): Item
}

data class Drawing(override val id: Int, val points: List<ImagePoint>, override val color: Int, val width: Float, val marker: Boolean = false) : Item {
    override val left: Int get() = floor(points.minOf { it.x } - width / 2f - 1f).toInt()
    override val top: Int get() = floor(points.minOf { it.y } - width / 2f - 1f).toInt()
    override val look: Drawing get() = moved(-left.toFloat(), -top.toFloat()).copy(id = 0)

    override fun moved(dx: Float, dy: Float): Drawing = copy(points = points.map { it.offset(dx, dy) })

    override fun recoloured(color: Int): Drawing = copy(color = color)

    override fun turned(turn: Turn, width: Int, height: Int): Drawing = copy(points = points.map { turn.point(it, width, height) })

    override fun hits(point: ImagePoint, slop: Float): Boolean {
        val reach = width / 2f + slop
        val segments = if (points.size == 1) listOf(points[0] to points[0]) else points.zipWithNext()
        return segments.any { (a, b) -> EditRenderer.distance(point.x, point.y, a, b) <= reach }
    }

    override fun bounds(): PixelRect {
        val half = width / 2f
        return PixelRect.spanning(points.minOf { it.x } - half, points.minOf { it.y } - half, points.maxOf { it.x } + half, points.maxOf { it.y } + half)
    }
}

data class TextBox(
    override val id: Int,
    val x: Int,
    val y: Int,
    val text: String,
    override val color: Int,
    val size: Float,
    val widthEm: Float?,
    val maxWidth: Int,
    val area: PixelRect = PixelRect(x, y, x, y),
    val effect: TextEffect = TextEffect.SHADOW,
    val backdrop: Int? = null,
) : Item {
    override val left: Int get() = area.left
    override val top: Int get() = area.top

    override fun recoloured(color: Int): TextBox = copy(color = color)

    override fun turned(turn: Turn, width: Int, height: Int): TextBox {
        val spot = turn.rect(area, width, height)
        return at(spot.left + Math.floorDiv(spot.width - area.width, 2), spot.top + Math.floorDiv(spot.height - area.height, 2))
    }

    override fun moved(dx: Float, dy: Float): TextBox = at(x + dx.roundToInt(), y + dy.roundToInt())

    override fun hits(point: ImagePoint, slop: Float): Boolean = area.contains(point.x, point.y, slop)

    override fun bounds(): PixelRect = area

    fun at(left: Int, top: Int): TextBox = copy(x = left, y = top, area = area.offset(left - x, top - y))

    override val look: TextBox get() = at(0, 0).copy(id = 0)
}

data class EditDoc(
    val crop: PixelRect? = null,
    val items: List<Item> = emptyList(),
    val nextId: Int = 1,
    val orientation: Orientation = Orientation(),
) {
    val isEmpty: Boolean get() = crop == null && items.isEmpty() && orientation == Orientation()

    fun turned(turn: Turn, width: Int, height: Int): EditDoc = copy(
        crop = crop?.let { turn.rect(it, width, height) },
        items = items.map { it.turned(turn, width, height) },
        orientation = orientation.then(turn),
    )

    fun withCrop(area: PixelRect): EditDoc = copy(crop = area)

    fun adding(make: (Int) -> Item): Pair<EditDoc, Item> {
        val doc = copy(items = items + make(nextId), nextId = nextId + 1).numbered()
        return doc to doc.items.last()
    }

    fun replacing(item: Item): EditDoc = copy(items = items.map { if (it.id == item.id) item else it })

    fun without(id: Int): EditDoc = copy(items = items.filterNot { it.id == id }).numbered()

    private fun numbered(): EditDoc {
        val order = items.filterIsInstance<Badge>().sortedBy { it.id }.mapIndexed { index, badge -> badge.id to index + 1 }.toMap()
        if (order.isEmpty()) return this
        return copy(items = items.map { if (it is Badge) it.copy(number = order.getValue(it.id)) else it })
    }

    fun item(id: Int): Item? = items.firstOrNull { it.id == id }

    fun itemAt(point: ImagePoint, slop: Float): Item? = items.lastOrNull { it.hits(point, slop) }
}

data class DocHistory(val past: List<EditDoc> = emptyList(), val current: EditDoc = EditDoc(), val future: List<EditDoc> = emptyList()) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun push(doc: EditDoc): DocHistory = if (doc == current) this else DocHistory(past + current, doc, emptyList())

    fun undo(): DocHistory = if (canUndo) DocHistory(past.dropLast(1), past.last(), listOf(current) + future) else this

    fun redo(): DocHistory = if (canRedo) DocHistory(past + current, future.first(), future.drop(1)) else this
}
