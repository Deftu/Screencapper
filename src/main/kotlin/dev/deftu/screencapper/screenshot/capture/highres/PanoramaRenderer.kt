package dev.deftu.screencapper.screenshot.capture.highres

import dev.deftu.screencapper.screenshot.capture.CaptureSource
import dev.deftu.screencapper.screenshot.capture.input.HiddenLayers
import dev.deftu.screencapper.screenshot.panorama.PanoramaLayout
import net.minecraft.client.Minecraft
import java.util.function.Supplier
import kotlin.math.min

//? if >=1.21 {
import net.minecraft.client.CameraType
//?}
//? if >=1.21 && <1.21.5 {
/*import com.mojang.blaze3d.systems.RenderSystem
*///?}
//? if <1.21.5 {
/*import dev.deftu.screencapper.MainThread
*///?}
//? if >=1.21.5 {
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.DeltaTracker
//?}
//? if >=1.21.11 && <26.1 {
/*import net.minecraft.client.renderer.PanoramicScreenshotParameters
import org.joml.Vector3f
*///?}

object PanoramaRenderer {
    fun interface FaceSink {
        fun accept(face: Int, width: Int, height: Int, pixels: Supplier<IntArray>, release: Runnable)
    }

    private class Request(val faceSize: Int, val maxSupersample: Int, val urgent: Boolean, val sink: FaceSink, val onRendered: (Throwable?) -> Unit)

    private class View(val yaw: Float, val pitch: Float, val yawO: Float, val pitchO: Float)

    private var pending: Request? = null
    private var rendering = false

    @JvmStatic
    var active = false
        private set

    @JvmStatic
    val isCapturing: Boolean
        get() = pending != null || rendering

    fun start(faceSize: Int, sink: FaceSink, onRendered: (Throwable?) -> Unit): Boolean {
        if (!canStart(faceSize)) return false
        pending = Request(faceSize, Int.MAX_VALUE, false, sink, onRendered)
        return true
    }

    fun renderNow(faceSize: Int, maxSupersample: Int, sink: FaceSink, onRendered: (Throwable?) -> Unit): Boolean {
        if (!canStart(faceSize)) return false
        renderRequest(Request(faceSize, maxSupersample, true, sink, onRendered), 1f, true)
        return true
    }

    @JvmStatic
    fun beforeFrame(partialTick: Float) {
        if (rendering) return
        val request = pending ?: return
        pending = null
        renderRequest(request, partialTick, false)
    }

    private fun canStart(faceSize: Int): Boolean = !isCapturing && !HighResRenderer.isCapturing && faceSize > 0

    private fun renderRequest(request: Request, partialTick: Float, outsideFrame: Boolean) {
        rendering = true
        val error = try {
            if (outsideFrame) bindMainTarget(true)
            try {
                render(request, partialTick)
            } finally {
                if (outsideFrame) bindMainTarget(false)
            }
            null
        } catch (e: Throwable) {
            e
        } finally {
            rendering = false
        }
        request.onRendered(error)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun bindMainTarget(bind: Boolean) {
        //? if >=1.21 && <1.21.5 {
        /*val target = Minecraft.getInstance().mainRenderTarget
        if (bind) target.bindWrite(true) else target.unbindWrite()
        *///?} else if <1.21 {
        /*val target = Minecraft.getInstance().renderTarget
        if (bind) target.bindWrite(true) else target.unbindWrite()
        *///?}
    }

    private fun render(request: Request, partialTick: Float) {
        check(HighResRenderer.canRenderLevel()) { "There's no level to render" }
        checkNotNull(Minecraft.getInstance().player) { "There's no player to look from" }
        val size = min(request.faceSize, HighResRenderer.maxTextureSize())
        val view = currentView()
        val restoreCamera = firstPerson()
        try {
            HiddenLayers.hide(false, true)
            setBlockOutline(false)
            active = true
            renderFaces(request, size, view, partialTick)
        } finally {
            active = false
            setBlockOutline(true)
            HiddenLayers.clear()
            restoreCamera()
            restoreView(view)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun renderFaces(request: Request, size: Int, view: View, partialTick: Float) {
        //? if >=1.21.5 {
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val target = mainTarget()
        val oldWidth = window.width
        val oldHeight = window.height
        val factor = min(PanoramaLayout.supersample(size, HighResRenderer.maxTextureSize(), canDownscale()), request.maxSupersample)
        val side = size * factor
        try {
            enablePanoramicMode()
            window.width = side
            window.height = side
            target.resize(side, side)
            for (face in PanoramaLayout.FACES) {
                look(face.yaw(view.yaw), face.pitch)
                renderFace()
                CaptureSource.read(target, factor) { width, height, pixels, release -> request.sink.accept(face.index, width, height, pixels, release) }
            }
        } finally {
            window.width = oldWidth
            window.height = oldHeight
            target.resize(oldWidth, oldHeight)
            disablePanoramicMode()
        }
        //?} else {
        /*for (face in PanoramaLayout.FACES) {
            look(face.yaw(view.yaw), face.pitch)
            HighResRenderer.renderTiled(size, size, partialTick, handBack(request)) { image ->
                if (image == null) request.onRendered(IllegalStateException("A panorama face couldn't be stitched"))
                else request.sink.accept(face.index, image.width, image.height, { image.argb }, {})
            }
        }
        *///?}
    }

    //? if >=1.21.5 {
    private fun canDownscale(): Boolean {
        //? if >=1.21.8 {
        return true
        //?} else {
        /*return false
        *///?}
    }

    private fun mainTarget(): RenderTarget {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget()
        //?} else {
        /*return Minecraft.getInstance().mainRenderTarget
        *///?}
    }

    private fun enablePanoramicMode() {
        val renderer = Minecraft.getInstance().gameRenderer
        //? if >=26.2 {
        renderer.mainCamera().enablePanoramicMode()
        //?} else if >=26.1 {
        /*renderer.mainCamera.enablePanoramicMode()
        *///?} else if >=1.21.11 {
        /*renderer.setPanoramicScreenshotParameters(PanoramicScreenshotParameters(Vector3f(renderer.mainCamera.forwardVector())))
        *///?} else {
        /*renderer.setPanoramicMode(true)
        *///?}
    }

    private fun disablePanoramicMode() {
        val renderer = Minecraft.getInstance().gameRenderer
        //? if >=26.2 {
        renderer.mainCamera().disablePanoramicMode()
        //?} else if >=26.1 {
        /*renderer.mainCamera.disablePanoramicMode()
        *///?} else if >=1.21.11 {
        /*renderer.setPanoramicScreenshotParameters(null)
        *///?} else {
        /*renderer.setPanoramicMode(false)
        *///?}
    }

    private fun renderFace() {
        val renderer = Minecraft.getInstance().gameRenderer
        //? if >=26.3 {
        renderer.update(DeltaTracker.ONE)
        renderer.extract(DeltaTracker.ONE, true)
        renderer.renderLevel()
        //?} else if >=26.2 {
        /*renderer.update(DeltaTracker.ONE)
        renderer.renderLevel(DeltaTracker.ONE)
        *///?} else if >=26.1 {
        /*renderer.update(DeltaTracker.ONE, true)
        renderer.extract(DeltaTracker.ONE, true)
        renderer.renderLevel(DeltaTracker.ONE)
        *///?} else if >=1.21.11 {
        /*renderer.updateCamera(DeltaTracker.ONE)
        renderer.renderLevel(DeltaTracker.ONE)
        *///?} else {
        /*renderer.renderLevel(DeltaTracker.ONE)
        *///?}
    }
    //?}

    //? if >=1.21 && <1.21.5 {
    /*private fun handBack(request: Request): (Runnable) -> Unit {
        if (!request.urgent) return MainThread::run
        return { action -> RenderSystem.recordRenderCall { action.run() } }
    }
    *///?} else if <1.21 {
    /*@Suppress("UNUSED_PARAMETER")
    private fun handBack(request: Request): (Runnable) -> Unit = MainThread::run
    *///?}

    //? if >=1.21 {
    private fun currentView(): View {
        val player = Minecraft.getInstance().player!!
        return View(player.yRot, player.xRot, player.yRotO, player.xRotO)
    }

    private fun look(yaw: Float, pitch: Float) {
        val player = Minecraft.getInstance().player ?: return
        player.yRot = yaw
        player.xRot = pitch
        player.yRotO = yaw
        player.xRotO = pitch
    }

    private fun restoreView(view: View) {
        val player = Minecraft.getInstance().player ?: return
        player.yRot = view.yaw
        player.xRot = view.pitch
        player.yRotO = view.yawO
        player.xRotO = view.pitchO
    }

    private fun firstPerson(): () -> Unit {
        val options = Minecraft.getInstance().options
        val previous = options.cameraType
        options.cameraType = CameraType.FIRST_PERSON
        return { options.cameraType = previous }
    }

    private fun setBlockOutline(render: Boolean) {
        Minecraft.getInstance().gameRenderer.setRenderBlockOutline(render)
    }
    //?} else {
    /*private fun currentView(): View {
        val player = Minecraft.getInstance().player!!
        return View(player.yaw, player.pitch, player.lastYaw, player.lastPitch)
    }

    private fun look(yaw: Float, pitch: Float) {
        val player = Minecraft.getInstance().player ?: return
        player.yaw = yaw
        player.pitch = pitch
        player.lastYaw = yaw
        player.lastPitch = pitch
    }

    private fun restoreView(view: View) {
        val player = Minecraft.getInstance().player ?: return
        player.yaw = view.yaw
        player.pitch = view.pitch
        player.lastYaw = view.yawO
        player.lastPitch = view.pitchO
    }

    private fun firstPerson(): () -> Unit {
        val options = Minecraft.getInstance().options
        val previous = options.perspective
        options.perspective = 0
        return { options.perspective = previous }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setBlockOutline(render: Boolean) {}
    *///?}
}
