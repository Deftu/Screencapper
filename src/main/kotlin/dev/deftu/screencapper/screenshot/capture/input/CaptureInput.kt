@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.screenshot.capture.input

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.ShutterSound
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.wheel.CaptureOverlay
import dev.deftu.screencapper.gui.wheel.WheelClick
import dev.deftu.screencapper.gui.wheel.WheelOption
import dev.deftu.screencapper.gui.wheel.WheelOptions
import dev.deftu.screencapper.gui.wheel.WheelShot
import dev.deftu.screencapper.metadata.MetadataCollector
import dev.deftu.screencapper.screenshot.capture.ArgentumFramebuffer
import dev.deftu.screencapper.screenshot.capture.CaptureKind
import dev.deftu.screencapper.screenshot.capture.CaptureResults
import dev.deftu.screencapper.screenshot.capture.CaptureSource
import dev.deftu.screencapper.screenshot.capture.FastCapture
import dev.deftu.screencapper.screenshot.capture.HighResCapture
import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfile
import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfiles
import dev.deftu.screencapper.screenshot.panorama.TitlePanorama
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuItem
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

object CaptureInput {
    private const val SETTLE_FRAMES = 2
    private const val NO_KEY = Int.MIN_VALUE

    private class Frame(val width: Int, val height: Int, val pixels: Supplier<IntArray>, val release: Runnable)

    private val installed = AtomicBoolean(false)
    private val frameHooked = AtomicBoolean(false)
    private val gesture = HoldGesture { ScreencapperConfig.holdThreshold.toLong() }
    private var heldKey = 0
    private var pending: PendingShot<Frame>? = null
    private val frameReads = ArrayList<CaptureSource.Sink>()

    private var timerEndsAt = -1L
        set(value) {
            field = value
            CaptureOverlay.showCountdown(value)
        }

    private var timerOption: WheelOption? = null

    private var wheelChoices: List<WheelOption> = emptyList()

    private var settleFrames = -1
    private var settleAction: (() -> Unit)? = null
    private var restoreHud: Boolean? = null

    private var swallowedKey = NO_KEY

    @JvmStatic
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        hookFrames()
        TitlePanorama.install()
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
        if (gesture.active && key == heldKey) return true
        if (!holdable || !ScreencapperConfig.holdForMenu) {
            captureNow(read = ::readFrame)
            return true
        }

        pending?.decide(false)
        val metadata = MetadataCollector.collect()
        val file = FastCapture.nextFile(InputBridge.gameDirectory, null)
        val shot = PendingShot<Frame>(
            keep = { frame ->
                ShutterSound.play()
                FastCapture.save(file, frame.width, frame.height, frame.pixels, frame.release, { error -> CaptureResults.deliver(file, error) }, CaptureKind.NORMAL, metadata)
            },
            drop = { frame ->
                frame.release.run()
                FastCapture.release(file)
            },
        )
        pending = shot
        heldKey = key
        gesture.press(System.currentTimeMillis())
        readFrame { width, height, pixels, release -> shot.arrive(Frame(width, height, pixels, release)) }
        return true
    }

    private fun readFrame(sink: CaptureSource.Sink) {
        //? if <1.21 {
        /*frameReads += sink
        *///?} else {
        CaptureSource.readMain(sink)
        //?}
    }

    private fun drainFrameReads() {
        if (frameReads.isEmpty()) return
        val reads = frameReads.toList()
        frameReads.clear()
        reads.forEach(CaptureSource::readMain)
    }

    @JvmStatic
    fun onMouseDelta(dx: Double, dy: Double) {
        if (dx == 0.0 && dy == 0.0) return
        CaptureOverlay.steer(dx, dy)
    }

    @JvmStatic
    fun onMouseButton(button: Int) {
        if (!CaptureOverlay.isOpen) return
        when (InputBridge.wheelClick(button)) {
            WheelClick.PICK -> confirmMenu()
            WheelClick.CANCEL -> closeMenu()
            WheelClick.NONE -> return
        }
        gesture.reset()
    }

    @JvmStatic
    fun onKey(key: Int, action: Int): Boolean {
        if (action == 0) {
            if (key == swallowedKey) swallowedKey = NO_KEY
            return false
        }
        if (key == swallowedKey) return true
        if (!CaptureOverlay.isOpen) return false
        if (action == 1) {
            val digit = InputBridge.digitOf(key)
            if (InputBridge.isEscape(key)) {
                closeMenu()
                swallowedKey = key
            } else if (digit in wheelChoices.indices) {
                pickDirectly(digit)
                swallowedKey = key
            }
        }
        return true
    }

    private fun busy(): Boolean = CaptureOverlay.isOpen || timerEndsAt >= 0 || settleAction != null || HighResCapture.isCapturing

    private fun onFrame() {
        runCatching {
            drainFrameReads()
            frame()
            if (busy()) ArgentumFramebuffer.request()
        }.onFailure {
            Screencapper.LOGGER.error("Capture input failed", it)
            reset()
        }
    }

    private fun frame() {
        val now = System.currentTimeMillis()
        if (CaptureOverlay.isOpen && !CaptureOverlay.canStayOpen()) closeMenu()

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

        if (timerEndsAt >= 0 && now >= timerEndsAt) {
            timerEndsAt = -1L
            timerOption?.let(::shoot)
            timerOption = null
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
        val choices = WheelOptions.decode(ScreencapperConfig.wheelOptions)
        val (customWidth, customHeight) = customSize()
        val entries = choices.map { option -> option.slot(customWidth, customHeight).let { RadialMenuItem(it.label, it.icon, it.detail) } }
        if (!CaptureOverlay.open(entries)) return false
        wheelChoices = choices
        return true
    }

    private fun closeMenu() {
        CaptureOverlay.dismiss(instant = false)
        gesture.reset()
    }

    private fun confirmMenu() {
        val choice = wheelChoices.getOrNull(CaptureOverlay.confirm())
        if (choice != null) run(choice)
    }

    private fun pickDirectly(index: Int) {
        CaptureOverlay.pick(index)
        gesture.reset()
        run(wheelChoices[index])
    }

    private fun customSize(): Pair<Int, Int> = ScreencapperConfig.highResWidth.toInt() to ScreencapperConfig.highResHeight.toInt()

    private fun run(option: WheelOption) {
        when (val shot = option.shot) {
            is WheelShot.HighRes -> customSize().let { (customWidth, customHeight) -> shot.size(customWidth, customHeight) }.let { (width, height) -> highRes(width, height, option) }
            is WheelShot.Timer -> {
                timerOption = option
                timerEndsAt = System.currentTimeMillis() + shot.seconds * 1_000L
            }
            WheelShot.Normal -> shoot(option)
            is WheelShot.Panorama -> panorama(shot.faceSize)
        }
    }

    private fun shoot(option: WheelOption) {
        hide(option)
        afterSettling {
            try {
                captureNow(option)
            } finally {
                unhide()
            }
        }
    }

    private fun hide(option: WheelOption) {
        if (option.hideHud) {
            if (restoreHud == null) restoreHud = InputBridge.hudHidden
            InputBridge.hudHidden = true
        }
        HiddenLayers.hide(option.hideChat, option.hideHand)
    }

    private fun unhide() {
        HiddenLayers.clear()
        restoreHud?.let { runCatching { InputBridge.hudHidden = it } }
        restoreHud = null
    }

    private fun afterSettling(action: () -> Unit) {
        settleFrames = SETTLE_FRAMES
        settleAction = action
    }

    private fun profileOf(option: WheelOption?, kind: CaptureKind): CompressionProfile? =
        option?.compression?.let { CompressionProfiles.forKind(kind, it) }

    private fun fileFor(kind: CaptureKind, profile: CompressionProfile?, prefix: String?): File {
        return FastCapture.nextFile(InputBridge.gameDirectory, null, kind, prefix, profile?.format?.extension)
    }

    private fun highRes(width: Int, height: Int, option: WheelOption) {
        val metadata = MetadataCollector.collect()
        val profile = profileOf(option, CaptureKind.HIGH_RES)
        HighResCapture.capture(width, height, option.hideHud, option.hideChat, option.hideHand) { image ->
            if (image == null) {
                ScreenshotActions.notifyError("Couldn't take high-res screenshot", "The capture failed or was cancelled.")
                return@capture
            }
            val file = fileFor(CaptureKind.HIGH_RES, profile, HighResCapture.fileName())
            FastCapture.save(file, image.width, image.height, { image.argb }, {}, { error -> CaptureResults.deliver(file, error) }, CaptureKind.HIGH_RES, metadata, profile)
        }
    }

    private fun panorama(faceSize: Int) {
        if (!PanoramaShot.start(faceSize)) ScreenshotActions.notifyError("Couldn't take panorama", "Another capture is still running.")
    }

    private fun captureNow(option: WheelOption? = null, read: (CaptureSource.Sink) -> Unit = CaptureSource::readMain) {
        val metadata = MetadataCollector.collect()
        val profile = profileOf(option, CaptureKind.NORMAL)
        val file = fileFor(CaptureKind.NORMAL, profile, null)
        ShutterSound.play()
        read(CaptureSource.Sink { width, height, pixels, release ->
            FastCapture.save(file, width, height, pixels, release, { error -> CaptureResults.deliver(file, error) }, CaptureKind.NORMAL, metadata, profile)
        })
    }

    private fun reset() {
        settlePending(false)
        gesture.reset()
        CaptureOverlay.dismiss(instant = true)
        timerEndsAt = -1L
        timerOption = null
        settleAction = null
        unhide()
    }
}
