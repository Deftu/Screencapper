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

/**
 * Hands mouse movement to the capture menu instead of the game while it's open, so picking doesn't turn the camera or
 * move over the screen underneath.
 * <p>
 * 1.21+: {@code MouseHandler.handleAccumulatedMovement} turns the player and moves the screen's hover from
 * {@code accumulatedDX}/{@code accumulatedDY}, then zeroes them. 1.8.9: {@code Mouse.tick} copies LWJGL's deltas into
 * {@code x}/{@code y}, which the game renderer turns the player with afterwards.
 */
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
        // LWJGL 2 counts y upwards.
        CaptureInput.onMouseDelta(x, -y);
        x = 0;
        y = 0;
    }
    *///?}
}
