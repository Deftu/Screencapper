package dev.deftu.screencapper.screenshot.capture.compression

class QuantizedImage(val indices: ByteArray, val palette: IntArray)

object PaletteQuantizer {
    private const val BITS = 5
    private const val BINS = 1 shl (BITS * 3)

    fun quantize(argb: IntArray, width: Int, height: Int, colors: Int, dither: Boolean): QuantizedImage {
        require(colors in 2..256) { "Palettes hold 2-256 colours, not $colors" }
        val pixels = width * height
        val counts = IntArray(BINS)
        val sumR = LongArray(BINS)
        val sumG = LongArray(BINS)
        val sumB = LongArray(BINS)
        for (i in 0 until pixels) {
            val pixel = argb[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val bin = bin(r, g, b)
            counts[bin]++
            sumR[bin] += r.toLong()
            sumG[bin] += g.toLong()
            sumB[bin] += b.toLong()
        }

        val boxes = medianCut((0 until BINS).filter { counts[it] > 0 }.toIntArray(), counts, colors)
        val palette = IntArray(boxes.size) { index ->
            var total = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (bin in boxes[index]) {
                total += counts[bin]
                r += sumR[bin]
                g += sumG[bin]
                b += sumB[bin]
            }
            (((r / total).toInt()) shl 16) or (((g / total).toInt()) shl 8) or (b / total).toInt()
        }

        val nearest = IntArray(BINS) { -1 }
        fun lookup(r: Int, g: Int, b: Int): Int {
            val bin = bin(r, g, b)
            var found = nearest[bin]
            if (found < 0) {
                found = closest(palette, r, g, b)
                nearest[bin] = found
            }
            return found
        }

        val indices = ByteArray(pixels)
        if (!dither) {
            for (i in 0 until pixels) {
                val pixel = argb[i]
                indices[i] = lookup((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF).toByte()
            }
            return QuantizedImage(indices, palette)
        }

        var current = Array(3) { IntArray(width + 2) }
        var next = Array(3) { IntArray(width + 2) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val pixel = argb[i]
                val r = (((pixel shr 16) and 0xFF) + current[0][x + 1] / 16).coerceIn(0, 255)
                val g = (((pixel shr 8) and 0xFF) + current[1][x + 1] / 16).coerceIn(0, 255)
                val b = ((pixel and 0xFF) + current[2][x + 1] / 16).coerceIn(0, 255)
                val index = lookup(r, g, b)
                indices[i] = index.toByte()
                val chosen = palette[index]
                spread(current[0], next[0], x, r - ((chosen shr 16) and 0xFF))
                spread(current[1], next[1], x, g - ((chosen shr 8) and 0xFF))
                spread(current[2], next[2], x, b - (chosen and 0xFF))
            }
            val done = current
            current = next
            next = done
            for (channel in next) channel.fill(0)
        }
        return QuantizedImage(indices, palette)
    }

    private fun spread(current: IntArray, next: IntArray, x: Int, error: Int) {
        current[x + 2] += error * 7
        next[x] += error * 3
        next[x + 1] += error * 5
        next[x + 2] += error
    }

    private fun bin(r: Int, g: Int, b: Int): Int = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)

    private fun channel(bin: Int, channel: Int): Int = (bin shr (10 - channel * 5)) and 0x1F

    private fun closest(palette: IntArray, r: Int, g: Int, b: Int): Int {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in palette.indices) {
            val colour = palette[i]
            val dr = ((colour shr 16) and 0xFF) - r
            val dg = ((colour shr 8) and 0xFF) - g
            val db = (colour and 0xFF) - b
            val distance = dr * dr * 2 + dg * dg * 4 + db * db * 3
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    private fun medianCut(occupied: IntArray, counts: IntArray, colors: Int): List<IntArray> {
        val boxes = mutableListOf(occupied)
        while (boxes.size < colors) {
            var widest = -1
            var widestChannel = 0
            var widestRange = 0
            for ((index, box) in boxes.withIndex()) {
                if (box.size < 2) continue
                for (c in 0 until 3) {
                    var min = 31
                    var max = 0
                    for (bin in box) {
                        val value = channel(bin, c)
                        if (value < min) min = value
                        if (value > max) max = value
                    }
                    if (max - min > widestRange) {
                        widestRange = max - min
                        widest = index
                        widestChannel = c
                    }
                }
            }
            if (widest < 0) break

            val sorted = boxes[widest].sortedBy { channel(it, widestChannel) }.toIntArray()
            val half = sorted.sumOf { counts[it].toLong() } / 2
            var running = 0L
            var cut = 1
            for (i in 0 until sorted.size - 1) {
                running += counts[sorted[i]]
                cut = i + 1
                if (running >= half) break
            }
            boxes[widest] = sorted.copyOfRange(0, cut)
            boxes.add(sorted.copyOfRange(cut, sorted.size))
        }
        return boxes
    }
}
