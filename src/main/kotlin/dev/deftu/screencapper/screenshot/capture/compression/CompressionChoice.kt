package dev.deftu.screencapper.screenshot.capture.compression

enum class CompressionChoice(val label: String, val preset: CompressionProfile?) {
    AS_SAVED("Send the file as saved", null),
    LOSSLESS("Lossless", CompressionProfile.LOSSLESS),
    SMALLER_PNG("Smaller PNG", CompressionProfile(paletteColors = 256, dither = true)),
    BALANCED("Balanced", CompressionProfile(format = ImageFormat.JPEG, jpegQuality = 90)),
    SMALL("Small", CompressionProfile(format = ImageFormat.JPEG, jpegQuality = 85, resize = ResizeMode.MAX_SIZE, maxWidth = 1920)),
    TINY("Tiny", CompressionProfile(format = ImageFormat.JPEG, jpegQuality = 75, resize = ResizeMode.MAX_SIZE, maxWidth = 1280)),
    CUSTOM("Custom", null);

    companion object {
        val PRESETS = listOf(LOSSLESS, SMALLER_PNG, BALANCED, SMALL, TINY)

        val FOR_SAVING: Array<CompressionChoice> = (PRESETS + CUSTOM).toTypedArray()
        val FOR_UPLOADS: Array<CompressionChoice> = (listOf(AS_SAVED) + PRESETS + CUSTOM).toTypedArray()

        fun matching(profile: CompressionProfile): CompressionChoice? =
            if (profile.isLossless) LOSSLESS else PRESETS.firstOrNull { it.preset!!.looksLike(profile) }
    }
}
