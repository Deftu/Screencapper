package dev.deftu.screencapper.screenshot.panorama

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PanoramaFolderTest {
    private val sandbox = Files.createTempDirectory("screencapper-panorama").toFile()
    private val root = File(sandbox, "screenshots").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        sandbox.deleteRecursively()
    }

    private fun png(file: File, width: Int, height: Int) {
        file.parentFile.mkdirs()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file)
    }

    private fun panorama(path: String, side: Int = 8): File {
        val dir = File(root, path)
        for (index in 0 until 6) png(File(dir, "panorama_$index.png"), side, side)
        return dir
    }

    @Test
    fun aFolderWithSixMatchingSquareFacesIsAPanorama() {
        val dir = panorama("panoramas/a_panorama")
        assertEquals((0 until 6).map { File(dir, "panorama_$it.png") }, PanoramaFolder.faces(dir))
    }

    @Test
    fun aMissingFaceIsNotAPanorama() {
        val dir = panorama("panoramas/a_panorama")
        File(dir, "panorama_5.png").delete()
        assertNull(PanoramaFolder.faces(dir))
    }

    @Test
    fun mismatchedOrNonSquareFacesAreRejected() {
        val mixed = panorama("mixed")
        png(File(mixed, "panorama_2.png"), 16, 16)
        assertNull(PanoramaFolder.faces(mixed))

        val wide = File(root, "wide")
        for (index in 0 until 6) png(File(wide, "panorama_$index.png"), 16, 8)
        assertNull(PanoramaFolder.faces(wide))

        val broken = panorama("broken")
        File(broken, "panorama_1.png").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(PanoramaFolder.faces(broken))
    }

    @Test
    fun foldersResolveInsideThePanoramasFolder() {
        val dir = panorama("panoramas/a_panorama")
        assertEquals(dir.canonicalFile, PanoramaFolder.resolve(root, "panoramas/a_panorama"))
        assertEquals(dir.canonicalFile, PanoramaFolder.resolve(root, "panoramas\\a_panorama"))
        assertNull(PanoramaFolder.resolve(root, "panoramas"))
        assertNull(PanoramaFolder.resolve(root, "panoramas/missing"))
        assertNull(PanoramaFolder.resolve(root, ""))
    }

    @Test
    fun oldFoldersResolveOnlyWhenAllowed() {
        val dir = panorama("World/a_panorama")
        assertNull(PanoramaFolder.resolve(root, "World/a_panorama"))
        assertEquals(dir.canonicalFile, PanoramaFolder.resolve(root, "World/a_panorama", allowLegacy = true))
        panorama("World/original")
        assertNull(PanoramaFolder.resolve(root, "World/original", allowLegacy = true))
    }

    @Test
    fun foldersCannotEscape() {
        panorama("panoramas/a_panorama")
        panorama("World/a_panorama")
        val outside = File(sandbox, "outside").apply { mkdirs() }
        assertNull(PanoramaFolder.resolve(root, "panoramas/../World/a_panorama"))
        assertNull(PanoramaFolder.resolve(root, "../screenshots/panoramas/a_panorama"))
        assertNull(PanoramaFolder.resolve(root, "panoramas/./a_panorama"))
        assertNull(PanoramaFolder.resolve(root, "../outside", allowLegacy = true))
        assertNull(PanoramaFolder.resolve(root, outside.absolutePath, allowLegacy = true))
        assertNull(PanoramaFolder.resolve(root, "panoramas/a_panorama\u0000"))
    }

    @Test
    fun hiddenFoldersInsideThePanoramasFolderAreRejected() {
        panorama("panoramas/.hidden")
        assertNull(PanoramaFolder.resolve(root, "panoramas/.hidden"))
    }

    @Test
    fun validatedNeedsSixFaces() {
        val dir = panorama("panoramas/a_panorama")
        assertEquals(dir.canonicalFile, PanoramaFolder.validated(root, "panoramas/a_panorama"))
        File(dir, "panorama_3.png").delete()
        assertNull(PanoramaFolder.validated(root, "panoramas/a_panorama"))
        panorama("World/old_panorama")
        assertNull(PanoramaFolder.validated(root, "World/old_panorama"))
        assertNotNull(PanoramaFolder.validated(root, "World/old_panorama", allowLegacy = true))
    }

    @Test
    fun idsAreRelativeToTheScreenshotsFolder() {
        val dir = panorama("panoramas/a_panorama")
        assertEquals("panoramas/a_panorama", PanoramaFolder.idOf(root, dir))
        assertNull(PanoramaFolder.idOf(root, root))
        assertNull(PanoramaFolder.idOf(root, sandbox))
    }
}
