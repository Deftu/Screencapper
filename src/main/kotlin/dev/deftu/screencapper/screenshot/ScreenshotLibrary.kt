package dev.deftu.screencapper.screenshot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.metadata.MetadataIndex
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object ScreenshotLibrary {
    private val EXTENSIONS = setOf("png", "jpg", "jpeg")
    private const val JUST_SAVED_WINDOW_MS = 15_000L

    val directory: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "screenshots")

    var revision by mutableIntStateOf(0)
        private set

    private fun isScreenshot(file: File) = file.isFile && file.extension.lowercase() in EXTENSIONS

    fun list(): List<Screenshot> =
        directory.listFiles(::isScreenshot)
            .orEmpty()
            .map(::Screenshot)
            .sortedByDescending { it.modified }

    fun find(name: String): Screenshot? {
        val file = File(directory, name)
        if (!isScreenshot(file) || file.parentFile?.canonicalFile != directory.canonicalFile) return null
        return Screenshot(file)
    }

    fun findJustSaved(message: String): Screenshot? {
        val cutoff = System.currentTimeMillis() - JUST_SAVED_WINDOW_MS
        val found = directory.listFiles { file -> message.contains(file.name) && isScreenshot(file) }
            .orEmpty()
            .map(::Screenshot)
            .filter { it.modified >= cutoff && it.size > 0 }
            .maxByOrNull { it.modified }
        if (found != null) MetadataIndex.record(found.file)
        return found
    }

    fun changed() {
        MainThread.run { revision++ }
    }
}
