package dev.deftu.screencapper.screenshot.panorama

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoPanoramasTest {
    private val time = LocalDateTime.of(2026, 11, 20, 14, 3, 22)

    @Test
    fun folderLivesInTheAutoFolderNamedByTimeThenWorld() {
        assertEquals("panoramas/auto/2026-11-20_14.03.22_My World", AutoPanoramas.folderPath(time, "My World", 1))
        assertEquals("panoramas/auto/2026-11-20_14.03.22_My World_2", AutoPanoramas.folderPath(time, "My World", 2))
        assertEquals("panoramas/auto/2026-11-20_14.03.22", AutoPanoramas.folderPath(time, null, 1))
        assertEquals("panoramas/auto/2026-11-20_14.03.22_3", AutoPanoramas.folderPath(time, "  ", 3))
    }

    @Test
    fun worldNamesCannotEscapeTheFolder() {
        assertEquals("panoramas/auto/2026-11-20_14.03.22_evil", AutoPanoramas.folderPath(time, "../../evil", 1))
        assertEquals("panoramas/auto/2026-11-20_14.03.22_ab", AutoPanoramas.folderPath(time, "a:b", 1))
    }

    @Test
    fun onlyTimestampedNamesCountAsAuto() {
        assertTrue(AutoPanoramas.isAuto("2026-11-20_14.03.22"))
        assertTrue(AutoPanoramas.isAuto("2026-11-20_14.03.22_My World"))
        assertTrue(AutoPanoramas.isAuto("2026-11-20_14.03.22_My World_2"))
        assertFalse(AutoPanoramas.isAuto("my favourite"))
        assertFalse(AutoPanoramas.isAuto("2026-11-20"))
        assertFalse(AutoPanoramas.isAuto("x2026-11-20_14.03.22"))
        assertFalse(AutoPanoramas.isAuto("2026-11-20_14.03.22_"))
    }

    @Test
    fun nameOfOnlyMatchesFoldersDirectlyInTheAutoFolder() {
        assertEquals("2026-11-20_14.03.22_World", AutoPanoramas.nameOf("panoramas/auto/2026-11-20_14.03.22_World"))
        assertEquals("2026-11-20_14.03.22", AutoPanoramas.nameOf("PANORAMAS/Auto/2026-11-20_14.03.22"))
        assertNull(AutoPanoramas.nameOf("panoramas/2026-11-20_14.03.22_panorama"))
        assertNull(AutoPanoramas.nameOf("panoramas/auto/nested/2026-11-20_14.03.22"))
        assertNull(AutoPanoramas.nameOf("panoramas/auto"))
        assertNull(AutoPanoramas.nameOf(""))
    }

    @Test
    fun pruneKeepsTheNewestAndDeletesOlderOnes() {
        val names = listOf(
            "2026-11-18_10.00.00_A",
            "2026-11-20_09.00.00_B",
            "2026-11-19_12.00.00",
            "2026-11-20_09.00.00_B_2",
            "2026-11-17_08.00.00_C",
        )
        assertEquals(listOf("2026-11-18_10.00.00_A", "2026-11-17_08.00.00_C"), AutoPanoramas.toPrune(names, 3, null))
        assertEquals(emptyList(), AutoPanoramas.toPrune(names, 5, null))
        assertEquals(emptyList(), AutoPanoramas.toPrune(emptyList(), 5, null))
    }

    @Test
    fun pruneNeverTouchesFoldersItDidNotName() {
        val names = listOf("2026-11-18_10.00.00", "my build", "2026-11-17_08.00.00", "notes")
        assertEquals(listOf("2026-11-17_08.00.00"), AutoPanoramas.toPrune(names, 1, null))
    }

    @Test
    fun pruneSparesTheTitleBackground() {
        val names = listOf("2026-11-20_10.00.00", "2026-11-19_10.00.00", "2026-11-18_10.00.00")
        assertEquals(listOf("2026-11-18_10.00.00"), AutoPanoramas.toPrune(names, 1, "2026-11-19_10.00.00"))
    }

    @Test
    fun faceSizeFallsBackToTheDefaultAndCapsTiledRendering() {
        assertEquals(1024, AutoPanoramas.faceSize(1024, false))
        assertEquals(2048, AutoPanoramas.faceSize(2048, false))
        assertEquals(1024, AutoPanoramas.faceSize(2048, true))
        assertEquals(1024, AutoPanoramas.faceSize(4096, false))
        assertEquals(1024, AutoPanoramas.faceSize(0, false))
    }
}
