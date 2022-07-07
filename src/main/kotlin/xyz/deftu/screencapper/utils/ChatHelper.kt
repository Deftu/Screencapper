package xyz.deftu.screencapper.utils

import net.minecraft.client.MinecraftClient
import net.minecraft.text.ClickEvent
import net.minecraft.text.LiteralText
import net.minecraft.util.Formatting

object ChatHelper {
    fun createLineDivider(number: Int) = LiteralText("-".repeat(number)).formatted(Formatting.STRIKETHROUGH)
    fun runClickEvent(runnable: Runnable): ClickEvent {
        return ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, run {
            runnable.run()
            MinecraftClient.getInstance().keyboard.clipboard
        })
    }
}
