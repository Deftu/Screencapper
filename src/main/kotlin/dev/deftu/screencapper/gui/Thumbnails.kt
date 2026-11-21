package dev.deftu.screencapper.gui

import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object Thumbnails {
    fun downscale(image: BufferedImage, maxWidth: Int): BufferedImage {
        if (image.width <= maxWidth) return image
        val height = (image.height * (maxWidth.toDouble() / image.width)).toInt().coerceAtLeast(1)
        val out = BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, maxWidth, height, null)
        g.dispose()
        return out
    }

    fun decodeRegion(file: File, x: Int, y: Int, width: Int, height: Int, subsampling: Int): BufferedImage? = runCatching {
        ImageIO.createImageInputStream(file)?.use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return@use null
            try {
                reader.input = stream
                val left = x.coerceIn(0, reader.getWidth(0))
                val top = y.coerceIn(0, reader.getHeight(0))
                val right = (x + width).coerceIn(left, reader.getWidth(0))
                val bottom = (y + height).coerceIn(top, reader.getHeight(0))
                if (right == left || bottom == top) return@use null
                val param = reader.defaultReadParam
                param.sourceRegion = Rectangle(left, top, right - left, bottom - top)
                val step = subsampling.coerceAtLeast(1)
                param.setSourceSubsampling(step, step, 0, 0)
                reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()
}
