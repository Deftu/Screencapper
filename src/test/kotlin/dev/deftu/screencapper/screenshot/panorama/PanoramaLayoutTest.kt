package dev.deftu.screencapper.screenshot.panorama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PanoramaLayoutTest {
    @Test
    fun facesFollowVanillasPanoramaGrab() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), PanoramaLayout.FACES.map { it.index })
        assertEquals(listOf(0f, 90f, 180f, -90f, 0f, 0f), PanoramaLayout.FACES.map { it.yawOffset })
        assertEquals(listOf(0f, 0f, 0f, 0f, -90f, 90f), PanoramaLayout.FACES.map { it.pitch })
    }

    @Test
    fun yawWrapsLikeVanilla() {
        assertEquals(10f, PanoramaLayout.FACES[0].yaw(10f))
        assertEquals(100f, PanoramaLayout.FACES[1].yaw(10f))
        assertEquals(10f, PanoramaLayout.FACES[2].yaw(190f))
        assertEquals(-80f, PanoramaLayout.FACES[3].yaw(10f))
        assertEquals(30f, PanoramaLayout.FACES[1].yaw(300f))
    }

    @Test
    fun cubeMapReadsFacesInVanillasSuffixOrder() {
        assertEquals(listOf(1, 3, 5, 4, 0, 2), PanoramaLayout.CUBE_ORDER)
    }

    @Test
    fun faceFilesAreNamedLikeVanilla() {
        assertEquals(listOf("panorama_0.png", "panorama_1.png", "panorama_2.png", "panorama_3.png", "panorama_4.png", "panorama_5.png"), PanoramaLayout.FACES.map { it.fileName })
        assertEquals(3, PanoramaLayout.faceIndex("panorama_3.png"))
        assertEquals(5, PanoramaLayout.faceIndex("PANORAMA_5.PNG"))
        assertNull(PanoramaLayout.faceIndex("panorama_6.png"))
        assertNull(PanoramaLayout.faceIndex("panorama_0.jpg"))
        assertNull(PanoramaLayout.faceIndex("my_panorama_0.png"))
    }

    @Test
    fun folderLivesInThePanoramasFolderWithTheSuffixAndCountsUp() {
        assertEquals("panoramas/2026-11-20_12.00.00_panorama", PanoramaLayout.folderPath("2026-11-20_12.00.00", 1))
        assertEquals("panoramas/2026-11-20_12.00.00_panorama_2", PanoramaLayout.folderPath("2026-11-20_12.00.00", 2))
    }

    @Test
    fun folderKeepsOnlyTheLastSegmentOfTheName() {
        assertEquals("panoramas/2026_panorama", PanoramaLayout.folderPath("World/2026", 1))
        assertEquals("panoramas/2026_panorama", PanoramaLayout.folderPath("panoramas_/2026", 1))
        assertEquals("panoramas/2026_panorama", PanoramaLayout.folderPath("a\\2026", 1))
    }

    @Test
    fun countTokenIsFilledInsteadOfAppendingANumber() {
        assertEquals("panoramas/shot_1_panorama", PanoramaLayout.folderPath("shot_%count%", 1))
        assertEquals("panoramas/shot_3_panorama", PanoramaLayout.folderPath("shot_%count%", 3))
        assertEquals("panoramas/shot_2_panorama", PanoramaLayout.folderPath("%count%/shot_%count%", 2))
    }

    @Test
    fun supersamplingMatchesVanillaAtTheDefaultSize() {
        assertEquals(4, PanoramaLayout.supersample(1024, 16384, canDownscale = true))
        assertEquals(2, PanoramaLayout.supersample(2048, 16384, canDownscale = true))
        assertEquals(1, PanoramaLayout.supersample(4096, 16384, canDownscale = true))
        assertEquals(2, PanoramaLayout.supersample(1024, 2048, canDownscale = true))
        assertEquals(1, PanoramaLayout.supersample(1024, 16384, canDownscale = false))
    }

    @Test
    fun unknownSizesFallBackToTheDefault() {
        assertEquals(listOf(1024, 2048, 4096), PanoramaLayout.SIZES)
        assertEquals(2048, PanoramaLayout.normalizedSize(2048))
        assertEquals(PanoramaLayout.DEFAULT_SIZE, PanoramaLayout.normalizedSize(1500))
        assertEquals(PanoramaLayout.DEFAULT_SIZE, PanoramaLayout.normalizedSize(-1))
    }

    @Test
    fun stackedCubeMatchesTheFileLoaderWhichFlipsEachFaceVertically() {
        val size = 2
        val faces = (0 until 6).map { face -> IntArray(size * size) { pixel -> face * 10 + pixel } }
        val stacked = PanoramaLayout.stackCube(faces, size)
        PanoramaLayout.CUBE_ORDER.forEachIndexed { slot, face ->
            for (y in 0 until size) for (x in 0 until size) {
                assertEquals(faces[face][(size - 1 - y) * size + x], stacked[(slot * size + y) * size + x], "slot $slot at $x,$y")
            }
        }
    }
}
