package dev.deftu.screencapper.screenshot.capture.highres

import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.screenshot.capture.CaptureSource
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import dev.deftu.screencapper.screenshot.capture.FastCapture
import net.minecraft.client.Minecraft
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

//? if >=1.21.5 {
import com.mojang.blaze3d.systems.RenderSystem
//?} else if >=1.21 {
/*import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import dev.deftu.screencapper.mixin.highres.HighResWindowAccessor
import net.minecraft.client.gui.GuiGraphics
import org.joml.Matrix4f
*///?} else {
/*import net.minecraft.client.render.Window
import net.minecraft.client.render.platform.GlStateManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
*///?}
//? if >=1.21.4 && <1.21.5 {
/*import com.mojang.blaze3d.ProjectionType
*///?} else if >=1.21 && <1.21.4 {
/*import com.mojang.blaze3d.vertex.VertexSorting
*///?}

// Based on Fabrishot by ramidzkh (MIT)
object HighResRenderer {
    private const val MAX_TILED_SIDE = 16384
    private const val READ_TIMEOUT_FRAMES = 120

    private class Job(
        val width: Int,
        val height: Int,
        val hideHud: Boolean,
        val delay: Int,
        val onDone: (CapturedImage?) -> Unit,
    ) {
        var frames = 0
        var hudWasHidden = false
        var oldWidth = 0
        var oldHeight = 0
        var reading = false
        var readFrames = 0
        var restored = false
        var completed = false
    }

    private var job: Job? = null

    @JvmStatic
    var guiScaleOverride = 0
        private set

    @JvmStatic
    var tile: TileProjection? = null
        private set

    @JvmStatic
    var tiledAspect = 0f
        private set

    @JvmStatic
    val isCapturing: Boolean
        get() = job != null

    @JvmStatic
    val usesTiling: Boolean
        get() {
            //? if >=1.21.5 {
            return false
            //?} else {
            /*return true
            *///?}
        }

    @JvmStatic
    fun start(width: Int, height: Int, hideHud: Boolean, delay: Int, onDone: (CapturedImage?) -> Unit) {
        if (job != null || width <= 0 || height <= 0) {
            onDone(null)
            return
        }

        val size = clamp(width, height)
        val job = Job(size.first, size.second, hideHud, max(1, delay), onDone)
        this.job = job
        try {
            job.hudWasHidden = isHudHidden()
            if (job.hideHud) setHudHidden(true)
            if (!usesTiling) resize(job)
        } catch (e: Throwable) {
            fail(job, e)
        }
    }

    @JvmStatic
    fun afterFrame() {
        val job = job ?: return
        try {
            if (job.reading) {
                if (++job.readFrames > READ_TIMEOUT_FRAMES) throw IllegalStateException("The framebuffer read never finished")
                return
            }
            if (job.frames++ < job.delay) return

            job.reading = true
            CaptureSource.readMain { width, height, pixels, release ->
                //? if <1.21.6 {
                /*MainThread.run { restore(job) }
                *///?}
                deliver(job, width, height, pixels, release)
            }
            //? if >=1.21.6 {
            restore(job)
            //?}
        } catch (e: Throwable) {
            fail(job, e)
        }
    }

    @JvmStatic
    fun beforeFrame(partialTick: Float) {
        val job = job ?: return
        if (job.frames++ < job.delay) return

        val tiles = ArrayList<ReadTile>()
        try {
            check(canRenderLevel()) { "There's no level to render" }
            val tileWidth = framebufferWidth()
            val tileHeight = framebufferHeight()
            val grid = TileGrid(job.width, job.height, tileWidth, tileHeight)
            tiledAspect = grid.aspect
            val hud = !isHudHidden()
            for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
                tile = grid.projection(column, row)
                renderLevel(tileWidth, tileHeight, partialTick)
                if (hud) renderHud(job, partialTick)
                CaptureSource.readMain { width, height, pixels, release -> tiles += ReadTile(column, row, width, height, pixels, release) }
            }
            tile = null
            tiledAspect = 0f
            restore(job)
            stitch(job, grid, tiles)
        } catch (e: Throwable) {
            tiles.forEach { it.release.run() }
            fail(job, e)
        } finally {
            tile = null
            tiledAspect = 0f
        }
    }

    @JvmStatic
    fun guiScale(calculated: Int): Int = if (guiScaleOverride > 0) guiScaleOverride else calculated

    //? if >=1.21 && <1.21.5 {
    /*@JvmStatic
    fun applyTile(projection: Matrix4f): Matrix4f {
        val tile = tile ?: return projection
        return projection.mulLocal(Matrix4f().translation(tile.offsetX, tile.offsetY, 0f).scale(tile.scaleX, tile.scaleY, 1f))
    }
    *///?}

    //? if <1.21 {
    /*private val matrix = BufferUtils.createFloatBuffer(16)

    @JvmStatic
    fun multiplyTile() {
        val tile = tile ?: return
        matrix.clear()
        matrix.put(floatArrayOf(tile.scaleX, 0f, 0f, 0f, 0f, tile.scaleY, 0f, 0f, 0f, 0f, 1f, 0f, tile.offsetX, tile.offsetY, 0f, 1f))
        matrix.flip()
        GL11.glMultMatrix(matrix)
    }

    private var hudPass = false

    @JvmStatic
    fun multiplyHudTile() {
        if (hudPass) multiplyTile()
    }

    @JvmStatic
    fun prependTileToProjection() {
        val tile = tile ?: return
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        matrix.clear()
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrix)
        val m = FloatArray(16)
        matrix.get(m)
        for (column in 0 until 4) {
            val w = m[column * 4 + 3]
            m[column * 4] = tile.scaleX * m[column * 4] + tile.offsetX * w
            m[column * 4 + 1] = tile.scaleY * m[column * 4 + 1] + tile.offsetY * w
        }
        matrix.clear()
        matrix.put(m)
        matrix.flip()
        GL11.glLoadMatrix(matrix)
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
    }
    *///?}

    private class ReadTile(val column: Int, val row: Int, val width: Int, val height: Int, val pixels: Supplier<IntArray>, val release: Runnable)

    private fun stitch(job: Job, grid: TileGrid, tiles: List<ReadTile>) {
        FastCapture.encoderPool.execute {
            val image = runCatching {
                val out = IntArray(grid.width * grid.height)
                for (tile in tiles) {
                    try {
                        check(tile.width >= grid.tileWidth && tile.height >= grid.tileHeight) { "A tile came back smaller than the window" }
                        grid.stitch(out, tile.pixels.get(), tile.width, tile.column, tile.row, false)
                    } finally {
                        tile.release.run()
                    }
                }
                CapturedImage(out, grid.width, grid.height)
            }.onFailure { Screencapper.LOGGER.error("Couldn't stitch the high-res screenshot", it) }.getOrNull()
            MainThread.run { complete(job, image) }
        }
    }

    private fun deliver(job: Job, width: Int, height: Int, pixels: Supplier<IntArray>, release: Runnable) {
        FastCapture.encoderPool.execute {
            val image = runCatching {
                try {
                    CapturedImage(pixels.get(), width, height)
                } finally {
                    release.run()
                }
            }.onFailure { Screencapper.LOGGER.error("Couldn't read the high-res screenshot", it) }.getOrNull()
            MainThread.run { complete(job, image) }
        }
    }

    private fun fail(job: Job, error: Throwable) {
        Screencapper.LOGGER.error("High-res capture failed", error)
        runCatching { restore(job) }.onFailure { Screencapper.LOGGER.error("Couldn't restore the window after a high-res capture", it) }
        complete(job, null)
    }

    private fun complete(job: Job, image: CapturedImage?) {
        if (job.completed) return
        job.completed = true
        job.onDone(image)
    }

    private fun restore(job: Job) {
        if (job.restored) return
        job.restored = true
        try {
            guiScaleOverride = 0
            if (job.oldWidth > 0) setFramebufferSize(job.oldWidth, job.oldHeight)
        } finally {
            if (job.hideHud) setHudHidden(job.hudWasHidden)
            if (this.job === job) this.job = null
        }
    }

    private fun resize(job: Job) {
        //? if >=1.21.5 {
        val window = Minecraft.getInstance().window
        job.oldWidth = window.width
        job.oldHeight = window.height
        guiScaleOverride = scaledGuiScale(window.guiScale.toDouble(), job.oldWidth, job.oldHeight, job.width, job.height)
        setFramebufferSize(job.width, job.height)
        //?}
    }

    private fun scaledGuiScale(scale: Double, oldWidth: Int, oldHeight: Int, newWidth: Int, newHeight: Int): Int =
        max(1, (scale * min(newWidth.toFloat() / oldWidth, newHeight.toFloat() / oldHeight)).roundToInt())

    @Suppress("UNUSED_PARAMETER")
    private fun renderHud(job: Job, partialTick: Float) {
        //? if >=1.21 && <1.21.5 {
        /*val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val access = window as HighResWindowAccessor
        val oldWidth = window.width
        val oldHeight = window.height
        val oldScale = window.guiScale
        val screen = minecraft.screen
        try {
            access.`screencapper$setFramebufferWidth`(job.width)
            access.`screencapper$setFramebufferHeight`(job.height)
            window.setGuiScale(scaledGuiScale(oldScale, oldWidth, oldHeight, job.width, job.height).toDouble())
            minecraft.screen = null
            val ortho = Matrix4f().setOrtho(0f, (window.width / window.guiScale).toFloat(), (window.height / window.guiScale).toFloat(), 0f, 1000f, 21000f)
            //? if >=1.21.4 {
            /^RenderSystem.clear(256)
            RenderSystem.setProjectionMatrix(applyTile(ortho), ProjectionType.ORTHOGRAPHIC)
            ^///?} else {
            /^RenderSystem.clear(256, Minecraft.ON_OSX)
            RenderSystem.setProjectionMatrix(applyTile(ortho), VertexSorting.ORTHOGRAPHIC_Z)
            ^///?}
            val stack = RenderSystem.getModelViewStack()
            stack.pushMatrix()
            try {
                stack.translation(0f, 0f, -11000f)
                //? if <1.21.4 {
                /^RenderSystem.applyModelViewMatrix()
                ^///?}
                Lighting.setupFor3DItems()
                val graphics = GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource())
                //? if >=1.21.4 {
                /^minecraft.gui.render(graphics, minecraft.deltaTracker)
                ^///?} else {
                /^minecraft.gui.render(graphics, minecraft.timer)
                ^///?}
                graphics.flush()
            } finally {
                stack.popMatrix()
                //? if <1.21.4 {
                /^RenderSystem.applyModelViewMatrix()
                ^///?}
            }
        } finally {
            minecraft.screen = screen
            access.`screencapper$setFramebufferWidth`(oldWidth)
            access.`screencapper$setFramebufferHeight`(oldHeight)
            window.setGuiScale(oldScale)
        }
        *///?} else if <1.21 {
        /*val minecraft = Minecraft.getInstance()
        val oldWidth = minecraft.width
        val oldHeight = minecraft.height
        val oldGuiScale = minecraft.options.guiScale
        val screen = minecraft.screen
        val scale = Window(minecraft).scale
        try {
            minecraft.width = job.width
            minecraft.height = job.height
            minecraft.options.guiScale = scaledGuiScale(scale.toDouble(), oldWidth, oldHeight, job.width, job.height)
            minecraft.screen = null
            hudPass = true
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f)
            minecraft.gui.render(partialTick)
        } finally {
            hudPass = false
            minecraft.screen = screen
            minecraft.width = oldWidth
            minecraft.height = oldHeight
            minecraft.options.guiScale = oldGuiScale
        }
        *///?}
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setFramebufferSize(width: Int, height: Int) {
        //? if >=26.2 {
        val minecraft = Minecraft.getInstance()
        minecraft.window.width = width
        minecraft.window.height = height
        minecraft.resizeGui()
        //?} else if >=26.1 {
        /*val minecraft = Minecraft.getInstance()
        minecraft.window.width = width
        minecraft.window.height = height
        minecraft.resizeGui()
        minecraft.gameRenderer.resize(width, height)
        *///?} else if >=1.21.5 {
        /*val minecraft = Minecraft.getInstance()
        minecraft.window.width = width
        minecraft.window.height = height
        minecraft.resizeDisplay()
        *///?}
    }

    private fun clamp(width: Int, height: Int): Pair<Int, Int> {
        //? if >=26.2 {
        val limit = RenderSystem.getDevice().deviceInfo.limits().maxTextureSize()
        //?} else if >=1.21.5 {
        /*val limit = RenderSystem.getDevice().maxTextureSize
        *///?} else {
        /*val limit = MAX_TILED_SIDE
        *///?}
        if (width <= limit && height <= limit) return width to height
        val scale = limit.toDouble() / max(width, height)
        val clamped = max(1, (width * scale).toInt()) to max(1, (height * scale).toInt())
        Screencapper.LOGGER.warn("High-res capture of {}x{} is larger than the {} pixel limit, capturing {}x{} instead", width, height, limit, clamped.first, clamped.second)
        return clamped
    }

    private fun isHudHidden(): Boolean {
        val minecraft = Minecraft.getInstance()
        //? if >=26.2 {
        return minecraft.gui.hud.isHidden
        //?} else {
        /*return minecraft.options.hideGui
        *///?}
    }

    private fun setHudHidden(hidden: Boolean) {
        val minecraft = Minecraft.getInstance()
        //? if >=26.2 {
        if (minecraft.gui.hud.isHidden != hidden) minecraft.gui.hud.toggle()
        //?} else {
        /*minecraft.options.hideGui = hidden
        *///?}
    }

    private fun framebufferWidth(): Int {
        //? if >=1.21 {
        return Minecraft.getInstance().window.width
        //?} else {
        /*return Minecraft.getInstance().width
        *///?}
    }

    private fun framebufferHeight(): Int {
        //? if >=1.21 {
        return Minecraft.getInstance().window.height
        //?} else {
        /*return Minecraft.getInstance().height
        *///?}
    }

    private fun canRenderLevel(): Boolean {
        val minecraft = Minecraft.getInstance()
        //? if >=1.21 {
        return minecraft.level != null && minecraft.player != null && minecraft.isGameLoadFinished
        //?} else {
        /*return minecraft.world != null && minecraft.getCamera() != null
        *///?}
    }

    @Suppress("UNUSED_PARAMETER")
    private fun renderLevel(width: Int, height: Int, partialTick: Float) {
        //? if >=1.21.4 && <1.21.5 {
        /*val minecraft = Minecraft.getInstance()
        RenderSystem.viewport(0, 0, width, height)
        minecraft.gameRenderer.renderLevel(minecraft.deltaTracker)
        minecraft.levelRenderer.doEntityOutline()
        minecraft.mainRenderTarget.bindWrite(true)
        *///?} else if >=1.21 && <1.21.4 {
        /*val minecraft = Minecraft.getInstance()
        RenderSystem.viewport(0, 0, width, height)
        minecraft.gameRenderer.renderLevel(minecraft.timer)
        minecraft.levelRenderer.doEntityOutline()
        minecraft.mainRenderTarget.bindWrite(true)
        *///?} else if <1.21 {
        /*Minecraft.getInstance().gameRenderer.renderWorld(partialTick, System.nanoTime())
        *///?}
    }

    @JvmStatic
    fun worldName(): String? {
        val minecraft = Minecraft.getInstance()
        //? if >=1.21 {
        minecraft.singleplayerServer?.let { return it.worldData.levelName }
        return minecraft.currentServer?.name
        //?} else {
        /*minecraft.getServer()?.let { return it.getWorldName() }
        return minecraft.getCurrentServerEntry()?.name
        *///?}
    }
}
