package dev.deftu.screencapper.mixin.input;

import dev.deftu.screencapper.screenshot.capture.input.CaptureInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
*///?}
//? if >=1.21.9
import net.minecraft.client.input.KeyEvent;

//? if >=1.21 {
@Mixin(KeyboardHandler.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class KeyInputMixin {
    //? if >=1.21.9 {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void screencapper$blockKeys(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != 0 && CaptureInput.blocksInput()) ci.cancel();
    }
    //?} else if >=1.21 {
    /*@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void screencapper$blockKeys(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action != 0 && CaptureInput.blocksInput()) ci.cancel();
    }
    *///?} else {
    /*@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false))
    private boolean screencapper$blockKeys(Operation<Boolean> original) {
        while (original.call()) {
            if (!Keyboard.getEventKeyState() || !CaptureInput.blocksInput()) return true;
        }
        return false;
    }
    *///?}
}
