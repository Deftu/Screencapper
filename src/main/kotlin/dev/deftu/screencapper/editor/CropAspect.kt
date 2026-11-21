package dev.deftu.screencapper.editor

import kotlin.math.abs
import kotlin.math.min

enum class CropAspect(val label: String, private val width: Int, private val height: Int) {
    FREE("Free", 0, 0),
    ORIGINAL("Original", 0, 0),
    WIDE("16:9", 16, 9),
    CLASSIC("4:3", 4, 3),
    SQUARE("1:1", 1, 1),
    TALL("9:16", 9, 16);

    fun ratio(bounds: PixelRect): Float? = when (this) {
        FREE -> null
        ORIGINAL -> if (bounds.isEmpty) null else bounds.width.toFloat() / bounds.height
        else -> width.toFloat() / height
    }

    companion object {
        fun lock(start: ImagePoint, end: ImagePoint, ratio: Float?, bounds: PixelRect): PixelRect {
            if (ratio == null || ratio <= 0f) return PixelRect.spanning(start.x, start.y, end.x, end.y).clampTo(bounds)
            val x = start.x.coerceIn(bounds.left.toFloat(), bounds.right.toFloat())
            val y = start.y.coerceIn(bounds.top.toFloat(), bounds.bottom.toFloat())
            val sx = if (end.x < x) -1f else 1f
            val sy = if (end.y < y) -1f else 1f
            var width = abs(end.x - x)
            var height = abs(end.y - y)
            if (width > height * ratio) width = height * ratio else height = width / ratio
            val roomX = if (sx > 0) bounds.right - x else x - bounds.left
            val roomY = if (sy > 0) bounds.bottom - y else y - bounds.top
            if (width > roomX) {
                width = roomX
                height = width / ratio
            }
            if (height > roomY) {
                height = roomY
                width = min(width, height * ratio)
            }
            return PixelRect.spanning(x, y, x + sx * width, y + sy * height).clampTo(bounds)
        }
    }
}
