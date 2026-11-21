package dev.deftu.screencapper.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import org.polyfrost.compose.backend.PolyImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object EditRenderer {
    private const val MIN_BLOCK = 4
    private const val BLOCKS_ACROSS = 120

    fun visibleArea(doc: EditDoc, width: Int, height: Int): PixelRect {
        val full = PixelRect.of(width, height)
        return doc.crop?.clampTo(full)?.takeUnless { it.isEmpty } ?: full
    }

    fun blurBlock(width: Int, height: Int): Int = max(MIN_BLOCK, (max(width, height).toFloat() / BLOCKS_ACROSS).roundToInt())

    fun ringAverage(image: CapturedImage, area: PixelRect): Int {
        val width = image.width
        val full = PixelRect.of(width, image.height)
        val inner = area.clampTo(full)
        val outer = PixelRect(inner.left - 1, inner.top - 1, inner.right + 1, inner.bottom + 1).clampTo(full)
        val sums = LongArray(4)
        var count = 0
        fun add(x: Int, y: Int) {
            val pixel = image.argb[y * width + x]
            sums[0] += (pixel ushr 24).toLong()
            sums[1] += (pixel shr 16 and 0xFF).toLong()
            sums[2] += (pixel shr 8 and 0xFF).toLong()
            sums[3] += (pixel and 0xFF).toLong()
            count++
        }
        for (y in outer.top until outer.bottom) {
            if (y < inner.top || y >= inner.bottom) {
                for (x in outer.left until outer.right) add(x, y)
            } else {
                if (outer.left < inner.left) add(outer.left, y)
                if (outer.right > inner.right) add(outer.right - 1, y)
            }
        }
        if (count == 0) {
            for (y in inner.top until inner.bottom) for (x in inner.left until inner.right) add(x, y)
        }
        if (count == 0) return 0
        fun channel(i: Int) = ((sums[i] + count / 2) / count).toInt()
        return (channel(0) shl 24) or (channel(1) shl 16) or (channel(2) shl 8) or channel(3)
    }

    fun region(image: CapturedImage, area: PixelRect): CapturedImage {
        val clamped = area.clampTo(PixelRect.of(image.width, image.height))
        val out = IntArray(clamped.width * clamped.height)
        for (y in 0 until clamped.height) System.arraycopy(image.argb, (clamped.top + y) * image.width + clamped.left, out, y * clamped.width, clamped.width)
        return CapturedImage(out, clamped.width, clamped.height)
    }

    fun layered(items: List<Item>): Int = items.indexOfLast { it is Layer } + 1

    fun render(image: CapturedImage, items: List<Item>): CapturedImage {
        if (items.isEmpty()) return image
        val bitmap = image.toImageBitmap()
        draw(bitmap) { for (item in items) paint(bitmap, item) }
        return bitmap.toCapturedImage()
    }

    fun cropped(image: CapturedImage, doc: EditDoc): CapturedImage {
        val area = visibleArea(doc, image.width, image.height)
        if (area.width == image.width && area.height == image.height) return image
        return region(image, area)
    }

    fun apply(source: CapturedImage, doc: EditDoc, render: (CapturedImage, List<Item>) -> CapturedImage = ::render): CapturedImage =
        cropped(render(doc.orientation.apply(source), doc.items), doc)

    fun sample(base: CapturedImage, items: List<Item>, x: Int, y: Int): Int {
        val px = x.coerceIn(0, base.width - 1)
        val py = y.coerceIn(0, base.height - 1)
        if (items.any { it is Layer }) return render(base, items).argb[py * base.width + px]
        val pixel = CapturedImage(intArrayOf(base.argb[py * base.width + px]), 1, 1).toImageBitmap()
        draw(pixel) { translate(-px.toFloat(), -py.toFloat()) { for (item in items) drawItem(item) } }
        return pixel.toCapturedImage().argb[0]
    }

    private fun DrawScope.paint(target: ImageBitmap, item: Item) {
        when (item) {
            is BlurArea -> pixelate(target, item)
            is CutArea -> cut(target, item)
            else -> drawItem(item)
        }
    }

    private fun DrawScope.pixelate(target: ImageBitmap, blur: BlurArea) {
        val area = blur.area.clampTo(PixelRect.of(target.width, target.height)).takeUnless { it.isEmpty } ?: return
        val block = blur.block.coerceAtLeast(1)
        val blocks = ImageBitmap((area.width + block - 1) / block, (area.height + block - 1) / block)
        val grid = IntSize(blocks.width, blocks.height)
        draw(blocks) { drawImage(target, area.topLeft(), area.size(), IntOffset.Zero, grid, filterQuality = FilterQuality.Low) }
        drawImage(blocks, IntOffset.Zero, grid, area.topLeft(), area.size(), filterQuality = FilterQuality.None)
    }

    private fun DrawScope.cut(target: ImageBitmap, cut: CutArea) {
        val from = cut.area.clampTo(PixelRect.of(target.width, target.height)).takeUnless { it.isEmpty } ?: return
        val patch = if (cut.dropped) null else ImageBitmap(from.width, from.height).also { patch ->
            draw(patch) { drawImage(target, from.topLeft(), from.size(), IntOffset.Zero, from.size(), filterQuality = FilterQuality.None) }
        }
        drawRect(Color(cut.fill), Offset(from.left.toFloat(), from.top.toFloat()), Size(from.width.toFloat(), from.height.toFloat()), blendMode = BlendMode.Src)
        if (patch != null) drawImage(patch, IntOffset.Zero, from.size(), from.offset(cut.dx, cut.dy).topLeft(), from.size(), filterQuality = FilterQuality.None)
    }

    private fun PixelRect.topLeft(): IntOffset = IntOffset(left, top)

    private fun PixelRect.size(): IntSize = IntSize(width, height)

    private fun draw(target: ImageBitmap, block: DrawScope.() -> Unit) {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(target), Size(target.width.toFloat(), target.height.toFloat()), block)
    }

    internal fun distance(px: Float, py: Float, a: ImagePoint, b: ImagePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = dx * dx + dy * dy
        val t = if (length == 0f) 0f else (((px - a.x) * dx + (py - a.y) * dy) / length).coerceIn(0f, 1f)
        val cx = a.x + t * dx - px
        val cy = a.y + t * dy - py
        return sqrt(cx * cx + cy * cy)
    }
}

internal fun CapturedImage.toImageBitmap(): ImageBitmap {
    val image = ImageBitmap(width, height)
    (image as PolyImageBitmap).borrowBitmap { bitmap ->
        val bytes = bitmap.pixels
        for (i in 0 until width * height) {
            val color = argb[i]
            bytes[i * 4] = (color shr 16).toByte()
            bytes[i * 4 + 1] = (color shr 8).toByte()
            bytes[i * 4 + 2] = color.toByte()
            bytes[i * 4 + 3] = (color ushr 24).toByte()
        }
    }
    return image
}

internal fun ImageBitmap.toCapturedImage(): CapturedImage {
    val argb = IntArray(width * height)
    readPixels(argb, 0, 0, width, height)
    return CapturedImage(argb, width, height)
}
