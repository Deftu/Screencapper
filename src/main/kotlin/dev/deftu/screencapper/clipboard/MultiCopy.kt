package dev.deftu.screencapper.clipboard

import java.io.File

sealed interface MultiCopyResult {
    object NothingToCopy : MultiCopyResult

    data class Single(val file: File) : MultiCopyResult

    data class Files(val files: List<File>) : MultiCopyResult

    data class Paths(val files: List<File>) : MultiCopyResult

    data class Failed(val files: List<File>) : MultiCopyResult
}

object MultiCopy {
    fun copyable(files: List<File>): List<File> = files.map { it.absoluteFile }.filter { it.isFile }.distinct()

    fun pathsText(files: List<File>): String = files.joinToString(System.lineSeparator()) { it.absolutePath }

    fun copy(files: List<File>, setFiles: (List<File>) -> Boolean, setText: (String) -> Boolean): MultiCopyResult {
        val copyable = copyable(files)
        return when {
            copyable.isEmpty() -> MultiCopyResult.NothingToCopy
            copyable.size == 1 -> MultiCopyResult.Single(copyable.first())
            runCatching { setFiles(copyable) }.getOrDefault(false) -> MultiCopyResult.Files(copyable)
            runCatching { setText(pathsText(copyable)) }.getOrDefault(false) -> MultiCopyResult.Paths(copyable)
            else -> MultiCopyResult.Failed(copyable)
        }
    }
}
