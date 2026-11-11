package dev.deftu.screencapper.screenshot.capture.compression

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.InflaterInputStream

object PngChunks {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    private val METADATA_TYPES = setOf("tEXt", "iTXt", "zTXt", "eXIf", "tIME")

    fun isPng(bytes: ByteArray): Boolean = bytes.size >= SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

    fun stripText(png: ByteArray): ByteArray {
        if (!isPng(png)) return png
        val out = ByteArrayOutputStream(png.size)
        out.write(png, 0, SIGNATURE.size)
        val ok = forEachChunk(png) { type, start, length ->
            if (type !in METADATA_TYPES) out.write(png, start, length + 12)
        }
        return if (ok) out.toByteArray() else png
    }

    fun readText(png: ByteArray): Map<String, String> {
        if (!isPng(png)) return emptyMap()
        val text = LinkedHashMap<String, String>()
        forEachChunk(png) { type, start, length ->
            val body = png.copyOfRange(start + 8, start + 8 + length)
            runCatching { parseText(type, body) }.getOrNull()?.let { (keyword, value) -> text[keyword] = value }
        }
        return text
    }

    private fun parseText(type: String, body: ByteArray): Pair<String, String>? {
        val keywordEnd = body.indexOf(0)
        if (keywordEnd <= 0) return null
        val keyword = String(body, 0, keywordEnd, Charsets.ISO_8859_1)
        return when (type) {
            "tEXt" -> keyword to String(body, keywordEnd + 1, body.size - keywordEnd - 1, Charsets.ISO_8859_1)
            "zTXt" -> keyword to String(inflate(body, keywordEnd + 2), Charsets.ISO_8859_1)
            "iTXt" -> {
                val compressed = body[keywordEnd + 1].toInt() != 0
                val languageEnd = indexOf(body, 0, keywordEnd + 3)
                val translatedEnd = indexOf(body, 0, languageEnd + 1)
                val start = translatedEnd + 1
                val value = if (compressed) inflate(body, start) else body.copyOfRange(start, body.size)
                keyword to String(value, Charsets.UTF_8)
            }

            else -> null
        }
    }

    private fun inflate(body: ByteArray, from: Int): ByteArray =
        InflaterInputStream(body.inputStream(from, body.size - from)).use { it.readBytes() }

    private fun indexOf(bytes: ByteArray, value: Byte, from: Int): Int {
        for (i in from until bytes.size) if (bytes[i] == value) return i
        throw IllegalArgumentException("Malformed text chunk")
    }

    private inline fun forEachChunk(png: ByteArray, action: (type: String, start: Int, length: Int) -> Unit): Boolean {
        val buffer = ByteBuffer.wrap(png)
        var offset = SIGNATURE.size
        while (offset + 12 <= png.size) {
            val length = buffer.getInt(offset)
            if (length < 0 || offset.toLong() + 12 + length > png.size) return false
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            action(type, offset, length)
            offset += 12 + length
            if (type == "IEND") return true
        }
        return false
    }
}
