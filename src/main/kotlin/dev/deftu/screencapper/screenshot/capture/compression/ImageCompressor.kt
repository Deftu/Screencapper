package dev.deftu.screencapper.screenshot.capture.compression

import dev.deftu.screencapper.screenshot.capture.CapturedImage
import dev.deftu.screencapper.screenshot.capture.FastCapture
import dev.deftu.screencapper.screenshot.capture.PngEncoder
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.roundToInt

/** Applies a [CompressionProfile] to captured pixels */
object ImageCompressor {
    private const val JPEG_METADATA_FORMAT = "javax_imageio_jpeg_image_1.0"

    /**
     * Writes [image] to [out] following [profile]. [extraScale] shrinks it further on top of the profile's own resize.
     * [text] is kept in PNGs; JPEGs carry none.
     */
    fun encode(image: CapturedImage, profile: CompressionProfile, text: Map<String, String>, out: OutputStream, extraScale: Double = 1.0) {
        val scaled = resize(image, profile, extraScale)
        when (profile.format) {
            ImageFormat.PNG -> if (profile.paletteColors > 0) {
                val quantized = PaletteQuantizer.quantize(scaled.argb, scaled.width, scaled.height, profile.paletteColors, profile.dither)
                PngEncoder.encodeIndexed(quantized.indices, quantized.palette, scaled.width, scaled.height, profile.pngLevel, FastCapture.encoderPool, FastCapture.encoderThreads, out, text)
            } else {
                PngEncoder.encode(scaled.argb, scaled.width, scaled.height, profile.pngLevel, FastCapture.encoderPool, FastCapture.encoderThreads, out, text)
            }

            ImageFormat.JPEG -> writeJpeg(scaled, profile.jpegQuality, profile.chroma, out)
        }
    }

    fun encodeBytes(image: CapturedImage, profile: CompressionProfile, text: Map<String, String>, extraScale: Double = 1.0): ByteArray =
        ByteArrayOutputStream().also { encode(image, profile, text, it, extraScale) }.toByteArray()

    /** Saves [image] untouched as a lossless PNG */
    fun encodeOriginal(image: CapturedImage, text: Map<String, String>, out: OutputStream) =
        PngEncoder.encode(image.argb, image.width, image.height, CompressionProfile.LOSSLESS.pngLevel, FastCapture.encoderPool, FastCapture.encoderThreads, out, text)

    fun fromImage(image: BufferedImage): CapturedImage =
        CapturedImage(image.getRGB(0, 0, image.width, image.height, null, 0, image.width), image.width, image.height)

    fun targetSize(width: Int, height: Int, profile: CompressionProfile, extraScale: Double = 1.0): Pair<Int, Int> {
        var scale = when (profile.resize) {
            ResizeMode.NONE -> 1.0
            ResizeMode.PERCENT -> profile.resizePercent.coerceIn(1, 100) / 100.0
            ResizeMode.MAX_SIZE -> {
                var fit = 1.0
                if (profile.maxWidth > 0) fit = minOf(fit, profile.maxWidth.toDouble() / width)
                if (profile.maxHeight > 0) fit = minOf(fit, profile.maxHeight.toDouble() / height)
                fit
            }
        }
        scale = (scale * extraScale).coerceAtMost(1.0)
        if (scale >= 1.0) return width to height
        return (width * scale).roundToInt().coerceIn(1, width) to (height * scale).roundToInt().coerceIn(1, height)
    }

    fun resize(image: CapturedImage, profile: CompressionProfile, extraScale: Double = 1.0): CapturedImage {
        val (width, height) = targetSize(image.width, image.height, profile, extraScale)
        if (width == image.width && height == image.height) return image
        return CapturedImage(areaAverage(image, width, height), width, height)
    }

    /**
     * Downscales by averaging every source pixel each output pixel covers, weighted by how much of it is covered.
     * Sharp and alias-free for any ratio, and needs only one source row of scratch space.
     */
    private fun areaAverage(image: CapturedImage, width: Int, height: Int): IntArray {
        val sourceWidth = image.width
        val sourceHeight = image.height
        val columns = spans(sourceWidth, width)
        val rows = spans(sourceHeight, height)
        val out = IntArray(width * height)

        val horizontal = Array(3) { FloatArray(width) }
        val accumulated = Array(3) { FloatArray(width) }
        var cachedRow = -1
        for (y in 0 until height) {
            for (channel in accumulated) channel.fill(0f)
            val rowSpan = rows[y]
            for (k in rowSpan.weights.indices) {
                val sourceY = rowSpan.start + k
                if (sourceY != cachedRow) {
                    resampleRow(image.argb, sourceY * sourceWidth, columns, horizontal)
                    cachedRow = sourceY
                }
                val weight = rowSpan.weights[k]
                for (c in 0 until 3) {
                    val source = horizontal[c]
                    val target = accumulated[c]
                    for (x in 0 until width) target[x] += source[x] * weight
                }
            }
            val base = y * width
            for (x in 0 until width) {
                val r = (accumulated[0][x] + 0.5f).toInt().coerceIn(0, 255)
                val g = (accumulated[1][x] + 0.5f).toInt().coerceIn(0, 255)
                val b = (accumulated[2][x] + 0.5f).toInt().coerceIn(0, 255)
                out[base + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    private fun resampleRow(argb: IntArray, offset: Int, columns: Array<Span>, out: Array<FloatArray>) {
        for (x in columns.indices) {
            val span = columns[x]
            var r = 0f
            var g = 0f
            var b = 0f
            for (k in span.weights.indices) {
                val pixel = argb[offset + span.start + k]
                val weight = span.weights[k]
                r += ((pixel shr 16) and 0xFF) * weight
                g += ((pixel shr 8) and 0xFF) * weight
                b += (pixel and 0xFF) * weight
            }
            out[0][x] = r
            out[1][x] = g
            out[2][x] = b
        }
    }

    /** The source pixels output pixel i covers, from [start], with weights summing to 1 */
    private class Span(val start: Int, val weights: FloatArray)

    private fun spans(sourceSize: Int, targetSize: Int): Array<Span> {
        val ratio = sourceSize.toDouble() / targetSize
        return Array(targetSize) { i ->
            val from = i * ratio
            val to = minOf(sourceSize.toDouble(), (i + 1) * ratio)
            val first = from.toInt()
            val last = minOf(sourceSize - 1, kotlin.math.ceil(to).toInt() - 1).coerceAtLeast(first)
            val weights = FloatArray(last - first + 1) { k ->
                val pixel = first + k
                val covered = minOf(to, pixel + 1.0) - maxOf(from, pixel.toDouble())
                (covered / ratio).toFloat().coerceAtLeast(0f)
            }
            Span(first, weights)
        }
    }

    private fun writeJpeg(image: CapturedImage, quality: Int, chroma: ChromaSubsampling, out: OutputStream) {
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        System.arraycopy(image.argb, 0, (buffered.raster.dataBuffer as DataBufferInt).data, 0, image.width * image.height)

        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality.coerceIn(1, 100) / 100f
            }
            val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(buffered), param)
            val tree = metadata.getAsTree(JPEG_METADATA_FORMAT) as IIOMetadataNode
            // Sampling factors live on the frame header's components: luma first, then the two chroma channels.
            val components = tree.getElementsByTagName("componentSpec")
            for (i in 0 until components.length) {
                val factor = if (i == 0 && chroma == ChromaSubsampling.HALF) "2" else "1"
                (components.item(i) as IIOMetadataNode).apply {
                    setAttribute("HsamplingFactor", factor)
                    setAttribute("VsamplingFactor", factor)
                }
            }
            metadata.setFromTree(JPEG_METADATA_FORMAT, tree)

            val stream = MemoryCacheImageOutputStream(out)
            writer.output = stream
            writer.write(null, IIOImage(buffered, null, metadata), param)
            stream.close()
        } finally {
            writer.dispose()
        }
    }
}
