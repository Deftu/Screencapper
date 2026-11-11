package dev.deftu.screencapper.screenshot.capture.input

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.platform.v1.Platform
import dev.deftu.screencapper.gui.wheel.WheelClick
import dev.deftu.screencapper.gui.wheel.wheelClick
import java.io.File

//? if >=1.21 {
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.system.Platform as LwjglPlatform
import org.lwjgl.system.windows.User32
//?} else {
/*import org.lwjgl.input.Keyboard
*///?}

internal object InputBridge {
    private val minecraft: Minecraft get() = Minecraft.getInstance()

    //? if >=26.3 {
    private val DIGITS = IntArray(9) { 30 + it }
    private const val ESCAPE = 41
    //?} else if >=1.21 {
    /*private val DIGITS = IntArray(9) { 49 + it }
    private const val ESCAPE = 256
    *///?} else {
    /*private val DIGITS = IntArray(9) { 2 + it }
    private const val ESCAPE = 1
    *///?}

    val gameDirectory: File
        //? if >=1.21 {
        get() = minecraft.gameDirectory
        //?} else {
        /*get() = minecraft.gameDir
        *///?}

    fun isKeyDown(key: Int): Boolean {
        //? if >=26.3 {
        return InputConstants.isKeyDown(key)
        //?} else if >=1.21.9 {
        /*return InputConstants.isKeyDown(minecraft.window, key)
        *///?} else if >=1.21 {
        /*return InputConstants.isKeyDown(minecraft.window.window, key)
        *///?} else {
        /*return Keyboard.isKeyDown(key)
        *///?}
    }

    fun digitOf(key: Int): Int = DIGITS.indexOf(key)

    fun isEscape(key: Int): Boolean = key == ESCAPE

    fun wheelClick(button: Int): WheelClick {
        //? if >=1.21 {
        return wheelClick(button, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOUSE_BUTTON_RIGHT, buttonsSwapped())
        //?} else {
        /*return wheelClick(button, 0, 1, false)
        *///?}
    }

    //? if >=1.21 {
    private fun buttonsSwapped(): Boolean = runCatching {
        LwjglPlatform.get() == LwjglPlatform.WINDOWS && User32.GetSystemMetrics(User32.SM_SWAPBUTTON) != 0
    }.getOrDefault(false)
    //?}

    val screenOpen: Boolean get() = Platform.screen().current<Any?>() != null

    fun sensitivity(): Double = runCatching {
        //? if >=1.21 {
        minecraft.options.sensitivity().get()
        //?} else {
        /*minecraft.options.mouseSensitivity.toDouble()
        *///?}
    }.getOrDefault(0.5)

    var hudHidden: Boolean
        //? if >=26.2 {
        get() = minecraft.gui.hud.isHidden
        set(value) {
            if (minecraft.gui.hud.isHidden != value) minecraft.gui.hud.toggle()
        }
        //?} else {
        /*get() = minecraft.options.hideGui
        set(value) {
            minecraft.options.hideGui = value
        }
        *///?}
}
