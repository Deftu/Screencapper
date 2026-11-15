package dev.deftu.screencapper.gui

import java.awt.RenderingHints
import java.awt.image.BufferedImage

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
}
