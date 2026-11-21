package dev.deftu.screencapper.screenshot

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ChatCards
import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.FastCapture
import dev.deftu.screencapper.screenshot.naming.PendingSaves
import dev.deftu.screencapper.screenshot.naming.ScreenshotNames
import java.io.File
import java.util.function.Consumer
import java.util.function.Function

object ScreenshotCapture {
    private val vanillaSaves = PendingSaves()
    private val completedSaves = PendingSaves()

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
    fun vanillaFile(directory: File): File? {
        if (!ScreencapperConfig.enabled) return null
        return FastCapture.reserve(directory, ScreenshotNames.normal(), "png").also(vanillaSaves::add)
    }

    @JvmStatic
    fun completed(file: File) {
        completedSaves.add(file)
    }

    @JvmStatic
    fun onMessage(message: String): Boolean {
        val settled = vanillaSaves.settle(message, oldestIfUnnamed = false)
        val exact = settled ?: completedSaves.settle(message, oldestIfUnnamed = false)
        val screenshot = when {
            !ScreencapperConfig.enabled -> null
            exact != null -> ScreenshotLibrary.justSaved(exact)
            else -> ScreenshotLibrary.findJustSaved(message)
        }
        (settled ?: if (exact == null && screenshot == null) vanillaSaves.settle(message) else null)?.let(FastCapture::release)
        if (screenshot == null) return false
        val replace = ScreencapperConfig.replaceMessage
        MainThread.run {
            if (replace) ChatCards.captured(screenshot)
            ScreenshotActions.onCaptured(screenshot)
        }
        return replace
    }
}
