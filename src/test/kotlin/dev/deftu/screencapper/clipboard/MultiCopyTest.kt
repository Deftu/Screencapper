package dev.deftu.screencapper.clipboard

import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiCopyTest {
    private val dir = Files.createTempDirectory("screencapper-multicopy").toFile()
    private val first = File(dir, "a.png").apply { writeBytes(byteArrayOf(1)) }
    private val second = File(dir, "b.png").apply { writeBytes(byteArrayOf(2)) }
    private val missing = File(dir, "gone.png")

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `copyable keeps existing files in order and drops missing and duplicate ones`() {
        assertEquals(listOf(first.absoluteFile, second.absoluteFile), MultiCopy.copyable(listOf(first, missing, second, first)))
    }

    @Test
    fun `copyable drops directories`() {
        assertEquals(listOf(first.absoluteFile), MultiCopy.copyable(listOf(dir, first)))
    }

    @Test
    fun `paths text puts one absolute path per line`() {
        assertEquals(first.absolutePath + System.lineSeparator() + second.absolutePath, MultiCopy.pathsText(listOf(first, second)))
    }

    @Test
    fun `nothing is copied when every file is missing`() {
        val result = MultiCopy.copy(listOf(missing), { error("no files expected") }, { error("no text expected") })
        assertEquals(MultiCopyResult.NothingToCopy, result)
    }

    @Test
    fun `a single remaining file is handed back for an image copy`() {
        val result = MultiCopy.copy(listOf(first, missing), { error("no files expected") }, { error("no text expected") })
        assertEquals(MultiCopyResult.Single(first.absoluteFile), result)
    }

    @Test
    fun `several files go on the clipboard as a file list`() {
        var copied: List<File>? = null
        val result = MultiCopy.copy(listOf(first, second), { copied = it; true }, { error("no text expected") })
        assertEquals(MultiCopyResult.Files(listOf(first.absoluteFile, second.absoluteFile)), result)
        assertEquals(listOf(first.absoluteFile, second.absoluteFile), copied)
    }

    @Test
    fun `paths are copied as text when the clipboard cannot hold files`() {
        var text: String? = null
        val result = MultiCopy.copy(listOf(first, second), { false }, { text = it; true })
        assertEquals(MultiCopyResult.Paths(listOf(first.absoluteFile, second.absoluteFile)), result)
        assertEquals(MultiCopy.pathsText(listOf(first, second)), text)
    }

    @Test
    fun `a throwing file copy falls back to paths`() {
        val result = MultiCopy.copy(listOf(first, second), { throw IllegalStateException("headless") }, { true })
        assertEquals(MultiCopyResult.Paths(listOf(first.absoluteFile, second.absoluteFile)), result)
    }

    @Test
    fun `copy fails when neither files nor text reach the clipboard`() {
        val result = MultiCopy.copy(listOf(first, second), { false }, { false })
        assertEquals(MultiCopyResult.Failed(listOf(first.absoluteFile, second.absoluteFile)), result)
    }

    @Test
    fun `file list selection offers only the file list flavor`() {
        val selection = FileListSelection(listOf(first, second))
        assertTrue(selection.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        assertFalse(selection.isDataFlavorSupported(DataFlavor.stringFlavor))
        assertEquals(listOf(DataFlavor.javaFileListFlavor), selection.transferDataFlavors.toList())
        assertEquals(listOf(first, second), selection.getTransferData(DataFlavor.javaFileListFlavor))
    }
}
