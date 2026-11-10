package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders one frame larger than the window and hands back its pixels.
 *
 * Contract between the capture input (which decides when and at what size) and the version-specific high-res renderer.
 * Call [capture] on the render thread. It spreads the work over the next few frames and calls [onDone] on the render
 * thread with the image, or null if the capture failed or was cancelled.
 */
object HighResCapture {
    private val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT)
    private val ILLEGAL_CHARACTERS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** Whether a high-res capture is in progress; the window should look normal again once this is false */
    @JvmStatic
    val isCapturing: Boolean
        get() = HighResRenderer.isCapturing

    /** Whether captures on this version are rendered in tiles */
    @JvmStatic
    val isTiled: Boolean
        get() = HighResRenderer.usesTiling

    @JvmStatic
    fun capture(width: Int, height: Int, hideHud: Boolean, onDone: (CapturedImage?) -> Unit) {
        HighResRenderer.start(width, height, hideHud, ScreencapperConfig.highResDelay.toInt(), onDone)
    }

    /** The configured file name pattern with `%time%` and `%world%` filled in, without an extension */
    @JvmStatic
    fun fileName(): String {
        val time = synchronized(TIME_FORMAT) { TIME_FORMAT.format(Date()) }
        val world = runCatching { HighResRenderer.worldName() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "no_world"
        return expand(ScreencapperConfig.highResFileName, time, world)
    }

    internal fun expand(pattern: String, time: String, world: String): String {
        val name = pattern.replace("%time%", time).replace("%world%", world)
            .replace(ILLEGAL_CHARACTERS, "")
            .trim()
            .trimEnd('.')
        return name.ifEmpty { "huge_$time" }
    }
}
