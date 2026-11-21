package dev.deftu.screencapper.upload.integration.nextcloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NextcloudPathsTest {
    @Test
    fun `server address must be an http or https link with a host`() {
        assertNull(NextcloudPaths.serverProblem("https://cloud.example.com"))
        assertNull(NextcloudPaths.serverProblem("http://192.168.1.5:8080/nextcloud"))
        assertNotNull(NextcloudPaths.serverProblem("cloud.example.com"))
        assertNotNull(NextcloudPaths.serverProblem("ftp://cloud.example.com"))
        assertNotNull(NextcloudPaths.serverProblem("https://"))
        assertNotNull(NextcloudPaths.serverProblem("not a link"))
    }

    @Test
    fun `server address drops trailing slashes and Nextcloud routes but keeps a sub folder`() {
        assertEquals("https://cloud.example.com", NextcloudPaths.normalizeServer(" https://cloud.example.com/ "))
        assertEquals("https://cloud.example.com", NextcloudPaths.normalizeServer("https://cloud.example.com/index.php/apps/files/?dir=/"))
        assertEquals("https://example.com/nextcloud", NextcloudPaths.normalizeServer("https://example.com/nextcloud/apps/files"))
        assertEquals("https://example.com/nextcloud", NextcloudPaths.normalizeServer("https://example.com/nextcloud/remote.php/dav/files/sam/"))
        assertEquals("https://cloud.example.com:8443", NextcloudPaths.normalizeServer("HTTPS://cloud.example.com:8443#top"))
    }

    @Test
    fun `folder splits into trimmed segments and blank means the top folder`() {
        assertEquals(listOf("Screenshots"), NextcloudPaths.folderSegments("Screenshots"))
        assertEquals(listOf("Games", "Minecraft shots"), NextcloudPaths.folderSegments("/Games/ Minecraft shots /"))
        assertEquals(listOf("A", "B"), NextcloudPaths.folderSegments("A\\B"))
        assertEquals(emptyList(), NextcloudPaths.folderSegments("  /  "))
    }

    @Test
    fun `folder can't climb out with dot segments`() {
        assertNull(NextcloudPaths.folderProblem("Screenshots/2026"))
        assertNull(NextcloudPaths.folderProblem(""))
        assertNotNull(NextcloudPaths.folderProblem("Screenshots/../Documents"))
        assertNotNull(NextcloudPaths.folderProblem("./Screenshots"))
    }

    @Test
    fun `segments are percent encoded as UTF-8 keeping only unreserved characters`() {
        assertEquals("2026-11-20_12.00.00.png", NextcloudPaths.encodeSegment("2026-11-20_12.00.00.png"))
        assertEquals("my%20shot%20%231%3F.png", NextcloudPaths.encodeSegment("my shot #1?.png"))
        assertEquals("sam%40example.com", NextcloudPaths.encodeSegment("sam@example.com"))
        assertEquals("Gr%C3%BC%C3%9Fe%2Bmehr~", NextcloudPaths.encodeSegment("Grüße+mehr~"))
        assertEquals("100%25%2F", NextcloudPaths.encodeSegment("100%/"))
    }

    @Test
    fun `file url encodes the user and every segment`() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/sam%40example.com/Games/Minecraft%20shots/a%20b.png",
            NextcloudPaths.davUrl("https://cloud.example.com", "sam@example.com", listOf("Games", "Minecraft shots", "a b.png")),
        )
        assertEquals("https://cloud.example.com/remote.php/dav/files/sam/shot.png", NextcloudPaths.davUrl("https://cloud.example.com", "sam", listOf("shot.png")))
    }

    @Test
    fun `share path is the plain path from the user's top folder`() {
        assertEquals("/Games/Minecraft shots/a b.png", NextcloudPaths.sharePath(listOf("Games", "Minecraft shots", "a b.png")))
        assertEquals("/shot.png", NextcloudPaths.sharePath(listOf("shot.png")))
    }

    @Test
    fun `retries pick numbered names then a random suffix`() {
        val random = { "a1b2c3d4" }
        assertEquals("shot.png", NextcloudPaths.candidateName("shot.png", 0, random))
        assertEquals("shot (2).png", NextcloudPaths.candidateName("shot.png", 1, random))
        assertEquals("shot (3).png", NextcloudPaths.candidateName("shot.png", 2, random))
        assertEquals("shot-a1b2c3d4.png", NextcloudPaths.candidateName("shot.png", 3, random))
        assertEquals("archive.tar (2).gz", NextcloudPaths.candidateName("archive.tar.gz", 1, random))
        assertEquals("noext (2)", NextcloudPaths.candidateName("noext", 1, random))
        assertEquals(".hidden (2)", NextcloudPaths.candidateName(".hidden", 1, random))
    }
}
