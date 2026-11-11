package dev.deftu.screencapper.screenshot.capture.input

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.wheel.CaptureOverlay
import dev.deftu.screencapper.gui.wheel.WheelEntry
import dev.deftu.screencapper.metadata.MetadataCollector
import dev.deftu.screencapper.screenshot.capture.CaptureKind
import dev.deftu.screencapper.screenshot.capture.CaptureResults
import dev.deftu.screencapper.screenshot.capture.CaptureSource
import dev.deftu.screencapper.screenshot.capture.FastCapture
import dev.deftu.screencapper.screenshot.capture.HighResCapture
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

object CaptureInput {
    private const val SETTLE_FRAMES = 2
    private const val TIMER_MS = 3_000L

    private class Frame(val width: Int, val height: Int, val pixels: Supplier<IntArray>, val release: Runnable)

    private val installed = AtomicBoolean(false)
    private val frameHooked = AtomicBoolean(false)
    private val gesture = HoldGesture { ScreencapperConfig.holdThreshold.toLong() }
    private var heldKey = 0
    private var pending: PendingShot<Frame>? = null

    private var timerEndsAt = -1L
        set(value) {
            field = value
            CaptureOverlay.showCountdown(value)
        }

    private var wheelChoices: List<CaptureChoice> = emptyList()

    private var settleFrames = -1
    private var settleAction: (() -> Unit)? = null
    private var restoreHud: Boolean? = null
    private var lastDigit = -1

    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        hookFrames()
    }

    private fun hookFrames() {
        if (frameHooked.compareAndSet(false, true)) {
            EventManager.register(FramebufferRenderEvent.Start::class.java) { _: FramebufferRenderEvent.Start -> onFrame() }
        }
    }

    @JvmStatic
    fun blocksInput(): Boolean = CaptureOverlay.isOpen

    @JvmStatic
    @JvmOverloads
    fun onScreenshotKey(key: Int, holdable: Boolean = true): Boolean {
        if (!FastCapture.isActive()) return false
        hookFrames()
        if (busy()) return true
        if (!holdable || !ScreencapperConfig.holdForMenu) {
            captureNow()
            return true
        }

        pending?.decide(false)
        val metadata = MetadataCollector.collect()
        val file = FastCapture.nextFile(InputBridge.gameDirectory, null)
        val shot = PendingShot<Frame>(
            keep = { frame ->
                FastCapture.save(file, frame.width, frame.height, frame.pixels, frame.release, { error -> CaptureResults.deliver(file, error) }, CaptureKind.NORMAL, metadata)
            },
            drop = { frame -> frame.release.run() },
        )
        pending = shot
        heldKey = key
        gesture.press(System.currentTimeMillis())
        CaptureSource.readMain { width, height, pixels, release -> shot.arrive(Frame(width, height, pixels, release)) }
        return true
    }

    @JvmStatic
    fun onMouseDelta(dx: Double, dy: Double) {
        if (dx == 0.0 && dy == 0.0) return
        CaptureOverlay.steer(dx, dy)
    }

    @JvmStatic
    fun onMouseButton(primary: Boolean) {
        if (!CaptureOverlay.isOpen) return
        if (primary) confirmMenu() else closeMenu()
        gesture.reset()
    }

    private fun busy(): Boolean = CaptureOverlay.isOpen || timerEndsAt >= 0 || settleAction != null || HighResCapture.isCapturing

    private fun onFrame() {
        runCatching { frame() }.onFailure {
            Screencapper.LOGGER.error("Capture input failed", it)
            reset()
        }
    }

    private fun frame() {
        val now = System.currentTimeMillis()
        if (gesture.active) {
            when (gesture.update(now, InputBridge.isKeyDown(heldKey))) {
                HoldGesture.Outcome.TAP -> settlePending(true)
                HoldGesture.Outcome.HOLD -> {
                    val opened = openMenu()
                    settlePending(!opened)
                    if (!opened) gesture.reset()
                }
                HoldGesture.Outcome.RELEASED -> confirmMenu()
                HoldGesture.Outcome.NONE -> {}
            }
        }

        if (CaptureOverlay.isOpen) {
            val digit = InputBridge.digitDown()
            if (digit >= 0 && digit != lastDigit && digit < wheelChoices.size) {
                pickDirectly(digit)
            } else if (InputBridge.escapeDown() || !CaptureOverlay.canStayOpen()) {
                closeMenu()
            }
            lastDigit = digit
        }

        if (timerEndsAt >= 0 && now >= timerEndsAt) {
            timerEndsAt = -1L
            afterSettling(::captureNow)
        }

        if (settleAction != null && settleFrames-- <= 0) {
            val action = settleAction
            settleAction = null
            action?.invoke()
        }
    }

    private fun settlePending(save: Boolean) {
        pending?.decide(save)
        pending = null
    }

    private fun openMenu(): Boolean {
        lastDigit = InputBridge.digitDown()
        val (choices, entries) = wheelEntries().unzip()
        if (!CaptureOverlay.open(entries)) return false
        wheelChoices = choices
        return true
    }

    private fun closeMenu() {
        CaptureOverlay.close(instant = false)
        gesture.reset()
    }

    private fun confirmMenu() {
        val choice = wheelChoices.getOrNull(CaptureOverlay.hovered)
        CaptureOverlay.close(instant = choice != null)
        if (choice != null) run(choice)
    }

    private fun pickDirectly(index: Int) {
        CaptureOverlay.close(instant = true)
        gesture.reset()
        run(wheelChoices[index])
    }

    private fun customSize(): Pair<Int, Int> = ScreencapperConfig.highResWidth.toInt() to ScreencapperConfig.highResHeight.toInt()

    private fun wheelEntries(): List<Pair<CaptureChoice, WheelEntry>> {
        val (customWidth, customHeight) = customSize()
        return listOf(
            CaptureChoice.HIGH_RES_4K to WheelEntry("maximise", "High-res 4K", "3840 \u00d7 2160"),
            CaptureChoice.HIGH_RES_8K to WheelEntry("layers", "High-res 8K", "7680 \u00d7 4320"),
            CaptureChoice.HIGH_RES_CUSTOM to WheelEntry("spanner", "High-res custom", "$customWidth \u00d7 $customHeight"),
            CaptureChoice.NO_HUD to WheelEntry("eye-off", "No HUD", "Hides the HUD for this shot"),
            CaptureChoice.TIMER to WheelEntry("bell", "Timer", "Takes it in 3 seconds"),
        )
    }

    private fun run(choice: CaptureChoice) {
        when (choice) {
            CaptureChoice.HIGH_RES_4K -> highRes(3840, 2160)
            CaptureChoice.HIGH_RES_8K -> highRes(7680, 4320)
            CaptureChoice.HIGH_RES_CUSTOM -> customSize().let { (width, height) -> highRes(width, height) }
            CaptureChoice.NO_HUD -> {
                restoreHud = InputBridge.hudHidden
                InputBridge.hudHidden = true
                afterSettling {
                    captureNow()
                    restoreHud?.let { InputBridge.hudHidden = it }
                    restoreHud = null
                }
            }
            CaptureChoice.TIMER -> timerEndsAt = System.currentTimeMillis() + TIMER_MS
        }
    }

    private fun afterSettling(action: () -> Unit) {
        settleFrames = SETTLE_FRAMES
        settleAction = action
    }

    private fun highRes(width: Int, height: Int) {
        val metadata = MetadataCollector.collect()
        HighResCapture.capture(width, height, ScreencapperConfig.highResHideHud) { image ->
            if (image == null) {
                ScreenshotActions.notifyError("Couldn't take high-res screenshot", "The capture failed or was cancelled.")
                return@capture
            }
            val file = FastCapture.nextFile(InputBridge.gameDirectory, null, CaptureKind.HIGH_RES, HighResCapture.fileName())
            FastCapture.saveImage(file, image, CaptureKind.HIGH_RES, metadata) { error -> CaptureResults.deliver(file, error) }
        }
    }

    private fun captureNow() {
        val metadata = MetadataCollector.collect()
        val file = FastCapture.nextFile(InputBridge.gameDirectory, null)
        CaptureSource.readMain { width, height, pixels, release ->
            FastCapture.save(file, width, height, pixels, release, { error -> CaptureResults.deliver(file, error) }, CaptureKind.NORMAL, metadata)
        }
    }

    private fun reset() {
        settlePending(false)
        gesture.reset()
        CaptureOverlay.close(instant = true)
        timerEndsAt = -1L
        settleAction = null
        restoreHud?.let { runCatching { InputBridge.hudHidden = it } }
        restoreHud = null
    }
}

internal enum class CaptureChoice {
    HIGH_RES_4K,
    HIGH_RES_8K,
    HIGH_RES_CUSTOM,
    NO_HUD,
    TIMER,
}
