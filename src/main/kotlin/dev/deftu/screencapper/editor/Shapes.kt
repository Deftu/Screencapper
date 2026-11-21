package dev.deftu.screencapper.editor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

enum class ShapeKind { ARROW, RECTANGLE, ELLIPSE, LINE }

data class Shape(
    override val id: Int,
    val kind: ShapeKind,
    val start: ImagePoint,
    val end: ImagePoint,
    override val color: Int,
    val width: Float,
    val filled: Boolean = false,
) : Item {
    val fills: Boolean get() = filled && (kind == ShapeKind.RECTANGLE || kind == ShapeKind.ELLIPSE)

    override val left: Int get() = floor(min(start.x, end.x) - ShapeGeometry.reach(this) - 1f).toInt()
    override val top: Int get() = floor(min(start.y, end.y) - ShapeGeometry.reach(this) - 1f).toInt()
    override val look: Shape get() = moved(-left.toFloat(), -top.toFloat()).copy(id = 0)

    override fun moved(dx: Float, dy: Float): Shape = copy(start = start.offset(dx, dy), end = end.offset(dx, dy))

    override fun hits(point: ImagePoint, slop: Float): Boolean = ShapeField(this).distance(point.x, point.y) <= slop

    override fun bounds(): PixelRect {
        val reach = ShapeGeometry.reach(this)
        return PixelRect.spanning(min(start.x, end.x) - reach, min(start.y, end.y) - reach, max(start.x, end.x) + reach, max(start.y, end.y) + reach)
    }

    override fun recoloured(color: Int): Shape = copy(color = color)

    override fun turned(turn: Turn, width: Int, height: Int): Shape = copy(start = turn.point(start, width, height), end = turn.point(end, width, height))
}

class ArrowHead(val tip: ImagePoint, val left: ImagePoint, val right: ImagePoint, val length: Float)

object ShapeGeometry {
    private const val HEAD_PER_WIDTH = 3.5f
    private const val HEAD_EXTRA = 4f
    private const val HEAD_SPREAD = 0.6f
    private const val HEAD_MAX_SHARE = 0.6f
    private const val SHAFT_INSET = 0.6f
    private const val SNAP = PI / 4

    fun head(start: ImagePoint, end: ImagePoint, width: Float): ArrowHead {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = hypot(dx, dy)
        if (length == 0f) return ArrowHead(end, end, end, 0f)
        val ux = dx / length
        val uy = dy / length
        val size = min(HEAD_PER_WIDTH * width + HEAD_EXTRA, length * HEAD_MAX_SHARE)
        val spread = size * HEAD_SPREAD
        val baseX = end.x - ux * size
        val baseY = end.y - uy * size
        return ArrowHead(end, ImagePoint(baseX + uy * spread, baseY - ux * spread), ImagePoint(baseX - uy * spread, baseY + ux * spread), size)
    }

    fun shaftEnd(start: ImagePoint, end: ImagePoint, head: ArrowHead): ImagePoint {
        val length = hypot(end.x - start.x, end.y - start.y)
        if (length == 0f) return start
        val back = head.length * SHAFT_INSET / length
        return end.offset(-(end.x - start.x) * back, -(end.y - start.y) * back)
    }

    fun reach(shape: Shape): Float {
        val half = shape.width / 2f
        if (shape.kind != ShapeKind.ARROW) return half
        return max(half, head(shape.start, shape.end, shape.width).length * HEAD_SPREAD)
    }

    fun constrained(kind: ShapeKind, start: ImagePoint, end: ImagePoint): ImagePoint {
        val dx = end.x - start.x
        val dy = end.y - start.y
        return when (kind) {
            ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> {
                val side = max(abs(dx), abs(dy))
                ImagePoint(start.x + if (dx < 0) -side else side, start.y + if (dy < 0) -side else side)
            }

            ShapeKind.ARROW, ShapeKind.LINE -> {
                val length = hypot(dx.toDouble(), dy.toDouble())
                val angle = round(atan2(dy.toDouble(), dx.toDouble()) / SNAP) * SNAP
                ImagePoint(start.x + (length * cos(angle)).toFloat(), start.y + (length * sin(angle)).toFloat())
            }
        }
    }
}

internal class ShapeField(private val shape: Shape) {
    private val half = shape.width / 2f
    private val centerX = (shape.start.x + shape.end.x) / 2f
    private val centerY = (shape.start.y + shape.end.y) / 2f
    private val radiusX = abs(shape.end.x - shape.start.x) / 2f
    private val radiusY = abs(shape.end.y - shape.start.y) / 2f
    private val head = if (shape.kind == ShapeKind.ARROW) ShapeGeometry.head(shape.start, shape.end, shape.width) else null
    private val shaftEnd = head?.let { ShapeGeometry.shaftEnd(shape.start, shape.end, it) }
    private val turning = head?.let { cross(it.tip, it.left, it.right) } ?: 0f

    fun distance(x: Float, y: Float): Float = when (shape.kind) {
        ShapeKind.LINE -> EditRenderer.distance(x, y, shape.start, shape.end) - half
        ShapeKind.ARROW -> {
            val shaft = EditRenderer.distance(x, y, shape.start, shaftEnd ?: shape.end) - half
            if (head == null || turning == 0f) shaft else min(shaft, triangle(x, y, head))
        }

        ShapeKind.RECTANGLE -> outlined(max(abs(x - centerX) - radiusX, abs(y - centerY) - radiusY))
        ShapeKind.ELLIPSE -> outlined(ellipse(x - centerX, y - centerY))
    }

    private fun outlined(edge: Float): Float = if (shape.fills) edge - half else abs(edge) - half

    private fun ellipse(x: Float, y: Float): Float {
        val a = max(radiusX, MIN_RADIUS)
        val b = max(radiusY, MIN_RADIUS)
        val level = x * x / (a * a) + y * y / (b * b) - 1f
        val slope = 2f * hypot(x / (a * a), y / (b * b))
        return if (slope < 1e-6f) -min(a, b) else level / slope
    }

    private fun triangle(x: Float, y: Float, head: ArrowHead): Float =
        max(edge(x, y, head.tip, head.left), max(edge(x, y, head.left, head.right), edge(x, y, head.right, head.tip)))

    private fun edge(x: Float, y: Float, from: ImagePoint, to: ImagePoint): Float {
        val ex = to.x - from.x
        val ey = to.y - from.y
        val length = hypot(ex, ey)
        if (length == 0f) return Float.NEGATIVE_INFINITY
        val side = ex * (y - from.y) - ey * (x - from.x)
        return if (turning > 0f) -side / length else side / length
    }

    private fun cross(a: ImagePoint, b: ImagePoint, c: ImagePoint): Float = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private companion object {
        const val MIN_RADIUS = 0.5f
    }
}
