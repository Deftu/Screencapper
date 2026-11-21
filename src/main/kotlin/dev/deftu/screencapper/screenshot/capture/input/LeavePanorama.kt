package dev.deftu.screencapper.screenshot.capture.input

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer
import dev.deftu.screencapper.screenshot.panorama.AutoPanoramas

object LeavePanorama {
    @JvmStatic
    var leaving = false

    @JvmStatic
    fun take() {
        try {
            if (!ScreencapperConfig.enabled || !ScreencapperConfig.leavePanorama) return
            PanoramaShot.takeOnLeave(AutoPanoramas.faceSize(ScreencapperConfig.leavePanoramaSize, HighResRenderer.usesTiling))
        } catch (e: Throwable) {
            Screencapper.LOGGER.error("Couldn't take the panorama while leaving the world", e)
        }
    }

    @JvmStatic
    fun afterTasksDropped() {
        if (!leaving) return
        leaving = false
        take()
    }
}
