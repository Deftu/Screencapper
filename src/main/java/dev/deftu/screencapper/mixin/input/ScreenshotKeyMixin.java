package dev.deftu.screencapper.mixin.input;

import dev.deftu.screencapper.screenshot.capture.input.CaptureInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

//? if >=26.2 {
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else if >=1.21.9 {
/*import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} else if >=1.21 {
/*import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} else {
/*import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Takes over vanilla's screenshot key at the exact call where vanilla would take its screenshot, so every check before
 * it (key binding screens, the debug modifier, the debug panorama) behaves as vanilla.
 * <p>
 * The call sites, per version:
 * 26.2+: {@code Minecraft.handleGlobalKeyPress} calls {@code Screenshot.grab(Minecraft, boolean)}, which also handles
 * the debug panorama (Ctrl with {@code SharedConstants.DEBUG_PANORAMA_SCREENSHOT}), left to vanilla here.
 * 1.21-26.1: {@code KeyboardHandler.keyPress} calls {@code Screenshot.grab(File, RenderTarget, Consumer)} on a press of
 * {@code options.keyScreenshot}; the debug panorama is a separate branch that never reaches that call.
 * 1.8.9: {@code Minecraft.handleGuiKeyBindings} calls {@code Screenshot.take(File, int, int, RenderTarget)} on a press of
 * {@code options.screenshotKey}.
 */
//? if >=26.2 {
@Mixin(Minecraft.class)
//?} else if >=1.21 {
/*@Mixin(KeyboardHandler.class)
*///?} else {
/*@Mixin(Minecraft.class)
*///?}
public class ScreenshotKeyMixin {
    //? if >=26.2 {
    @Inject(method = "handleGlobalKeyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Lnet/minecraft/client/Minecraft;Z)V"), cancellable = true)
    private void screencapper$onScreenshotKey(InputConstants.Key key, boolean controlDown, CallbackInfoReturnable<Boolean> cir) {
        if (controlDown && SharedConstants.DEBUG_PANORAMA_SCREENSHOT) return;
        // A mouse button's release can't be polled the way a key's can, so mouse-bound screenshot keys only tap.
        boolean holdable = key.getType() != InputConstants.Type.MOUSE;
        if (CaptureInput.onScreenshotKey(key.getValue(), holdable)) cir.setReturnValue(true);
    }
    //?} else if >=1.21.9 {
    /*@Inject(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"), cancellable = true)
    private void screencapper$onScreenshotKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (CaptureInput.onScreenshotKey(event.key())) ci.cancel();
    }
    *///?} else if >=1.21 {
    /*@Inject(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"), cancellable = true)
    private void screencapper$onScreenshotKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (CaptureInput.onScreenshotKey(key)) ci.cancel();
    }
    *///?} else {
    /*@Inject(method = "handleGuiKeyBindings", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;take(Ljava/io/File;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;"), cancellable = true)
    private void screencapper$onScreenshotKey(CallbackInfo ci) {
        // Keys LWJGL only reports as a character can't be polled, so those only tap.
        int key = Keyboard.getEventKey();
        if (CaptureInput.onScreenshotKey(key, key != 0)) ci.cancel();
    }
    *///?}
}
