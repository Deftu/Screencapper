package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.screenshot.capture.compression.PaletteQuantizer
import java.awt.image.IndexColorModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PngEncoderTest {
    private val pool = Executors.newFixedThreadPool(4)

    @AfterTest
    fun shutdown() {
        pool.shutdownNow()
    }

    private fun sample(width: Int, height: Int, seed: Int): IntArray {
        val random = Random(seed)
        return IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            // Gradients with noise, so both the filter and the deflate strips see varied data.
            val noise = random.nextInt(16)
            (0xFF shl 24) or (((x * 7 + noise) and 0xFF) shl 16) or (((y * 3) and 0xFF) shl 8) or ((x xor y) and 0xFF)
        }
    }

    /** Entries in the PLTE chunk, read from the raw chunks since ImageIO pads palettes to a power of two */
    private fun paletteEntries(png: ByteArray): Int {
        val data = java.io.DataInputStream(ByteArrayInputStream(png, 8, png.size - 8))
        while (true) {
            val length = data.readInt()
            val type = String(ByteArray(4).also { data.readFully(it) }, Charsets.US_ASCII)
            if (type == "PLTE") return length / 3
            if (type == "IEND") return 0
            data.skipBytes(length + 4)
        }
    }

    @Test
    fun `multi-strip RGB round trip is pixel exact`() {
        val sizes = listOf(1 to 1, 17 to 1, 640 to 1, 33 to 129, 100 to 255, 257 to 513, 64 to 64)
        for ((width, height) in sizes) {
            val argb = sample(width, height, width * 31 + height)
            val out = ByteArrayOutputStream()
            PngEncoder.encode(argb, width, height, 6, pool, 4, out)
            val image = assertNotNull(ImageIO.read(ByteArrayInputStream(out.toByteArray())), "${width}x$height didn't decode")
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            val decoded = image.getRGB(0, 0, width, height, null, 0, width)
            for (i in argb.indices) assertEquals(argb[i] or (0xFF shl 24), decoded[i], "${width}x$height pixel $i")
        }
    }

    @Test
    fun `iTXt chunks read back through ImageIO`() {
        val text = linkedMapOf("Screencapper:Biome" to "minecraft:cherry_grove", "Comment" to "Café ☃ chat line")
        val out = ByteArrayOutputStream()
        PngEncoder.encode(sample(80, 200, 1), 80, 200, 4, pool, 4, out, text)

        val reader = ImageIO.getImageReadersByFormatName("png").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(out.toByteArray()))
        val tree = reader.getImageMetadata(0).getAsTree("javax_imageio_png_1.0") as IIOMetadataNode
        val entries = tree.getElementsByTagName("iTXtEntry")
        val read = (0 until entries.length).associate {
            val node = entries.item(it) as IIOMetadataNode
            node.getAttribute("keyword") to node.getAttribute("text")
        }
        reader.dispose()
        assertEquals(text, read)
    }

    @Test
    fun `indexed PNG round trip keeps the palette within the limit`() {
        for (colors in listOf(256, 128, 64)) {
            for ((width, height) in listOf(1 to 1, 31 to 1, 120 to 131, 300 to 257)) {
                val argb = sample(width, height, colors + width)
                val quantized = PaletteQuantizer.quantize(argb, width, height, colors, dither = colors != 128)
                assertTrue(quantized.palette.size in 1..colors, "palette of ${quantized.palette.size} for $colors")

                val out = ByteArrayOutputStream()
                PngEncoder.encodeIndexed(quantized.indices, quantized.palette, width, height, 6, pool, 4, out, mapOf("Comment" to "indexed"))
                val image = assertNotNull(ImageIO.read(ByteArrayInputStream(out.toByteArray())), "${width}x$height/$colors didn't decode")
                assertEquals(width, image.width)
                assertEquals(height, image.height)
                assertTrue(image.colorModel is IndexColorModel, "not an indexed image")
                // ImageIO pads the colour model to 256 entries, so the PLTE chunk itself is checked.
                assertTrue(paletteEntries(out.toByteArray()) in 1..colors, "PLTE over $colors colours")

                val decoded = image.getRGB(0, 0, width, height, null, 0, width)
                for (i in decoded.indices) {
                    val expected = quantized.palette[quantized.indices[i].toInt() and 0xFF] or (0xFF shl 24)
                    assertEquals(expected, decoded[i], "${width}x$height/$colors pixel $i")
                }
                assertTrue(decoded.toSet().size <= colors)
            }
        }
    }
}
