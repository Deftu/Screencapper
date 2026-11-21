package dev.deftu.screencapper.screenshot.capture

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FastCaptureNamingTest {
    @Test
    fun capturesInTheSameSecondGetDistinctFilesBeforeEitherIsWritten() {
        val directory = Files.createTempDirectory("screencapper-naming").toFile()
        try {
            val first = FastCapture.reserve(directory, "2026-11-20_12.00.00", "png")
            val second = FastCapture.reserve(directory, "2026-11-20_12.00.00", "png")
            assertNotEquals(first, second)
            assertEquals("2026-11-20_12.00.00_2", second.nameWithoutExtension)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun reservationCreatesSubfoldersAndStaysUniqueInThem() {
        val directory = Files.createTempDirectory("screencapper-naming").toFile()
        try {
            val first = FastCapture.reserve(directory, "World/2026-11-20_12.00.00", "png")
            val second = FastCapture.reserve(directory, "World/2026-11-20_12.00.00", "png")
            assertEquals(File(directory, "World"), first.parentFile)
            assertTrue(first.parentFile.isDirectory)
            assertEquals("2026-11-20_12.00.00_2", second.nameWithoutExtension)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun countTokenTakesTheFirstFreeNumber() {
        val directory = Files.createTempDirectory("screencapper-naming").toFile()
        try {
            File(directory, "shot_1.png").writeBytes(byteArrayOf(1))
            val first = FastCapture.reserve(directory, "shot_%count%", "png")
            val second = FastCapture.reserve(directory, "shot_%count%", "png")
            assertEquals("shot_2.png", first.name)
            assertEquals("shot_3.png", second.name)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aNameIsUniqueAcrossImageFormatsSoOriginalsNeverCollide() {
        val directory = Files.createTempDirectory("screencapper-naming").toFile()
        try {
            val png = FastCapture.reserve(directory, "shot", "png")
            val jpg = FastCapture.reserve(directory, "shot", "jpg")
            assertEquals("shot", png.nameWithoutExtension)
            assertEquals("shot_2", jpg.nameWithoutExtension)
            File(directory, "taken.png").writeBytes(byteArrayOf(1))
            assertEquals("taken_2", FastCapture.reserve(directory, "taken", "jpg").nameWithoutExtension)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aClaimedRenameTargetIsSkippedByNewCaptures() {
        val directory = Files.createTempDirectory("screencapper-naming").toFile()
        try {
            val target = File(directory, "renamed.png")
            assertTrue(FastCapture.claim(target))
            assertEquals("renamed_2", FastCapture.reserve(directory, "renamed", "png").nameWithoutExtension)
            FastCapture.release(target)
            assertFalse(FastCapture.claim(FastCapture.reserve(directory, "free", "png")))
        } finally {
            directory.deleteRecursively()
        }
    }
}
