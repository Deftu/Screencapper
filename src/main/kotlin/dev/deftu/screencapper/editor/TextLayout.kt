package dev.deftu.screencapper.editor

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

interface TextMetrics {
    fun width(text: String, size: Float): Float

    fun ascent(size: Float): Float

    fun lineHeight(size: Float): Float
}

enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, RIGHT, START, END }

enum class TextEffect { SHADOW, OUTLINE, BOX }

object TextLayout {
    const val MAX_LENGTH = 500
    const val PAD = 0.1f
    const val SHADOW = 0.0625f
    const val MIN_SIZE = 8f
    const val MIN_WIDTH_EM = 2f

    private const val CHROME = 2 * PAD + SHADOW
    private const val AUTO_MIN_EM = 1f

    fun create(id: Int, x: Int, y: Int, text: String, color: Int, size: Float, width: Int?, maxWidth: Int, metrics: TextMetrics): TextBox =
        fit(TextBox(id, x, y, text, color, size, width?.let { max(MIN_WIDTH_EM, it / size) }, maxWidth), metrics)

    fun lines(box: TextBox, metrics: TextMetrics): List<String> {
        val size = box.size
        val outer = box.widthEm?.let { it * size } ?: box.maxWidth.toFloat()
        return TextFit.wrap(box.text.take(MAX_LENGTH), outer - CHROME * size) { metrics.width(it, size) }
    }

    fun fit(box: TextBox, metrics: TextMetrics): TextBox {
        val size = box.size
        val lines = lines(box, metrics)
        val width = box.widthEm?.let { it * size }
            ?: min(box.maxWidth.toFloat(), max(AUTO_MIN_EM * size, lines.maxOfOrNull { metrics.width(it, size) } ?: 0f) + CHROME * size)
        val height = max(1, lines.size) * metrics.lineHeight(size) + CHROME * size
        return box.copy(area = PixelRect(box.x, box.y, box.x + ceil(width).toInt(), box.y + ceil(height).toInt()))
    }

    fun scaled(box: TextBox, factor: Float, handle: Handle, maxSize: Float, metrics: TextMetrics): TextBox {
        val size = (box.size * factor).coerceIn(MIN_SIZE, max(MIN_SIZE, maxSize))
        val em = box.widthEm ?: (box.area.width / box.size)
        return anchored(fit(box.copy(size = size, widthEm = em), metrics), box.area, handle)
    }

    fun widened(box: TextBox, width: Float, handle: Handle, metrics: TextMetrics): TextBox =
        anchored(fit(box.copy(widthEm = max(MIN_WIDTH_EM, width / box.size)), metrics), box.area, handle)

    fun withSize(box: TextBox, size: Float, metrics: TextMetrics): TextBox =
        fit(box.copy(size = size, widthEm = box.widthEm?.let { it * box.size / size }), metrics)

    private fun anchored(box: TextBox, before: PixelRect, handle: Handle): TextBox {
        val left = handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT || handle == Handle.LEFT
        val top = handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT
        return box.at(if (left) before.right - box.area.width else before.left, if (top) before.bottom - box.area.height else before.top)
    }
}
