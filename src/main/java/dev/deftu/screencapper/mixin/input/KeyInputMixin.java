package dev.deftu.screencapper.mixin.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.deftu.screencapper.screenshot.capture.input.CaptureInput;
import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.client.KeyboardHandler;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.At;
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
    @WrapMethod(method = "keyPress")
    private void screencapper$blockKeys(long window, int action, KeyEvent event, Operation<Void> original) {
        if (!CaptureInput.onKey(event.key(), action)) original.call(window, action, event);
    }
    //?} else if >=1.21 {
    /*@WrapMethod(method = "keyPress")
    private void screencapper$blockKeys(long window, int key, int scancode, int action, int modifiers, Operation<Void> original) {
        if (!CaptureInput.onKey(key, action)) original.call(window, key, scancode, action, modifiers);
    }
    *///?} else {
    /*@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false))
    private boolean screencapper$blockKeys(Operation<Boolean> original) {
        while (original.call()) {
            int action = !Keyboard.getEventKeyState() ? 0 : Keyboard.isRepeatEvent() ? 2 : 1;
            if (!CaptureInput.onKey(Keyboard.getEventKey(), action)) return true;
        }
        return false;
    }
    *///?}
}
