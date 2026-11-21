package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer
import dev.deftu.screencapper.screenshot.capture.highres.PanoramaRenderer
import dev.deftu.screencapper.screenshot.naming.ScreenshotNames

object HighResCapture {
    @JvmStatic
    val isCapturing: Boolean
        get() = HighResRenderer.isCapturing || PanoramaRenderer.isCapturing

    @JvmStatic
    val isTiled: Boolean
        get() = HighResRenderer.usesTiling

    @JvmStatic
    fun capture(width: Int, height: Int, hideHud: Boolean, hideChat: Boolean, hideHand: Boolean, onDone: (CapturedImage?) -> Unit) {
        HighResRenderer.start(width, height, hideHud, hideChat, hideHand, ScreencapperConfig.highResDelay.toInt(), onDone)
    }

    @JvmStatic
    fun fileName(): String = ScreenshotNames.highRes()
}
