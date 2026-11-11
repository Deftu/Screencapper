package dev.deftu.screencapper.mixin;

import dev.deftu.screencapper.metadata.MetadataCollector;
import dev.deftu.screencapper.screenshot.ScreenshotCapture;
import dev.deftu.screencapper.screenshot.capture.CaptureKind;
import dev.deftu.screencapper.screenshot.capture.CaptureResults;
import dev.deftu.screencapper.screenshot.capture.CaptureSource;
import dev.deftu.screencapper.screenshot.capture.FastCapture;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.io.File;
import java.util.Map;

//? if >=1.21 {
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;
//?} else {
/*import dev.deftu.screencapper.chat.LegacyChatSuppression;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

@Mixin(Screenshot.class)
public class ScreenshotMixin {
    //? if >=1.21 {
    @ModifyVariable(method = "grab", at = @At("HEAD"), argsOnly = true)
    private static Consumer<Component> screencapper$wrapMessage(Consumer<Component> consumer) {
        return ScreenshotCapture.wrap(consumer, Component::getString);
    }

    //? if >=1.21.8 {
    @Inject(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void screencapper$saveFast(File workDir, String forceName, RenderTarget target, int downscale, Consumer<Component> callback, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void screencapper$saveFast(File workDir, String forceName, RenderTarget target, Consumer<Component> callback, CallbackInfo ci) {
        int downscale = 1;
    *///?}
        if (!FastCapture.isActive()) return;
        ci.cancel();

        Consumer<Component> reply = ScreenshotCapture.wrap(callback, Component::getString);
        File file = FastCapture.nextFile(workDir, forceName);
        Map<String, String> metadata = MetadataCollector.collect();
        CaptureSource.read(target, downscale, (width, height, pixels, release) -> FastCapture.save(file, width, height, pixels, release, error ->
            reply.accept(error == null ? CaptureResults.success(file) : CaptureResults.failure(error)), CaptureKind.NORMAL, metadata));
    }
    //?} else {
    /*@Inject(method = "take(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;", at = @At("HEAD"), cancellable = true)
    private static void screencapper$saveFast(File gameDir, String forceName, int width, int height, RenderTarget target, CallbackInfoReturnable<Text> cir) {
        if (!FastCapture.isActive()) return;

        File file = FastCapture.nextFile(gameDir, forceName);
        Map<String, String> metadata = MetadataCollector.collect();
        CaptureSource.read(target, width, height, (outWidth, outHeight, pixels, release) ->
            FastCapture.save(file, outWidth, outHeight, pixels, release, error -> CaptureResults.deliver(file, error), CaptureKind.NORMAL, metadata));

        Text placeholder = new LiteralText("");
        LegacyChatSuppression.suppress(placeholder);
        cir.setReturnValue(placeholder);
    }

    @Inject(method = "take(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;", at = @At("RETURN"))
    private static void screencapper$onTaken(CallbackInfoReturnable<Text> cir) {
        Text message = cir.getReturnValue();
        if (message != null && ScreenshotCapture.onMessage(message.getString())) LegacyChatSuppression.suppress(message);
    }
    *///?}
}
