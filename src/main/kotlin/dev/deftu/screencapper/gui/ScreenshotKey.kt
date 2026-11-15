package dev.deftu.screencapper.gui

//? if >=1.21 {
import com.mojang.blaze3d.platform.InputConstants
//?}
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.api.ui.v1.platform.vanillaProperty

@OptIn(ExperimentalOneConfigUiApi::class)
internal object ScreenshotKey {
    //? if >=26.2 {
    private const val MOUSE_SUPPORTED = true
    //?} else {
    /*private const val MOUSE_SUPPORTED = false
    *///?}

    private val property: Property<OneConfigKeybind?> by lazy {
        //? if >=1.21 {
        val mapping = Minecraft.getInstance().options.keyScreenshot
        //?} else {
        /*val mapping = Minecraft.getInstance().options.screenshotKey
        *///?}
        KeybindHelper.vanillaProperty(mapping).apply {
            if (!MOUSE_SUPPORTED) addCallback { keybind -> keybind != null && keybind.isBound && keybind.isMousePrimary && !vanillaHoldsMouse() }
        }
    }

    private fun vanillaHoldsMouse(): Boolean {
        //? if >=1.21 {
        return InputConstants.getKey(Minecraft.getInstance().options.keyScreenshot.saveString()).type == InputConstants.Type.MOUSE
        //?} else {
        /*return Minecraft.getInstance().options.screenshotKey.keyCode < 0
        *///?}
    }

    val visualizer = Visualizer { _ -> Visualizer.KeybindVisualizer().visualize(property) }
}
