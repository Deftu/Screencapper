package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.screenshot.ScreenshotCapture
import net.minecraft.client.Minecraft
import java.io.File

//? if >=1.21 {
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
//?} else {
/*import net.minecraft.text.ClickEvent
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
*///?}

/** Vanilla's screenshot chat messages, and the hand-off from a finished save to Screencapper's chat card */
object CaptureResults {
    /**
     * Reports a finished save the way vanilla would: Screencapper's chat card when it's on, otherwise vanilla's message.
     * Safe to call from any thread.
     */
    @JvmStatic
    fun deliver(file: File, error: Throwable?) {
        MainThread.run {
            val message = if (error == null) success(file) else failure(error)
            if (error == null && ScreenshotCapture.onMessage(message.string)) return@run
            show(message)
        }
    }

    //? if >=1.21 {
    @JvmStatic
    fun success(file: File): Component {
        //? if >=1.21.5 {
        val click = ClickEvent.OpenFile(file.absoluteFile)
        //?} else {
        /*val click = ClickEvent(ClickEvent.Action.OPEN_FILE, file.absolutePath)
        *///?}
        val name = Component.literal(file.name).withStyle(ChatFormatting.UNDERLINE).withStyle { it.withClickEvent(click) }
        return Component.translatable("screenshot.success", name)
    }

    @JvmStatic
    fun failure(error: Throwable): Component = Component.translatable("screenshot.failure", error.message.toString())

    private fun show(message: Component) {
        val minecraft = Minecraft.getInstance()
        val gui = minecraft.gui ?: return
        //? if >=26.2 {
        gui.hud.chat.addClientSystemMessage(message)
        //?} else if >=26.1 {
        /*gui.chat.addClientSystemMessage(message)
        *///?} else {
        /*gui.chat.addMessage(message)
        *///?}
    }
    //?} else {
    /*@JvmStatic
    fun success(file: File): Text {
        val name: Text = LiteralText(file.name)
        name.style.clickEvent = ClickEvent(ClickEvent.Action.OPEN_FILE, file.absolutePath)
        name.style.setUnderlined(true)
        return TranslatableText("screenshot.success", name)
    }

    @JvmStatic
    fun failure(error: Throwable): Text = TranslatableText("screenshot.failure", error.message.toString())

    private fun show(message: Text) {
        Minecraft.getInstance().gui?.chat?.addMessage(message)
    }
    *///?}
}
