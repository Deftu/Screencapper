package dev.deftu.screencapper.mixin.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.deftu.screencapper.screenshot.capture.input.CaptureInput;
import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Unique;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.At;
import org.lwjgl.input.Mouse;
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
    @WrapMethod(method = "onButton")
    private void screencapper$clickWheel(long window, MouseButtonInfo info, int action, Operation<Void> original) {
        if (action == 0 || !screencapper$takeClick(info.button(), action)) original.call(window, info, action);
    }
    //?} else if >=1.21 {
    /*@WrapMethod(method = "onPress")
    private void screencapper$clickWheel(long window, int button, int action, int modifiers, Operation<Void> original) {
        if (action == 0 || !screencapper$takeClick(button, action)) original.call(window, button, action, modifiers);
    }
    *///?}

    //? if >=1.21 {
    @WrapMethod(method = "onScroll")
    private void screencapper$scrollWheel(long window, double xOffset, double yOffset, Operation<Void> original) {
        if (!CaptureInput.blocksInput()) original.call(window, xOffset, yOffset);
    }

    @Unique
    private static boolean screencapper$takeClick(int button, int action) {
        if (!CaptureInput.blocksInput()) return false;
        if (action == 1) CaptureInput.onMouseButton(button);
        return true;
    }
    //?} else {
    /*@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z", remap = false))
    private boolean screencapper$clickWheel(Operation<Boolean> original) {
        while (original.call()) {
            if (!CaptureInput.blocksInput()) return true;
            int button = Mouse.getEventButton();
            if (!Mouse.getEventButtonState()) {
                if (button >= 0) return true;
                continue;
            }
            if (button >= 0) CaptureInput.onMouseButton(button);
        }
        return false;
    }
    *///?}
}
