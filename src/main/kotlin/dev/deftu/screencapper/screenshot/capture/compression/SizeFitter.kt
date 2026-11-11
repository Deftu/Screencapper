package dev.deftu.screencapper.screenshot.capture.compression

sealed interface FitResult {
    class Fits(val bytes: ByteArray, val profile: CompressionProfile, val scale: Double) : FitResult

    class TooLarge(val smallestSize: Long) : FitResult
}

object SizeFitter {
    private const val QUALITY_STEP = 10
    private const val MIN_QUALITY = 50
    private val SCALES = listOf(0.85, 0.7, 0.55, 0.4, 0.25)

    fun fit(limit: Long?, profile: CompressionProfile, encode: (CompressionProfile, Double) -> ByteArray): FitResult {
        var smallest = Long.MAX_VALUE
        for ((attempt, scale) in attempts(profile)) {
            val bytes = encode(attempt, scale)
            if (limit == null || bytes.size <= limit) return FitResult.Fits(bytes, attempt, scale)
            smallest = minOf(smallest, bytes.size.toLong())
        }
        return FitResult.TooLarge(smallest)
    }

    private fun attempts(profile: CompressionProfile): Sequence<Pair<CompressionProfile, Double>> = sequence {
        yield(profile to 1.0)
        var last = profile
        if (profile.format == ImageFormat.JPEG) {
            var quality = profile.jpegQuality - QUALITY_STEP
            while (quality >= MIN_QUALITY) {
                last = profile.copy(jpegQuality = quality)
                yield(last to 1.0)
                quality -= QUALITY_STEP
            }
        }
        for (scale in SCALES) yield(last to scale)
    }
}
