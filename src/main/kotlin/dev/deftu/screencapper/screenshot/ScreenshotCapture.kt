package dev.deftu.screencapper.screenshot

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ChatCards
import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.ScreencapperConfig
import java.util.function.Consumer
import java.util.function.Function

object ScreenshotCapture {
    private class Wrapped<T>(val delegate: Consumer<T>, val describe: Function<T, String>) : Consumer<T> {
        override fun accept(message: T) {
            val replaced = runCatching { onMessage(describe.apply(message)) }
                .onFailure { Screencapper.LOGGER.error("Failed to handle a screenshot", it) }
                .getOrDefault(false)
            if (!replaced) delegate.accept(message)
        }
    }

    @JvmStatic
    fun <T> wrap(delegate: Consumer<T>, describe: Function<T, String>): Consumer<T> =
        if (delegate is Wrapped<*>) delegate else Wrapped(delegate, describe)

    @JvmStatic
    fun onMessage(message: String): Boolean {
        if (!ScreencapperConfig.enabled) return false
        val screenshot = ScreenshotLibrary.findJustSaved(message) ?: return false
        val replace = ScreencapperConfig.replaceMessage
        MainThread.run {
            if (replace) ChatCards.captured(screenshot)
            ScreenshotActions.onCaptured(screenshot)
        }
        return replace
    }
}
