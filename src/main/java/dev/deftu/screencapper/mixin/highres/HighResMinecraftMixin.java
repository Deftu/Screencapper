package dev.deftu.screencapper.mixin.highres;

import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if <1.21 {
/*import net.minecraft.client.TickTimer;
import org.spongepowered.asm.mixin.Shadow;
*///?}

/** Runs the high-res capture's per-frame work around the game renderer's frame */
@Mixin(Minecraft.class)
public class HighResMinecraftMixin {
    //? if >=26.3 {
    @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V", shift = At.Shift.AFTER))
    private void screencapper$afterFrame(CallbackInfo ci) {
        HighResRenderer.afterFrame();
    }
    //?} else if >=26.1 {
    /*@Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V", shift = At.Shift.AFTER))
    private void screencapper$afterFrame(CallbackInfo ci) {
        HighResRenderer.afterFrame();
    }
    *///?} else if >=1.21.5 {
    /*@Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V", shift = At.Shift.AFTER))
    private void screencapper$afterFrame(CallbackInfo ci) {
        HighResRenderer.afterFrame();
    }
    *///?} else if >=1.21 {
    /*@Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void screencapper$beforeFrame(CallbackInfo ci) {
        HighResRenderer.beforeFrame(0f);
    }
    *///?} else {
    /*@Shadow
    private TickTimer timer;

    @Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V"))
    private void screencapper$beforeFrame(CallbackInfo ci) {
        HighResRenderer.beforeFrame(timer.partialTick);
    }
    *///?}
}
