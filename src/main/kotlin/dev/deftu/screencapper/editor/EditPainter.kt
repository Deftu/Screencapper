package dev.deftu.screencapper.editor

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import dev.deftu.screencapper.editor.EditorText.advance
import kotlin.math.abs
import kotlin.math.min

private const val MARKER_ALPHA = 0.4f
private const val LIGHT_TEXT = 140
private const val SHADOW_ALPHA = 140
private const val BOX_ALPHA = 200
private const val OUTLINE = 0.06f
private const val BOX_RADIUS = 0.25f
private const val ONE_DIGIT = 1.1f
private const val TWO_DIGITS = 0.95f
private const val MORE_DIGITS = 0.75f

fun contrastOf(color: Int): Int {
    val light = ((color shr 16 and 0xFF) * 299 + (color shr 8 and 0xFF) * 587 + (color and 0xFF) * 114) / 1000 >= LIGHT_TEXT
    return if (light) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}

fun DrawScope.drawItem(item: Item) {
    when (item) {
        is Drawing -> drawDrawing(item)
        is Shape -> drawShape(item)
        is Badge -> drawBadge(item)
        is TextBox -> drawTextBox(item)
        is BlurArea, is CutArea -> Unit
    }
}

private fun ImagePoint.offset(): Offset = Offset(x, y)

private fun DrawScope.drawDrawing(drawing: Drawing) {
    val points = drawing.points.ifEmpty { return }
    if (!drawing.marker) return drawStroke(points, Color(drawing.color), drawing.width)
    val bounds = drawing.bounds()
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat()), Paint().apply { alpha = MARKER_ALPHA })
    try {
        drawStroke(points, Color(drawing.color), drawing.width)
    } finally {
        canvas.restore()
    }
}

private fun DrawScope.drawStroke(points: List<ImagePoint>, color: Color, width: Float) {
    val start = points.first().offset()
    if (points.size == 1) return drawCircle(color, width / 2f, start)
    val path = Path().apply {
        moveTo(start.x, start.y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawShape(shape: Shape) {
    val color = Color(shape.color)
    val width = shape.width
    val start = shape.start.offset()
    val end = shape.end.offset()
    val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
    val size = Size(abs(end.x - start.x), abs(end.y - start.y))
    val grown = topLeft - Offset(width / 2f, width / 2f)
    val grownSize = Size(size.width + width, size.height + width)
    when (shape.kind) {
        ShapeKind.LINE -> drawLine(color, start, end, width, StrokeCap.Round)
        ShapeKind.ARROW -> {
            val head = ShapeGeometry.head(shape.start, shape.end, width)
            drawLine(color, start, ShapeGeometry.shaftEnd(shape.start, shape.end, head).offset(), width, StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(head.tip.x, head.tip.y)
                lineTo(head.left.x, head.left.y)
                lineTo(head.right.x, head.right.y)
                close()
            }, color)
        }

        ShapeKind.RECTANGLE -> if (shape.fills) drawRect(color, grown, grownSize) else drawRect(color, topLeft, size, style = Stroke(width, join = StrokeJoin.Miter))
        ShapeKind.ELLIPSE -> if (shape.fills) drawOval(color, grown, grownSize) else drawOval(color, topLeft, size, style = Stroke(width))
    }
}

private fun DrawScope.drawBadge(badge: Badge) {
    if (badge.radius <= 0f) return
    val center = badge.center.offset()
    drawCircle(Color(badge.color), badge.radius, center)
    val label = badge.number.toString()
    val scale = when (label.length) {
        1 -> ONE_DIGIT
        2 -> TWO_DIGITS
        else -> MORE_DIGITS
    }
    val line = EditorText.line(label, badge.radius * scale)
    drawText(line, Color(contrastOf(badge.color)), Offset(center.x - line.advance() / 2f, center.y - line.multiParagraph.height / 2f))
}

private fun DrawScope.drawTextBox(box: TextBox) {
    val area = box.area
    if (box.text.isBlank() || area.isEmpty) return
    val size = box.size
    val pad = TextLayout.PAD * size
    val shadow = TextLayout.SHADOW * size
    val ascent = EditorText.ascent(size)
    val lineHeight = EditorText.lineHeight(size)
    val textColor = Color(box.color)
    val contrast = Color(contrastOf(box.color))
    val lines = TextLayout.lines(box, EditorText).map { EditorText.line(it, size) }
    fun baseline(index: Int) = pad + ascent + index * lineHeight
    translate(area.left.toFloat(), area.top.toFloat()) {
        clipRect(0f, 0f, area.width.toFloat(), area.height.toFloat()) {
            when (box.effect) {
                TextEffect.SHADOW -> lines.forEachIndexed { index, line ->
                    drawTextLine(line, contrast.copy(alpha = SHADOW_ALPHA / 255f), pad + shadow, baseline(index) + shadow)
                    drawTextLine(line, textColor, pad, baseline(index))
                }

                TextEffect.OUTLINE -> {
                    val reach = OUTLINE * size
                    lines.forEachIndexed { index, line ->
                        drawTextLine(line, contrast, pad, baseline(index), Stroke(reach * 2f, join = StrokeJoin.Round))
                        drawTextLine(line, textColor, pad, baseline(index))
                    }
                }

                TextEffect.BOX -> {
                    val radius = min(BOX_RADIUS * size, min(area.width, area.height) / 2f)
                    drawRoundRect(box.backdrop?.let(::Color) ?: contrast.copy(alpha = BOX_ALPHA / 255f), Offset.Zero, Size(area.width.toFloat(), area.height.toFloat()), CornerRadius(radius))
                    lines.forEachIndexed { index, line -> drawTextLine(line, textColor, pad + shadow / 2f, baseline(index) + shadow / 2f) }
                }
            }
        }
    }
}

private fun DrawScope.drawTextLine(line: TextLayoutResult, color: Color, x: Float, baseline: Float, style: DrawStyle = Fill) {
    drawText(line, color, Offset(x, baseline - line.firstBaseline), drawStyle = style)
}
