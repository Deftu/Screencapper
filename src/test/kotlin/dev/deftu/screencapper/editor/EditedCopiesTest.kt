package dev.deftu.screencapper.editor

import dev.deftu.screencapper.metadata.PngText
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import dev.deftu.screencapper.screenshot.capture.PngEncoder
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EditedCopiesTest {
    private val dir = Files.createTempDirectory("screencapper-edit").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun png(name: String, width: Int, height: Int, text: Map<String, String> = emptyMap()): Pair<File, CapturedImage> {
        val image = CapturedImage(IntArray(width * height) { 0xFF000000.toInt() or (it * 7) }, width, height)
        val file = File(dir, name).also { it.parentFile.mkdirs() }
        file.outputStream().use { PngEncoder.encode(image.argb, width, height, 1, null, 1, it, text) }
        return file to image
    }

    @Test
    fun `edited copies get an edited suffix once`() {
        assertEquals("shot-edited", EditedCopies.baseName("shot"))
        assertEquals("shot-edited", EditedCopies.baseName("shot-edited"))
        assertEquals("shot-edited", EditedCopies.baseName("shot-edited_3"))
        assertEquals("my_2-edited", EditedCopies.baseName("my_2"))
    }

    @Test
    fun `the copy sits next to the original with the same extension`() {
        val original = File(dir, "World/2026-11-20_12.00.00.jpeg").also { it.parentFile.mkdirs(); it.writeBytes(byteArrayOf(1)) }
        val copy = EditedCopies.reserve(original)
        assertEquals(original.parentFile, copy.parentFile)
        assertEquals("2026-11-20_12.00.00-edited.jpeg", copy.name)
    }

    @Test
    fun `a taken name gets a number instead of being overwritten`() {
        val original = File(dir, "shot.png").also { it.writeBytes(byteArrayOf(1)) }
        File(dir, "shot-edited.png").writeBytes(byteArrayOf(2))
        val copy = EditedCopies.reserve(original)
        assertEquals("shot-edited_2.png", copy.name)
        val again = EditedCopies.reserve(File(dir, "shot-edited.png"))
        assertEquals("shot-edited_3.png", again.name)
    }

    @Test
    fun `saving writes a new file, keeps the original and its png text`() {
        val (original, image) = png("shot.png", 8, 6, mapOf("Title" to "Base", "Comment" to "hello"))
        val before = original.readBytes()
        val saved = EditedCopies.save(original, image, EditDoc(crop = PixelRect(2, 1, 6, 5)))
        assertNotEquals(original, saved)
        assertEquals("shot-edited.png", saved.name)
        assertContentEquals(before, original.readBytes())
        val read = ImageIO.read(saved)
        assertEquals(4, read.width)
        assertEquals(4, read.height)
        assertEquals(image.argb[1 * 8 + 2] and 0xFFFFFF, read.getRGB(0, 0) and 0xFFFFFF)
        assertEquals(mapOf("Title" to "Base", "Comment" to "hello"), PngText.read(saved))
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `saving a jpeg stays a jpeg`() {
        val source = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        val original = File(dir, "shot.jpg").also { ImageIO.write(source, "jpeg", it) }
        val image = CapturedImage(IntArray(64) { 0xFF000000.toInt() }, 8, 8)
        val saved = EditedCopies.save(original, image, EditDoc(crop = PixelRect(0, 0, 4, 8)))
        assertEquals("shot-edited.jpg", saved.name)
        assertEquals(4, ImageIO.read(saved).width)
        val signature = saved.readBytes().take(2).map { it.toInt() and 0xFF }
        assertEquals(listOf(0xFF, 0xD8), signature)
    }
}
