package dev.deftu.screencapper.metadata

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.util.zip.InflaterInputStream

/**
 * Reads the text chunks (`tEXt`, `zTXt`, `iTXt`) of a PNG without decoding any pixels.
 *
 * Text written before the image data is all that's read; the file is closed at the first `IDAT`. Anything that isn't a
 * readable PNG gives an empty map instead of an error.
 */
object PngText {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    private const val MAX_TEXT_CHUNK = 4 * 1024 * 1024
    private const val MAX_INFLATED = 4 * 1024 * 1024

    fun read(file: File): Map<String, String> =
        runCatching { BufferedInputStream(file.inputStream(), 8192).use(::read) }.getOrDefault(emptyMap())

    fun read(input: InputStream): Map<String, String> {
        val text = LinkedHashMap<String, String>()
        val data = DataInputStream(input)
        try {
            val signature = ByteArray(SIGNATURE.size)
            data.readFully(signature)
            if (!signature.contentEquals(SIGNATURE)) return emptyMap()

            while (true) {
                val length = data.readInt()
                val type = ByteArray(4).also(data::readFully).toString(Charsets.US_ASCII)
                if (type == "IDAT" || type == "IEND" || length < 0) break
                if ((type == "tEXt" || type == "zTXt" || type == "iTXt") && length <= MAX_TEXT_CHUNK) {
                    val body = ByteArray(length).also(data::readFully)
                    data.readInt()
                    runCatching { decode(type, body) }.getOrNull()?.let { (keyword, value) -> text.putIfAbsent(keyword, value) }
                } else {
                    skipFully(data, length.toLong() + 4)
                }
            }
        } catch (_: EOFException) {
        } catch (_: Exception) {
        }
        return text
    }

    private fun decode(type: String, body: ByteArray): Pair<String, String>? {
        val keywordEnd = body.indexOf(0)
        if (keywordEnd !in 1..79) return null
        val keyword = String(body, 0, keywordEnd, Charsets.ISO_8859_1)
        val rest = keywordEnd + 1
        return when (type) {
            "tEXt" -> keyword to String(body, rest, body.size - rest, Charsets.ISO_8859_1)
            "zTXt" -> keyword to String(inflate(body, rest + 1), Charsets.ISO_8859_1)
            else -> {
                val compressed = body[rest].toInt() == 1
                val languageEnd = body.indexOf(0, rest + 2)
                val translatedEnd = body.indexOf(0, languageEnd + 1)
                if (languageEnd < 0 || translatedEnd < 0) return null
                val start = translatedEnd + 1
                val bytes = if (compressed) inflate(body, start) else body.copyOfRange(start, body.size)
                keyword to String(bytes, Charsets.UTF_8)
            }
        }
    }

    private fun inflate(body: ByteArray, from: Int): ByteArray =
        InflaterInputStream(body.inputStream(from, body.size - from)).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                require(out.size() <= MAX_INFLATED) { "Text chunk too large" }
            }
            out.toByteArray()
        }

    private fun ByteArray.indexOf(value: Byte, from: Int = 0): Int {
        for (i in from until size) if (this[i] == value) return i
        return -1
    }

    private fun skipFully(input: DataInputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) throw EOFException()
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
