package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HighResCapture {
    private val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT)
    private val ILLEGAL_CHARACTERS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    @JvmStatic
    val isCapturing: Boolean
        get() = HighResRenderer.isCapturing

    @JvmStatic
    val isTiled: Boolean
        get() = HighResRenderer.usesTiling

    @JvmStatic
    fun capture(width: Int, height: Int, hideHud: Boolean, onDone: (CapturedImage?) -> Unit) {
        HighResRenderer.start(width, height, hideHud, ScreencapperConfig.highResDelay.toInt(), onDone)
    }

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
