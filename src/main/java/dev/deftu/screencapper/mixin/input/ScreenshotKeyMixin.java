package dev.deftu.screencapper.mixin.input;

import dev.deftu.screencapper.screenshot.capture.input.CaptureInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
*///?}
//? if >=26.2 {
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
//?} else if >=1.21.9 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.io.File;
import java.util.function.Consumer;
*///?} else if >=1.21 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
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
    @WrapWithCondition(method = "handleGlobalKeyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Lnet/minecraft/client/Minecraft;Z)V"))
    private boolean screencapper$onScreenshotKey(Minecraft minecraft, boolean controlDown, @Local(name = "key") InputConstants.Key key) {
        if (controlDown && SharedConstants.DEBUG_PANORAMA_SCREENSHOT) return true;
        boolean holdable = key.getType() != InputConstants.Type.MOUSE;
        return !CaptureInput.onScreenshotKey(key.getValue(), holdable);
    }
    //?} else if >=26.1 {
    /*@WrapWithCondition(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"))
    private boolean screencapper$onScreenshotKey(File gameDirectory, RenderTarget target, Consumer<Component> callback, @Local(name = "event") KeyEvent event) {
        return !CaptureInput.onScreenshotKey(event.key());
    }
    *///?} else if >=1.21.9 {
    /*@WrapWithCondition(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"))
    private boolean screencapper$onScreenshotKey(File gameDirectory, RenderTarget target, Consumer<Component> callback, @Local(argsOnly = true) KeyEvent event) {
        return !CaptureInput.onScreenshotKey(event.key());
    }
    *///?} else if >=1.21 {
    /*@WrapWithCondition(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"))
    private boolean screencapper$onScreenshotKey(File gameDirectory, RenderTarget target, Consumer<Component> callback, @Local(argsOnly = true, ordinal = 0) int key) {
        return !CaptureInput.onScreenshotKey(key);
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
