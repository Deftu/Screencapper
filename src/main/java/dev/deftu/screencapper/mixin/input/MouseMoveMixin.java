package dev.deftu.screencapper.mixin.input;

import dev.deftu.screencapper.screenshot.capture.input.CaptureInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21 {
import net.minecraft.client.MouseHandler;
//?} else {
/*import net.minecraft.client.Mouse;
*///?}

//? if >=1.21 {
@Mixin(MouseHandler.class)
//?} else {
/*@Mixin(Mouse.class)
*///?}
public class MouseMoveMixin {
    //? if >=1.21 {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void screencapper$redirectToMenu(CallbackInfo ci) {
        if (!CaptureInput.blocksInput()) return;
        CaptureInput.onMouseDelta(accumulatedDX, accumulatedDY);
        accumulatedDX = 0.0;
        accumulatedDY = 0.0;
        ci.cancel();
    }
    //?} else {
    /*@Shadow
    public int x;

    @Shadow
    public int y;

    @Inject(method = "tick", at = @At("TAIL"))
    private void screencapper$redirectToMenu(CallbackInfo ci) {
        if (!CaptureInput.blocksInput()) return;
        CaptureInput.onMouseDelta(x, -y);
        x = 0;
        y = 0;
    }
    *///?}
}
