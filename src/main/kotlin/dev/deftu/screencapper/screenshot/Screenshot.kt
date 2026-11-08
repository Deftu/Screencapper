package dev.deftu.screencapper.screenshot

import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/** A screenshot file, with its size and modification time read once when it's listed */
class Screenshot(val file: File) {
    val name: String get() = file.name
    val size: Long = file.length()
    val modified: Long = file.lastModified()

    fun readBytes(): ByteArray = file.readBytes()

    fun readImage(): BufferedImage = ImageIO.read(file) ?: throw IOException("${file.name} is not a readable image")

    override fun equals(other: Any?): Boolean =
        other is Screenshot && other.file == file && other.size == size && other.modified == modified

    override fun hashCode(): Int = (file.hashCode() * 31 + size.hashCode()) * 31 + modified.hashCode()
}
