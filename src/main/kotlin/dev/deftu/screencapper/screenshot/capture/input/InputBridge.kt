package dev.deftu.screencapper.screenshot.capture.input

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.platform.v1.Platform
import java.io.File

//? if >=1.21 {
import com.mojang.blaze3d.platform.InputConstants
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

    fun digitDown(): Int = DIGITS.indexOfFirst(::isKeyDown)

    fun escapeDown(): Boolean = isKeyDown(ESCAPE)

    val screenOpen: Boolean get() = Platform.screen().current<Any?>() != null

    fun sensitivityGain(): Double = runCatching {
        //? if >=1.21 {
        val sensitivity = minecraft.options.sensitivity().get()
        //?} else {
        /*val sensitivity = minecraft.options.mouseSensitivity.toDouble()
        *///?}
        val f = sensitivity * 0.6 + 0.2
        f * f * f * 8.0
    }.getOrDefault(1.0)

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
