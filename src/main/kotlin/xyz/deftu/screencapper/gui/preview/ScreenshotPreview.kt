package xyz.deftu.screencapper.gui.preview

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.provideDelegate
import gg.essential.universal.UMatrixStack
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import xyz.deftu.screencapper.config.ScreencapperConfig
import xyz.deftu.screencapper.utils.Screenshot

object ScreenshotPreview {
    private val window = Window(ElementaVersion.V1)

    fun initialize() {
        HudRenderCallback.EVENT.register { matrices, tickDelta ->
            if (!ScreencapperConfig.preview) return@register
            window.draw(UMatrixStack(matrices))
        }
    }

    fun append(screenshot: Screenshot) {
        val preview by ScreenshotPreviewComponent(screenshot)
        preview childOf window
        preview.animate()
    }
}
