package dev.deftu.screencapper.screenshot.naming

import dev.deftu.screencapper.screenshot.capture.FastCapture
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingSavesTest {
    @Test
    fun settlingReturnsTheFileNamedInTheMessage() {
        val pending = PendingSaves()
        val first = File("screenshots/World/shot_1.png")
        val second = File("screenshots/World/shot_2.png")
        pending.add(first)
        pending.add(second)
        assertEquals(second, pending.settle("Saved screenshot as shot_2.png"))
        assertEquals(first, pending.settle("Saved screenshot as shot_1.png"))
        assertNull(pending.settle("Saved screenshot as shot_1.png"))
    }

    @Test
    fun settlingPrefersTheLongestMatchingName() {
        val pending = PendingSaves()
        val short = File("screenshots/1.png")
        val long = File("screenshots/11.png")
        pending.add(short)
        pending.add(long)
        assertEquals(long, pending.settle("Saved screenshot as 11.png"))
        assertEquals(short, pending.settle("Saved screenshot as 1.png"))
    }

    @Test
    fun aFailureMessageSettlesTheOldestSave() {
        val pending = PendingSaves()
        val first = File("screenshots/a.png")
        val second = File("screenshots/b.png")
        pending.add(first)
        pending.add(second)
        assertEquals(first, pending.settle("Couldn't save screenshot: disk full"))
        assertEquals(second, pending.settle("Couldn't save screenshot: disk full"))
        assertNull(pending.settle("Couldn't save screenshot: disk full"))
    }

    @Test
    fun aSettledReservationFreesItsNameForTheNextCapture() {
        val directory = Files.createTempDirectory("screencapper-pending").toFile()
        try {
            val pending = PendingSaves()
            val first = FastCapture.reserve(directory, "World/shot_%count%", "png")
            pending.add(first)
            pending.settle("Couldn't save screenshot")?.let(FastCapture::release)
            assertEquals(first, FastCapture.reserve(directory, "World/shot_%count%", "png"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aMessageForAnotherFileCanLeaveTheReservationsAlone() {
        val pending = PendingSaves()
        val first = File("screenshots/a.png")
        pending.add(first)
        assertNull(pending.settle("Saved screenshot as forced.png", oldestIfUnnamed = false))
        assertEquals(first, pending.settle("Saved screenshot as a.png"))
    }
}
