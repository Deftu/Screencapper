package dev.deftu.screencapper.screenshot.capture.compression

import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/** The config settings of one use: its choice, plus the detailed options shown when that choice is Custom */
class CompressionFields(
    val choice: KMutableProperty0<CompressionChoice>,
    val format: KMutableProperty0<ImageFormat>,
    val quality: KMutableProperty0<Float>,
    val colours: KMutableProperty0<Int>,
    val dither: KMutableProperty0<Boolean>,
    val resize: KMutableProperty0<ResizeMode>,
    val scale: KMutableProperty0<Float>,
    val maxWidth: KMutableProperty0<Float>,
    /** Null for uploads, which never save anything */
    val keepOriginal: KMutableProperty0<Boolean>?,
) {
    private val isCustom: Boolean get() = choice.get() == CompressionChoice.CUSTOM

    /** The settings to encode with, or null to send the file as it was saved */
    fun profile(): CompressionProfile? {
        val keep = keepOriginal?.get() ?: false
        return when (val selected = choice.get()) {
            CompressionChoice.AS_SAVED -> null
            CompressionChoice.CUSTOM -> {
                val q = quality.get().toInt()
                CompressionProfile(
                    format = format.get(),
                    paletteColors = colours.get(),
                    dither = dither.get(),
                    jpegQuality = q,
                    chroma = CompressionProfile.chromaFor(q),
                    resize = resize.get(),
                    resizePercent = scale.get().toInt(),
                    maxWidth = maxWidth.get().toInt(),
                    keepOriginal = keep,
                ).normalized()
            }

            else -> selected.preset!!.copy(keepOriginal = keep)
        }
    }

    /** Registers when each detailed option is hidden, and asks for a re-check whenever any of these settings changes */
    fun registerVisibility(hideIf: (KProperty<*>, () -> Boolean) -> Unit, refreshOn: (KProperty<*>) -> Unit) {
        hideIf(format) { !isCustom }
        hideIf(quality) { !isCustom || format.get() != ImageFormat.JPEG }
        hideIf(colours) { !isCustom || format.get() != ImageFormat.PNG }
        hideIf(dither) { !isCustom || format.get() != ImageFormat.PNG || colours.get() == 0 }
        hideIf(resize) { !isCustom }
        hideIf(scale) { !isCustom || resize.get() != ResizeMode.PERCENT }
        hideIf(maxWidth) { !isCustom || resize.get() != ResizeMode.MAX_SIZE }
        keepOriginal?.let { hideIf(it) { profile()?.isLossless != false } }
        listOfNotNull(choice, format, quality, colours, dither, resize, scale, maxWidth, keepOriginal).forEach(refreshOn)
    }
}
