package dev.deftu.screencapper.screenshot.capture

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Writes opaque RGB or indexed PNGs, compressing horizontal strips of the image in parallel.
 *
 * Each strip is deflated on its own and flushed to a byte boundary, so the strips concatenate into one valid zlib
 * stream. Screenshots have no useful alpha, so it's dropped, which also makes the file smaller.
 */
object PngEncoder {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    private const val MIN_STRIP_ROWS = 64
    private const val ADLER_MOD = 65521L
    private const val COLOR_RGB = 2
    private const val COLOR_INDEXED = 3

    /** [argb] holds [width] x [height] pixels, top row first */
    fun encode(argb: IntArray, width: Int, height: Int, level: Int, pool: ExecutorService?, threads: Int, out: OutputStream, text: Map<String, String> = emptyMap()) {
        require(argb.size >= width * height) { "Not enough pixels for a ${width}x$height image" }
        write(width, height, COLOR_RGB, null, 1 + width * 3, level, pool, threads, out, text) { y, raw, start ->
            // Sub filter: each byte minus the same channel of the pixel to its left. Cheap, and screenshots shrink a lot.
            raw[start] = 1
            var offset = start + 1
            val row = y * width
            var previous = 0
            for (x in 0 until width) {
                val pixel = argb[row + x]
                raw[offset] = ((pixel shr 16) - (previous shr 16)).toByte()
                raw[offset + 1] = ((pixel shr 8) - (previous shr 8)).toByte()
                raw[offset + 2] = (pixel - previous).toByte()
                offset += 3
                previous = pixel
            }
        }
    }

    /** [indices] holds one palette index per pixel, top row first; [palette] holds 1-256 RGB colours */
    fun encodeIndexed(indices: ByteArray, palette: IntArray, width: Int, height: Int, level: Int, pool: ExecutorService?, threads: Int, out: OutputStream, text: Map<String, String> = emptyMap()) {
        require(indices.size >= width * height) { "Not enough pixels for a ${width}x$height image" }
        require(palette.size in 1..256) { "A PNG palette holds 1-256 colours, not ${palette.size}" }
        val plte = ByteArray(palette.size * 3)
        palette.forEachIndexed { i, colour ->
            plte[i * 3] = (colour shr 16).toByte()
            plte[i * 3 + 1] = (colour shr 8).toByte()
            plte[i * 3 + 2] = colour.toByte()
        }
        // No filter: the PNG spec advises it for indexed images, where neighbouring indices don't predict each other.
        write(width, height, COLOR_INDEXED, plte, 1 + width, level, pool, threads, out, text) { y, raw, start ->
            raw[start] = 0
            System.arraycopy(indices, y * width, raw, start + 1, width)
        }
    }

    private fun write(
        width: Int,
        height: Int,
        colorType: Int,
        plte: ByteArray?,
        stride: Int,
        level: Int,
        pool: ExecutorService?,
        threads: Int,
        out: OutputStream,
        text: Map<String, String>,
        fillRow: (y: Int, raw: ByteArray, offset: Int) -> Unit,
    ) {
        val strips = if (pool == null) 1 else (height / MIN_STRIP_ROWS).coerceIn(1, threads.coerceAtLeast(1))
        val rowsPerStrip = (height + strips - 1) / strips
        val tasks = (0 until strips).map { index ->
            val from = index * rowsPerStrip
            val to = minOf(height, from + rowsPerStrip)
            Callable { compressStrip(stride, from, to, level, last = to == height, fillRow) }
        }
        val parts = if (pool == null) tasks.map { it.call() } else pool.invokeAll(tasks).map { it.get() }

        val data = DataOutputStream(out)
        data.write(SIGNATURE)
        chunk(data, "IHDR", ByteArrayOutputStream(13).also { header ->
            DataOutputStream(header).apply {
                writeInt(width)
                writeInt(height)
                writeByte(8)
                writeByte(colorType)
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
        }.toByteArray())
        if (plte != null) chunk(data, "PLTE", plte)

        for ((keyword, value) in text) chunk(data, "iTXt", textChunk(keyword, value))

        var adler = 1L
        for (part in parts) adler = combineAdler(adler, part.adler, part.rawLength)
        val idat = ByteArrayOutputStream(parts.sumOf { it.deflated.size } + 6)
        idat.write(0x78)
        idat.write(0x01)
        for (part in parts) idat.write(part.deflated)
        DataOutputStream(idat).writeInt(adler.toInt())
        chunk(data, "IDAT", idat.toByteArray())
        chunk(data, "IEND", ByteArray(0))
        data.flush()
    }

    private class Strip(val deflated: ByteArray, val adler: Long, val rawLength: Long)

    private fun compressStrip(stride: Int, from: Int, to: Int, level: Int, last: Boolean, fillRow: (Int, ByteArray, Int) -> Unit): Strip {
        val raw = ByteArray(stride * (to - from))
        for (y in from until to) fillRow(y, raw, (y - from) * stride)

        val adler = Adler32().apply { update(raw) }.value
        val deflater = Deflater(level, true)
        try {
            deflater.setInput(raw)
            val out = ByteArrayOutputStream(raw.size / 3)
            val buffer = ByteArray(64 * 1024)
            if (last) {
                deflater.finish()
                while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            } else {
                while (true) {
                    val written = deflater.deflate(buffer, 0, buffer.size, Deflater.SYNC_FLUSH)
                    out.write(buffer, 0, written)
                    if (written < buffer.size && deflater.needsInput()) break
                }
            }
            return Strip(out.toByteArray(), adler, raw.size.toLong())
        } finally {
            deflater.end()
        }
    }

    /** zlib's adler32_combine: the checksum of A followed by B, from both checksums and B's length */
    private fun combineAdler(first: Long, second: Long, secondLength: Long): Long {
        val remainder = secondLength % ADLER_MOD
        var sum1 = first and 0xFFFF
        var sum2 = (remainder * sum1) % ADLER_MOD
        sum1 += (second and 0xFFFF) + ADLER_MOD - 1
        sum2 += ((first shr 16) and 0xFFFF) + ((second shr 16) and 0xFFFF) + ADLER_MOD - remainder
        if (sum1 >= ADLER_MOD) sum1 -= ADLER_MOD
        if (sum1 >= ADLER_MOD) sum1 -= ADLER_MOD
        if (sum2 >= ADLER_MOD shl 1) sum2 -= ADLER_MOD shl 1
        if (sum2 >= ADLER_MOD) sum2 -= ADLER_MOD
        return (sum2 shl 16) or sum1
    }

    /** An uncompressed iTXt chunk body: UTF-8 text under a Latin-1 keyword */
    private fun textChunk(keyword: String, value: String): ByteArray {
        require(keyword.length in 1..79) { "PNG text keywords must be 1-79 characters: $keyword" }
        return ByteArrayOutputStream().apply {
            write(keyword.toByteArray(Charsets.ISO_8859_1))
            write(byteArrayOf(0, 0, 0, 0, 0))
            write(value.toByteArray(Charsets.UTF_8))
        }.toByteArray()
    }

    private fun chunk(out: DataOutputStream, type: String, body: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.writeInt(body.size)
        out.write(typeBytes)
        out.write(body)
        out.writeInt(CRC32().apply { update(typeBytes); update(body) }.value.toInt())
    }
}
