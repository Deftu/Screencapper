package dev.deftu.screencapper.screenshot.capture

import net.minecraft.client.Minecraft
import java.util.function.Supplier

//? if >=1.21 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Screenshot
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
*///?}

/**
 * Reads framebuffer pixels for the capture pipeline.
 *
 * On 1.21.5 and newer this goes through vanilla's device-neutral screenshot code, so it works on every render backend
 * (including Vulkan on 26.2+). Only the copy happens on the render thread; the pixels are converted when the sink asks
 * for them, on a background thread.
 */
object CaptureSource {
    /** Receives a capture: [pixels] returns width x height ARGB, top row first; [release] frees the native copy */
    fun interface Sink {
        fun accept(width: Int, height: Int, pixels: Supplier<IntArray>, release: Runnable)
    }

    /** Reads the main framebuffer as it is now. Call on the render thread, after a frame has been drawn */
    @JvmStatic
    fun readMain(sink: Sink) {
        val minecraft = Minecraft.getInstance()
        //? if >=26.2 {
        read(minecraft.gameRenderer.mainRenderTarget(), 1, sink)
        //?} else if >=1.21 {
        /*read(minecraft.mainRenderTarget, 1, sink)
        *///?} else {
        /*read(minecraft.renderTarget, minecraft.width, minecraft.height, sink)
        *///?}
    }

    //? if >=1.21 {
    /** [downscale] averages blocks of pixels; it must be 1 before 1.21.8 */
    @JvmStatic
    fun read(target: RenderTarget, downscale: Int, sink: Sink) {
        //? if >=1.21.8 {
        Screenshot.takeScreenshot(target, downscale) { image -> deliver(image, sink) }
        //?} else if >=1.21.5 {
        /*require(downscale == 1) { "Downscaled captures need 1.21.8 or newer" }
        Screenshot.takeScreenshot(target) { image -> deliver(image, sink) }
        *///?} else {
        /*require(downscale == 1) { "Downscaled captures need 1.21.8 or newer" }
        deliver(Screenshot.takeScreenshot(target), sink)
        *///?}
    }

    private fun deliver(image: NativeImage, sink: Sink) {
        sink.accept(image.width, image.height, { image.makePixelArray() }, { image.close() })
    }
    //?} else {
    /*/^* [width] and [height] are the window size, used only when framebuffers are unsupported *^/
    @JvmStatic
    fun read(target: RenderTarget, width: Int, height: Int, sink: Sink) {
        val fbo = GLX.useFbo()
        val readWidth = if (fbo) target.width else width
        val readHeight = if (fbo) target.height else height
        val outWidth = if (fbo) target.viewWidth else width
        val outHeight = if (fbo) target.viewHeight else height

        val buffer = BufferUtils.createIntBuffer(readWidth * readHeight)
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
        if (fbo) {
            GlStateManager.bindTexture(target.colorTextureId)
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer)
        } else {
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer)
        }
        val raw = IntArray(readWidth * readHeight)
        buffer.get(raw)
        sink.accept(outWidth, outHeight, { FastCapture.flipped(raw, readWidth, outWidth, outHeight) }, {})
    }
    *///?}
}
