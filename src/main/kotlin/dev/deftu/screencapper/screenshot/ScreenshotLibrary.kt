package dev.deftu.screencapper.screenshot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.metadata.MetadataIndex
import dev.deftu.screencapper.screenshot.naming.ScreenshotPaths
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object ScreenshotLibrary {
    private const val JUST_SAVED_WINDOW_MS = 15_000L

    val directory: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "screenshots")

    var revision by mutableIntStateOf(0)
        private set

    fun list(): List<Screenshot> =
        ScreenshotPaths.list(directory)
            .map { (id, file) -> Screenshot(file, id) }
            .sortedByDescending { it.modified }

    fun idOf(file: File): String = ScreenshotPaths.idOf(directory, file)

    fun find(id: String): Screenshot? {
        val root = directory
        val file = ScreenshotPaths.resolve(root, id) ?: return null
        return Screenshot(file, ScreenshotPaths.idOf(root, file))
    }

    fun findJustSaved(message: String): Screenshot? {
        val cutoff = System.currentTimeMillis() - JUST_SAVED_WINDOW_MS
        val found = ScreenshotPaths.list(directory) { file -> message.contains(file.name) }
            .map { (id, file) -> Screenshot(file, id) }
            .filter { it.modified >= cutoff && it.size > 0 }
            .maxByOrNull { it.modified }
        if (found != null) MetadataIndex.record(found.id, found.file)
        return found
    }

    fun justSaved(file: File): Screenshot? {
        if (!file.isFile || file.length() <= 0) return null
        val found = Screenshot(file, idOf(file))
        MetadataIndex.record(found.id, found.file)
        return found
    }

    fun changed() {
        MainThread.run { revision++ }
    }
}
