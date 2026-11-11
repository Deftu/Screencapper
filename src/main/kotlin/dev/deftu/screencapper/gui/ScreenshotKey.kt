package dev.deftu.screencapper.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.deftu.screencapper.Screencapper
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import java.util.function.Consumer
import java.util.function.Supplier

//? if >=1.21 {
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
//?} else {
/*import net.minecraft.client.options.KeyBinding
import org.polyfrost.oneconfig.internal.legacy.InputConstants
import org.polyfrost.oneconfig.internal.legacy.KeyCodes
*///?}

internal object ScreenshotKey {
    data class Bound(val mouse: Boolean, val code: Int)

    private val minecraft: Minecraft get() = Minecraft.getInstance()

    fun current(): Bound? = runCatching { read() }.getOrNull()

    private fun read(): Bound? {
        //? if >=1.21 {
        return bound(InputConstants.getKey(minecraft.options.keyScreenshot.saveString()))
        //?} else {
        /*return bound(KeyCodes.fromLegacy(minecraft.options.screenshotKey.keyCode))
        *///?}
    }

    private fun default(): Bound? = runCatching {
        //? if >=1.21 {
        bound(minecraft.options.keyScreenshot.defaultKey)
        //?} else {
        /*bound(KeyCodes.fromLegacy(minecraft.options.screenshotKey.defaultKeyCode))
        *///?}
    }.getOrNull()

    private fun bound(key: InputConstants.Key): Bound? {
        if (key == InputConstants.UNKNOWN) return null
        return Bound(key.type == InputConstants.Type.MOUSE, key.value)
    }

    //? if >=26.2 {
    private const val MOUSE_SUPPORTED = true
    //?} else {
    /*private const val MOUSE_SUPPORTED = false
    *///?}

    fun set(bound: Bound?) {
        if (bound != null && bound.mouse && !MOUSE_SUPPORTED) return
        runCatching {
            //? if >=1.21 {
            val key = when {
                bound == null -> InputConstants.UNKNOWN
                bound.mouse -> InputConstants.Type.MOUSE.getOrCreate(bound.code)
                //? if >=26.3 {
                else -> InputConstants.Type.KEYBOARD.getOrCreate(bound.code)
                //?} else {
                /*else -> InputConstants.Type.KEYSYM.getOrCreate(bound.code)
                *///?}
            }
            minecraft.options.keyScreenshot.setKey(key)
            KeyMapping.resetMapping()
            //?} else {
            /*val code = when {
                bound == null -> 0
                bound.mouse -> KeyCodes.mouseToLegacy(bound.code).let { if (it < 0) 0 else it - 100 }
                else -> KeyCodes.toLegacy(bound.code)
            }
            minecraft.options.screenshotKey.keyCode = code
            KeyBinding.resetMapping()
            *///?}
            minecraft.options.save()
        }.onFailure { Screencapper.LOGGER.error("Couldn't rebind the screenshot key", it) }
    }

    private fun Bound.toKeybind(): OneConfigKeybind =
        OneConfigKeybind(if (mouse) null else intArrayOf(code), if (mouse) intArrayOf(code) else null, KeyModifiers.NONE, 0L) { true }

    private fun OneConfigKeybind.toBound(): Bound? = when {
        !isBound -> null
        isMousePrimary -> Bound(true, mouseBtns!!.first())
        else -> Bound(false, keyCodes!!.first())
    }

    private val property: Property<OneConfigKeybind?> by lazy {
        Properties.functional<OneConfigKeybind?>(
            Supplier { current()?.toKeybind() },
            Consumer { keybind -> set(keybind?.toBound()) },
            "screenshotKey",
            "Screenshot key",
            null,
            @Suppress("UNCHECKED_CAST") (OneConfigKeybind::class.java as Class<OneConfigKeybind?>),
        ).apply {
            addMetadata("singleKey", true)
            default()?.let { addMetadata("default", it.toKeybind()) }
        }
    }

    val visualizer = Visualizer { _ -> Control() }

    @Composable
    private fun Control() {
        var shown by remember { mutableStateOf(current()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                shown = current()
            }
        }
        key(shown) { Visualizer.KeybindVisualizer().visualize(property) }
    }
}
