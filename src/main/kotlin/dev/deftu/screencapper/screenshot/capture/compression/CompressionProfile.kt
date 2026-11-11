package dev.deftu.screencapper.screenshot.capture.compression

enum class ImageFormat(val displayName: String, val extension: String, val contentType: String) {
    PNG("PNG", "png", "image/png"),
    JPEG("JPEG", "jpg", "image/jpeg"),
}

enum class ChromaSubsampling(val displayName: String) {
    FULL("4:4:4"),
    HALF("4:2:0"),
}

enum class ResizeMode(val displayName: String) {
    NONE("Full size"),
    PERCENT("By percentage"),
    MAX_SIZE("To a max width"),
}

data class CompressionProfile(
    val format: ImageFormat = ImageFormat.PNG,
    val pngLevel: Int = 4,
    val paletteColors: Int = 0,
    val dither: Boolean = true,
    val jpegQuality: Int = 90,
    val chroma: ChromaSubsampling = ChromaSubsampling.HALF,
    val resize: ResizeMode = ResizeMode.NONE,
    val resizePercent: Int = 50,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val keepOriginal: Boolean = false,
) {
    val isLossless: Boolean get() = format == ImageFormat.PNG && paletteColors == 0 && resize == ResizeMode.NONE

    fun normalized(): CompressionProfile = copy(
        pngLevel = pngLevel.coerceIn(0, 9),
        paletteColors = if (paletteColors <= 0) 0 else PALETTE_SIZES.minByOrNull { kotlin.math.abs(it - paletteColors) }!!,
        jpegQuality = jpegQuality.coerceIn(1, 100),
        resizePercent = resizePercent.coerceIn(1, 100),
        maxWidth = maxWidth.coerceAtLeast(0),
        maxHeight = maxHeight.coerceAtLeast(0),
    )

    fun looksLike(other: CompressionProfile): Boolean {
        val a = normalized()
        val b = other.normalized()
        if (a.format != b.format) return false
        val sameEncoding = when (a.format) {
            ImageFormat.PNG -> a.paletteColors == b.paletteColors && (a.paletteColors == 0 || a.dither == b.dither)
            ImageFormat.JPEG -> a.jpegQuality == b.jpegQuality
        }
        val sameSize = a.resize == b.resize && when (a.resize) {
            ResizeMode.NONE -> true
            ResizeMode.PERCENT -> a.resizePercent == b.resizePercent
            ResizeMode.MAX_SIZE -> a.maxWidth == b.maxWidth && a.maxHeight == b.maxHeight
        }
        return sameEncoding && sameSize
    }

    companion object {
        val PALETTE_SIZES = listOf(256, 128, 64)

        val LOSSLESS = CompressionProfile()

        val UPLOAD = CompressionProfile(format = ImageFormat.JPEG, jpegQuality = 90, chroma = ChromaSubsampling.HALF)

        fun chromaFor(quality: Int): ChromaSubsampling = if (quality >= 95) ChromaSubsampling.FULL else ChromaSubsampling.HALF
    }
}
