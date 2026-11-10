package dev.deftu.screencapper.screenshot.capture;

import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

//? if >=1.21 {
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;
*///?}

/**
 * Reads framebuffer pixels for the capture pipeline.
 * <p>
 * On 1.21.5 and newer this goes through vanilla's device-neutral screenshot code, so it works on every render backend
 * (including Vulkan on 26.2+). Only the copy happens on the render thread; the pixels are converted when the sink asks
 * for them, on a background thread.
 */
public final class CaptureSource {
    /** Receives a capture: [pixels] returns width x height ARGB, top row first; [release] frees the native copy */
    public interface Sink {
        void accept(int width, int height, Supplier<int[]> pixels, Runnable release);
    }

    private CaptureSource() {
    }

    /** Reads the main framebuffer as it is now. Call on the render thread, after a frame has been drawn */
    public static void readMain(Sink sink) {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        read(minecraft.gameRenderer.mainRenderTarget(), 1, sink);
        //?} else if >=1.21 {
        /*read(minecraft.getMainRenderTarget(), 1, sink);
        *///?} else {
        /*read(minecraft.getRenderTarget(), minecraft.width, minecraft.height, sink);
        *///?}
    }

    //? if >=1.21 {
    /** [downscale] averages blocks of pixels; it must be 1 before 1.21.8 */
    public static void read(RenderTarget target, int downscale, Sink sink) {
        //? if >=1.21.8 {
        Screenshot.takeScreenshot(target, downscale, image -> deliver(image, sink));
        //?} else if >=1.21.5 {
        /*if (downscale != 1) throw new IllegalArgumentException("Downscaled captures need 1.21.8 or newer");
        Screenshot.takeScreenshot(target, image -> deliver(image, sink));
        *///?} else {
        /*if (downscale != 1) throw new IllegalArgumentException("Downscaled captures need 1.21.8 or newer");
        deliver(Screenshot.takeScreenshot(target), sink);
        *///?}
    }

    private static void deliver(NativeImage image, Sink sink) {
        sink.accept(image.getWidth(), image.getHeight(), image::makePixelArray, image::close);
    }
    //?} else {
    /*/^* [width] and [height] are the window size, used only when framebuffers are unsupported *^/
    public static void read(RenderTarget target, int width, int height, Sink sink) {
        boolean fbo = GLX.useFbo();
        int readWidth = fbo ? target.width : width;
        int readHeight = fbo ? target.height : height;
        int outWidth = fbo ? target.viewWidth : width;
        int outHeight = fbo ? target.viewHeight : height;

        IntBuffer buffer = BufferUtils.createIntBuffer(readWidth * readHeight);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        if (fbo) {
            GlStateManager.bindTexture(target.colorTextureId);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        } else {
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        }
        int[] raw = new int[readWidth * readHeight];
        buffer.get(raw);
        sink.accept(outWidth, outHeight, () -> FastCapture.flipped(raw, readWidth, outWidth, outHeight), () -> {});
    }
    *///?}
}
