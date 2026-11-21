package dev.deftu.screencapper.gui

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThumbnailsRegionTest {
    private val dir = Files.createTempDirectory("screencapper-region").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun gradient(width: Int, height: Int): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, (x * 4 shl 16) or (y * 4 shl 8))
        return File(dir, "gradient.png").also { ImageIO.write(image, "png", it) }
    }

    private fun BufferedImage.at(x: Int, y: Int): Int = getRGB(x, y) and 0xFFFFFF

    @Test
    fun `decodes only the requested region at full resolution`() {
        val region = assertNotNull(Thumbnails.decodeRegion(gradient(64, 32), 8, 4, 16, 8, 1))
        assertEquals(16, region.width)
        assertEquals(8, region.height)
        assertEquals((8 * 4 shl 16) or (4 * 4 shl 8), region.at(0, 0))
        assertEquals((23 * 4 shl 16) or (11 * 4 shl 8), region.at(15, 7))
    }

    @Test
    fun `subsampling keeps every nth source pixel`() {
        val region = assertNotNull(Thumbnails.decodeRegion(gradient(64, 32), 8, 4, 16, 8, 2))
        assertEquals(8, region.width)
        assertEquals(4, region.height)
        assertEquals((10 * 4 shl 16) or (6 * 4 shl 8), region.at(1, 1))
    }

    @Test
    fun `a region past the edge is clipped to the image`() {
        val region = assertNotNull(Thumbnails.decodeRegion(gradient(64, 32), 56, 24, 16, 16, 1))
        assertEquals(8, region.width)
        assertEquals(8, region.height)
    }

    @Test
    fun `a missing file decodes to nothing`() {
        assertNull(Thumbnails.decodeRegion(File(dir, "missing.png"), 0, 0, 4, 4, 1))
    }
}
