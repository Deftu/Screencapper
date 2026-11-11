package dev.deftu.screencapper.mixin.input;

import dev.deftu.screencapper.screenshot.capture.input.CaptureInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.injection.Redirect;
*///?}
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonInfo;

//? if >=1.21 {
@Mixin(MouseHandler.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class MouseButtonMixin {
    //? if >=1.21.9 {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void screencapper$clickWheel(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action != 0 && screencapper$takeClick(info.button(), action)) ci.cancel();
    }
    //?} else if >=1.21 {
    /*@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void screencapper$clickWheel(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action != 0 && screencapper$takeClick(button, action)) ci.cancel();
    }
    *///?}

    //? if >=1.21 {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void screencapper$scrollWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (CaptureInput.blocksInput()) ci.cancel();
    }

    private static boolean screencapper$takeClick(int button, int action) {
        if (!CaptureInput.blocksInput()) return false;
        if (action == 1 && button == InputConstants.MOUSE_BUTTON_LEFT) CaptureInput.onMouseButton(true);
        else if (action == 1 && button == InputConstants.MOUSE_BUTTON_RIGHT) CaptureInput.onMouseButton(false);
        return true;
    }
    //?} else {
    /*@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z", remap = false))
    private boolean screencapper$clickWheel() {
        while (Mouse.next()) {
            if (!CaptureInput.blocksInput()) return true;
            int button = Mouse.getEventButton();
            if (!Mouse.getEventButtonState()) {
                if (button >= 0) return true;
                continue;
            }
            if (button == 0) CaptureInput.onMouseButton(true);
            else if (button == 1) CaptureInput.onMouseButton(false);
        }
        return false;
    }
    *///?}
}
