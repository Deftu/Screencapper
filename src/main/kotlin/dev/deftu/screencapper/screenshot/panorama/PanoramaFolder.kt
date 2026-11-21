package dev.deftu.screencapper.screenshot.panorama

import dev.deftu.screencapper.screenshot.naming.PathSanitizer
import dev.deftu.screencapper.screenshot.naming.ScreenshotPaths
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

object PanoramaFolder {
    fun faces(dir: File): List<File>? {
        val files = PanoramaLayout.FACES.map { File(dir, it.fileName) }
        if (!files.all { it.isFile }) return null
        val sizes = files.map { sizeOf(it) ?: return null }
        val (width, height) = sizes.first()
        if (width <= 0 || width != height || sizes.any { it != sizes.first() }) return null
        return files
    }

    fun validated(root: File, id: String, allowLegacy: Boolean = false): File? =
        resolve(root, id, allowLegacy)?.takeIf { faces(it) != null }

    fun resolve(root: File, id: String, allowLegacy: Boolean = false): File? {
        if (id.isBlank() || '\u0000' in id || id.split('/', '\\').any { it == ".." || it == "." }) return null
        val base = canonical(root) ?: return null
        val dir = canonical(File(base, id)) ?: return null
        if (!dir.path.startsWith(base.path + File.separator)) return null
        val parts = relative(base, dir).split('/')
        val inside = parts.size > 1 && parts.first().equals(PathSanitizer.PANORAMAS, ignoreCase = true) && parts.none { it.startsWith(".") }
        val legacy = allowLegacy && parts.all(ScreenshotPaths::isListedFolder)
        if (!inside && !legacy) return null
        return dir.takeIf { it.isDirectory }
    }

    fun idOf(root: File, dir: File): String? {
        val base = canonical(root) ?: return null
        val target = canonical(dir) ?: return null
        if (!target.path.startsWith(base.path + File.separator)) return null
        return relative(base, target)
    }

    private fun sizeOf(file: File): Pair<Int, Int>? = try {
        ImageIO.createImageInputStream(file)?.use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
            try {
                reader.input = stream
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    } catch (_: IOException) {
        null
    }

    private fun relative(base: File, file: File): String = file.toRelativeString(base).replace(File.separatorChar, '/')

    private fun canonical(file: File): File? = try {
        file.canonicalFile
    } catch (_: IOException) {
        null
    }
}
