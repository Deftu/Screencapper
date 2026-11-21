package dev.deftu.screencapper.editor

import dev.deftu.screencapper.screenshot.capture.CapturedImage
import dev.deftu.screencapper.screenshot.capture.FastCapture
import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfile
import dev.deftu.screencapper.screenshot.capture.compression.ImageCompressor
import dev.deftu.screencapper.screenshot.capture.compression.ImageFormat
import dev.deftu.screencapper.screenshot.capture.compression.PngChunks
import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.Files

object EditedCopies {
    private const val SUFFIX = "-edited"
    private const val JPEG_QUALITY = 95
    private val EDITED = Regex("^(.+)$SUFFIX(_\\d+)?$")
    private val JPEG_EXTENSIONS = setOf("jpg", "jpeg")

    fun baseName(name: String): String = (EDITED.matchEntire(name)?.groupValues?.get(1) ?: name) + SUFFIX

    fun reserve(original: File): File =
        FastCapture.reserve(original.absoluteFile.parentFile, baseName(original.nameWithoutExtension), original.extension.ifEmpty { "png" })

    fun save(original: File, source: CapturedImage, doc: EditDoc, render: (CapturedImage, List<Item>) -> CapturedImage = EditRenderer::render): File {
        val result = EditRenderer.apply(source, doc, render)
        val jpeg = original.extension.lowercase() in JPEG_EXTENSIONS
        val profile = if (jpeg) CompressionProfile(format = ImageFormat.JPEG, jpegQuality = JPEG_QUALITY, chroma = CompressionProfile.chromaFor(JPEG_QUALITY)) else CompressionProfile.LOSSLESS
        val metadata = if (jpeg) emptyMap() else PngChunks.readText(original.readBytes()).filterKeys { it.length in 1..79 }
        val target = reserve(original)
        try {
            val temp = File(target.parentFile, ".${target.name}.tmp")
            try {
                BufferedOutputStream(Files.newOutputStream(temp.toPath()), 1 shl 16).use { ImageCompressor.encode(result, profile, metadata, it) }
                Files.move(temp.toPath(), target.toPath())
            } finally {
                temp.delete()
            }
        } finally {
            FastCapture.release(target)
        }
        return target
    }
}
