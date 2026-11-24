package dev.deftu.screencapper.screenshot

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.wheel.CaptureOverlay
import dev.deftu.screencapper.screenshot.capture.HighResCapture
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.platform.v1.Platform

//? if >=1.21 && <26.2 {
/*import net.minecraft.network.chat.Component
*///?}
//? if <1.21 {
/*import dev.deftu.screencapper.chat.LegacyChatSuppression
*///?}

object AutoCapture {
    private val schedule = AutoCaptureSchedule(delayTicks = 20, windowTicks = 80, cooldownTicks = 100)
    private var installed = false
    private var ticks = 0L

    @JvmStatic
    fun onAdvancementsUpdated(doneBefore: Set<String>, doneAfter: List<DoneAdvancement>, reset: Boolean, shown: Boolean) {
        if (!wanted()) return
        val earned = newlyEarnedAdvancements(doneBefore, doneAfter, reset, shown)
        if (earned.isEmpty()) return
        install()
        val connection = connection()
        for (id in earned) schedule.earned(id, connection, ticks)
    }

    private fun install() {
        if (installed) return
        installed = true
        EventManager.register(TickEvent.End::class.java) { _: TickEvent.End -> onTick() }
    }

    private fun onTick() {
        ticks++
        if (!schedule.pending || !schedule.shouldShoot(ticks, blocked())) return
        runCatching { grab() }.onFailure { Screencapper.LOGGER.error("Couldn't take an advancement screenshot", it) }
    }

    private fun wanted(): Boolean = ScreencapperConfig.enabled && ScreencapperConfig.advancementShots

    private fun blocked(): Boolean =
        !wanted() || Minecraft.getInstance().player == null || Platform.screen().current<Any?>() != null || CaptureOverlay.isOpen || HighResCapture.isCapturing

    private fun connection(): Any? {
        //? if >=1.21 {
        return Minecraft.getInstance().getConnection()
        //?} else {
        /*return Minecraft.getInstance().getNetworkHandler()
        *///?}
    }

    internal fun grab() {
        val minecraft = Minecraft.getInstance()
        //? if >=26.2 {
        Screenshot.grab(minecraft, false)
        //?} else if >=1.21 {
        /*Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget()) { message: Component -> minecraft.execute { show(minecraft, message) } }
        *///?} else {
        /*val message = Screenshot.take(minecraft.gameDir, minecraft.width, minecraft.height, minecraft.getRenderTarget())
        if (!LegacyChatSuppression.consume(message)) minecraft.gui.chat.addMessage(message)
        *///?}
    }

    //? if >=26.1 && <26.2 {
    /*private fun show(minecraft: Minecraft, message: Component) {
        minecraft.gui.chat.addClientSystemMessage(message)
    }
    *///?} else if >=1.21 && <26.1 {
    /*private fun show(minecraft: Minecraft, message: Component) {
        minecraft.gui.chat.addMessage(message)
    }
    *///?}
}
