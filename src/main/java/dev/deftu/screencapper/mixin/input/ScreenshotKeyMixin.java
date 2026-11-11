package dev.deftu.screencapper.mixin.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.deftu.screencapper.screenshot.capture.input.CaptureInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.2 {
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
//?} else if >=1.21.9 {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.io.File;
import java.util.function.Consumer;
*///?} else if >=1.21 {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.network.chat.Component;
import java.io.File;
import java.util.function.Consumer;
*///?} else {
/*import dev.deftu.screencapper.chat.LegacyChatSuppression;
import dev.deftu.screencapper.screenshot.capture.FastCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.lwjgl.input.Keyboard;
import java.io.File;
*///?}

//? if >=26.2 {
@Mixin(Minecraft.class)
//?} else if >=1.21 {
/*@Mixin(KeyboardHandler.class)
*///?} else {
/*@Mixin(Minecraft.class)
*///?}
public class ScreenshotKeyMixin {
    //? if >=26.2 {
    @WrapOperation(method = "handleGlobalKeyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Lnet/minecraft/client/Minecraft;Z)V"))
    private void screencapper$onScreenshotKey(Minecraft minecraft, boolean controlDown, Operation<Void> original, @Local(argsOnly = true) InputConstants.Key key) {
        if (!(controlDown && SharedConstants.DEBUG_PANORAMA_SCREENSHOT)) {
            boolean holdable = key.getType() != InputConstants.Type.MOUSE;
            if (CaptureInput.onScreenshotKey(key.getValue(), holdable)) return;
        }
        original.call(minecraft, controlDown);
    }
    //?} else if >=1.21.9 {
    /*@WrapOperation(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"))
    private void screencapper$onScreenshotKey(File gameDirectory, RenderTarget target, Consumer<Component> callback, Operation<Void> original, @Local(argsOnly = true) KeyEvent event) {
        if (CaptureInput.onScreenshotKey(event.key())) return;
        original.call(gameDirectory, target, callback);
    }
    *///?} else if >=1.21 {
    /*@WrapOperation(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"))
    private void screencapper$onScreenshotKey(File gameDirectory, RenderTarget target, Consumer<Component> callback, Operation<Void> original, @Local(argsOnly = true, ordinal = 0) int key) {
        if (CaptureInput.onScreenshotKey(key)) return;
        original.call(gameDirectory, target, callback);
    }
    *///?} else {
    /*@WrapOperation(method = "handleGuiKeyBindings", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;take(Ljava/io/File;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;"))
    private Text screencapper$onScreenshotKey(File gameDir, int width, int height, RenderTarget target, Operation<Text> original) {
        int key = Keyboard.getEventKey();
        boolean repeat = Keyboard.isRepeatEvent() && FastCapture.isActive();
        if (!repeat && !CaptureInput.onScreenshotKey(key, key != 0)) return original.call(gameDir, width, height, target);

        Text placeholder = new LiteralText("");
        LegacyChatSuppression.suppress(placeholder);
        return placeholder;
    }
    *///?}
}
