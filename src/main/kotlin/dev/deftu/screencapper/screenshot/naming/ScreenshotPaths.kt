package dev.deftu.screencapper.screenshot.naming

import java.io.File
import java.io.IOException
import java.nio.file.Files

sealed interface RenameCheck {
    data class Ok(val file: File, val id: String) : RenameCheck
    data class Invalid(val reason: String) : RenameCheck
}

object ScreenshotPaths {
    private val EXTENSIONS = setOf("png", "jpg", "jpeg")

    fun isScreenshotName(name: String): Boolean =
        !name.startsWith(".") && name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    fun isListedFolder(name: String): Boolean =
        !name.startsWith(".") && !name.equals(PathSanitizer.ORIGINALS, ignoreCase = true)

    fun list(root: File, accept: (File) -> Boolean = { true }): List<Pair<String, File>> {
        val base = canonical(root) ?: return emptyList()
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown()
            .maxDepth(PathSanitizer.MAX_DEPTH)
            .onEnter { it == base || (isListedFolder(it.name) && !(it.parentFile == base && isPanoramas(it.name)) && isInside(base, it)) }
            .filter { isScreenshotName(it.name) && accept(it) && it.isFile }
            .map { relative(base, it) to it }
            .toList()
    }

    fun idOf(root: File, file: File): String {
        val base = canonical(root) ?: return file.name
        val target = canonical(file) ?: return file.name
        if (!target.path.startsWith(base.path + File.separator)) return file.name
        return relative(base, target)
    }

    fun resolve(root: File, id: String): File? {
        if (id.isBlank() || '\u0000' in id) return null
        val base = canonical(root) ?: return null
        val file = canonical(File(base, id)) ?: return null
        if (!file.path.startsWith(base.path + File.separator)) return null
        val parts = relative(base, file).split('/')
        if (!parts.dropLast(1).all(::isListedFolder) || !isScreenshotName(parts.last())) return null
        if (parts.size > 1 && isPanoramas(parts.first())) return null
        return file.takeIf { it.isFile }
    }

    fun renameTarget(root: File, current: File, input: String): RenameCheck {
        val extension = current.extension
        val typed = input.trim().let { if (extension.isNotEmpty() && it.endsWith(".$extension", ignoreCase = true)) it.dropLast(extension.length + 1) else it }
        val path = PathSanitizer.path(typed) ?: return RenameCheck.Invalid("Enter a name.")
        val id = if (extension.isEmpty()) path else "$path.$extension"
        val base = canonical(root) ?: return RenameCheck.Invalid("Your screenshots folder can't be read.")
        val target = File(base, id)
        if (canonical(target)?.path?.startsWith(base.path + File.separator) != true) return RenameCheck.Invalid("That name points outside your screenshots folder.")
        if (id == idOf(base, current)) return RenameCheck.Invalid("That's already its name.")
        if (target.exists() && !sameFile(target, current)) return RenameCheck.Invalid("Another screenshot already has that name.")
        return RenameCheck.Ok(target, id)
    }

    fun originalOf(file: File): File = File(File(file.parentFile, PathSanitizer.ORIGINALS), "${file.nameWithoutExtension}.png")

    fun ownsOriginal(file: File): Boolean {
        val siblings = file.parentFile?.listFiles { other -> other != file && other.nameWithoutExtension == file.nameWithoutExtension && isScreenshotName(other.name) && other.isFile }.orEmpty()
        if (siblings.isEmpty()) return true
        return !file.extension.equals("png", ignoreCase = true) && siblings.all { it.extension.equals("png", ignoreCase = true) }
    }

    private fun isInside(base: File, dir: File): Boolean = canonical(dir)?.path?.startsWith(base.path + File.separator) == true

    private fun isPanoramas(name: String): Boolean = name.equals(PathSanitizer.PANORAMAS, ignoreCase = true)

    private fun relative(base: File, file: File): String = file.toRelativeString(base).replace(File.separatorChar, '/')

    private fun canonical(file: File): File? = try {
        file.canonicalFile
    } catch (_: IOException) {
        null
    }

    private fun sameFile(a: File, b: File): Boolean = try {
        Files.isSameFile(a.toPath(), b.toPath())
    } catch (_: IOException) {
        false
    }
}
