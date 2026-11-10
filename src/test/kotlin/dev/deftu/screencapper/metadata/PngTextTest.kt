package dev.deftu.screencapper.metadata

import dev.deftu.screencapper.screenshot.capture.PngEncoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PngTextTest {
    private fun encode(text: Map<String, String>): ByteArray {
        val width = 37
        val height = 21
        val pixels = IntArray(width * height) { it * 2654435 }
        return ByteArrayOutputStream().also { PngEncoder.encode(pixels, width, height, 6, null, 1, it, text) }.toByteArray()
    }

    @Test
    fun readsBackWhatPngEncoderWrote() {
        val text = linkedMapOf(
            MetadataKeys.SERVER to "Hypixel Network",
            MetadataKeys.BIOME to "minecraft:cherry_grove",
            MetadataKeys.CHAT to "<Stéve> héllo ✓\nsecond line",
        )
        assertEquals(text, PngText.read(ByteArrayInputStream(encode(text))))
    }

    @Test
    fun noTextGivesAnEmptyMap() {
        assertTrue(PngText.read(ByteArrayInputStream(encode(emptyMap()))).isEmpty())
    }

    @Test
    fun notAPngGivesAnEmptyMap() {
        assertTrue(PngText.read(ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 1, 2, 3, 4, 5))).isEmpty())
        assertTrue(PngText.read(ByteArrayInputStream(ByteArray(0))).isEmpty())
    }

    @Test
    fun readsLatin1AndCompressedChunks() {
        val png = encode(emptyMap())
        val withText = ByteArrayOutputStream().apply {
            write(png, 0, 33)
            chunk(this, "tEXt", "Screencapper:World".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + "Café".toByteArray(Charsets.ISO_8859_1))
            val deflated = ByteArrayOutputStream().also { DeflaterOutputStream(it).use { out -> out.write("Plains".toByteArray(Charsets.ISO_8859_1)) } }.toByteArray()
            chunk(this, "zTXt", "Screencapper:Biome".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0) + deflated)
            val compressedUtf8 = ByteArrayOutputStream().also { DeflaterOutputStream(it).use { out -> out.write("Ñether".toByteArray(Charsets.UTF_8)) } }.toByteArray()
            chunk(this, "iTXt", "Screencapper:Dimension".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 1, 0) + "en".toByteArray() + byteArrayOf(0, 0) + compressedUtf8)
            write(png, 33, png.size - 33)
        }.toByteArray()
        assertEquals(
            mapOf(MetadataKeys.WORLD to "Café", MetadataKeys.BIOME to "Plains", MetadataKeys.DIMENSION to "Ñether"),
            PngText.read(ByteArrayInputStream(withText)),
        )
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, body: ByteArray) {
        val data = DataOutputStream(out)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        data.writeInt(body.size)
        data.write(typeBytes)
        data.write(body)
        data.writeInt(CRC32().apply { update(typeBytes); update(body) }.value.toInt())
    }
}
