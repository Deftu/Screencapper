package dev.deftu.screencapper.screenshot.naming

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class ScreenshotPathsTest {
    private val sandbox = Files.createTempDirectory("screencapper-paths").toFile()
    private val root = File(sandbox, "screenshots").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        sandbox.deleteRecursively()
    }

    private fun touch(path: String): File = File(root, path).apply {
        parentFile.mkdirs()
        writeBytes(byteArrayOf(1))
    }

    @Test
    fun listsSubfoldersButSkipsOriginalsHiddenAndTempFiles() {
        touch("a.png")
        touch("World/b.jpg")
        touch("World/Nether/c.jpeg")
        touch("original/a.png")
        touch("World/original/b.png")
        touch(".a.png.tmp")
        touch("World/.b.jpg.tmp")
        touch(".hidden/d.png")
        touch(".e.png")
        touch("notes.txt")

        assertEquals(listOf("World/Nether/c.jpeg", "World/b.jpg", "a.png"), ScreenshotPaths.list(root).map { it.first }.sorted())
    }

    @Test
    fun listingSkipsTheTopLevelPanoramasFolder() {
        touch("a.png")
        touch("panoramas/a_panorama/panorama_0.png")
        touch("panoramas/b.png")
        touch("World/panoramas/c.png")

        assertEquals(listOf("World/panoramas/c.png", "a.png"), ScreenshotPaths.list(root).map { it.first }.sorted())
    }

    @Test
    fun resolveRejectsFilesInThePanoramasFolder() {
        touch("panoramas/a_panorama/panorama_0.png")
        touch("panoramas/b.png")
        touch("World/panoramas/c.png")
        assertNull(ScreenshotPaths.resolve(root, "panoramas/a_panorama/panorama_0.png"))
        assertNull(ScreenshotPaths.resolve(root, "panoramas/b.png"))
        assertNull(ScreenshotPaths.resolve(root, "Panoramas/b.png"))
        assertNotNull(ScreenshotPaths.resolve(root, "World/panoramas/c.png"))
    }

    @Test
    fun renameCannotMoveIntoThePanoramasFolder() {
        val current = touch("a.png")
        assertEquals("panoramas_/a.png", assertIs<RenameCheck.Ok>(ScreenshotPaths.renameTarget(root, current, "panoramas/a")).id)
    }

    @Test
    fun idIsTheRelativePathWithForwardSlashes() {
        assertEquals("a.png", ScreenshotPaths.idOf(root, touch("a.png")))
        assertEquals("World/b.png", ScreenshotPaths.idOf(root, touch("World/b.png")))
        assertEquals("x.png", ScreenshotPaths.idOf(root, File(sandbox, "x.png")))
    }

    @Test
    fun resolveFindsRootAndSubfolderFiles() {
        touch("a.png")
        touch("World/b.png")
        assertNotNull(ScreenshotPaths.resolve(root, "a.png"))
        assertNotNull(ScreenshotPaths.resolve(root, "World/b.png"))
        assertNotNull(ScreenshotPaths.resolve(root, "World\\b.png"))
    }

    @Test
    fun resolveRejectsPathsOutsideTheFolder() {
        File(sandbox, "secret.png").writeBytes(byteArrayOf(1))
        touch("a.png")
        assertNull(ScreenshotPaths.resolve(root, "../secret.png"))
        assertNull(ScreenshotPaths.resolve(root, "World/../../secret.png"))
        assertNull(ScreenshotPaths.resolve(root, File(sandbox, "secret.png").absolutePath))
        assertNull(ScreenshotPaths.resolve(root, ""))
    }

    @Test
    fun resolveRejectsFilesTheListingHides() {
        touch("original/a.png")
        touch(".a.png")
        touch("notes.txt")
        assertNull(ScreenshotPaths.resolve(root, "original/a.png"))
        assertNull(ScreenshotPaths.resolve(root, ".a.png"))
        assertNull(ScreenshotPaths.resolve(root, "notes.txt"))
        assertNull(ScreenshotPaths.resolve(root, "missing.png"))
    }

    @Test
    fun renameKeepsTheExtension() {
        val current = touch("a.png")
        assertEquals("b.png", assertIs<RenameCheck.Ok>(ScreenshotPaths.renameTarget(root, current, "b")).id)
        assertEquals("b.png", assertIs<RenameCheck.Ok>(ScreenshotPaths.renameTarget(root, current, "b.PNG")).id)
        assertEquals("b.jpg.png", assertIs<RenameCheck.Ok>(ScreenshotPaths.renameTarget(root, current, "b.jpg")).id)
    }

    @Test
    fun renameCanMoveIntoASubfolder() {
        val current = touch("a.png")
        val check = assertIs<RenameCheck.Ok>(ScreenshotPaths.renameTarget(root, current, "World/a"))
        assertEquals("World/a.png", check.id)
        assertEquals(File(root, "World/a.png").canonicalFile, check.file.canonicalFile)
    }

    @Test
    fun renameSanitizesAndCannotEscape() {
        val current = touch("a.png")
        assertEquals("etc/b.png", assertIs<RenameCheck.Ok>(ScreenshotPaths.renameTarget(root, current, "../../etc/b")).id)
        assertEquals("CON_.png", assertIs<RenameCheck.Ok>(ScreenshotPaths.renameTarget(root, current, "CON")).id)
    }

    @Test
    fun renameRejectsEmptyUnchangedAndTakenNames() {
        val current = touch("World/a.png")
        touch("b.png")
        assertIs<RenameCheck.Invalid>(ScreenshotPaths.renameTarget(root, current, "  "))
        assertIs<RenameCheck.Invalid>(ScreenshotPaths.renameTarget(root, current, "../.."))
        assertIs<RenameCheck.Invalid>(ScreenshotPaths.renameTarget(root, current, "World/a"))
        assertIs<RenameCheck.Invalid>(ScreenshotPaths.renameTarget(root, current, "b"))
    }

    @Test
    fun renameAllowsChangingOnlyTheCase() {
        val current = touch("shot.png")
        assertEquals("Shot.png", assertIs<RenameCheck.Ok>(ScreenshotPaths.renameTarget(root, current, "Shot")).id)
    }

    @Test
    fun originalCopyLivesNextToTheScreenshot() {
        assertEquals(File(root, "World/original/a.png"), ScreenshotPaths.originalOf(File(root, "World/a.jpg")))
    }

    @Test
    fun listingSkipsFoldersThatLinkOutsideTheScreenshotsFolder() {
        val outside = File(sandbox, "elsewhere").apply { mkdirs() }
        File(outside, "private.png").writeBytes(byteArrayOf(1))
        touch("kept.png")
        val link = File(root, "linked").toPath()
        val linked = runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess
        assumeTrue(linked, "this system can't create symbolic links")
        assertEquals(listOf("kept.png"), ScreenshotPaths.list(root).map { it.first })
    }

    @Test
    fun anOriginalBelongsToTheCompressedShotWhenALosslessOneSharesItsName() {
        val png = touch("shot.png")
        val jpg = touch("shot.jpg")
        val alone = touch("solo.png")
        assertFalse(ScreenshotPaths.ownsOriginal(png))
        assertTrue(ScreenshotPaths.ownsOriginal(jpg))
        assertTrue(ScreenshotPaths.ownsOriginal(alone))
    }
}
