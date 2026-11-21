package dev.deftu.screencapper.mixin.panorama;

import dev.deftu.screencapper.screenshot.capture.highres.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}

//? if <1.21.5 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
*///?}

@Mixin(GameRenderer.class)
public class PanoramaGameRendererMixin {
    //? if >=26.2 {
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void screencapper$panorama(CallbackInfo ci) {
        PanoramaRenderer.beforeFrame(0f);
    }
    //?} else if >=26.1 {
    /*@Inject(method = "update(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void screencapper$panorama(CallbackInfo ci) {
        PanoramaRenderer.beforeFrame(0f);
    }
    *///?} else if >=1.21 {
    /*@Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void screencapper$panorama(CallbackInfo ci) {
        PanoramaRenderer.beforeFrame(0f);
    }
    *///?} else {
    /*@Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void screencapper$panorama(float partialTick, long nanoTime, CallbackInfo ci) {
        PanoramaRenderer.beforeFrame(partialTick);
    }
    *///?}

    //? if >=1.21.4 && <1.21.5 {
    /*@ModifyReturnValue(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"))
    private float screencapper$panoramaFov(float original) {
        return PanoramaRenderer.getActive() ? 90f : original;
    }
    *///?} else if >=1.21 && <1.21.4 {
    /*@ModifyReturnValue(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"))
    private double screencapper$panoramaFov(double original) {
        return PanoramaRenderer.getActive() ? 90.0 : original;
    }
    *///?} else if <1.21 {
    /*@ModifyReturnValue(method = "getFov(FZ)F", at = @At("RETURN"))
    private float screencapper$panoramaFov(float original) {
        return PanoramaRenderer.getActive() ? 90f : original;
    }

    @ModifyReturnValue(method = "shouldRenderBlockOutline()Z", at = @At("RETURN"))
    private boolean screencapper$panoramaOutline(boolean original) {
        return !PanoramaRenderer.getActive() && original;
    }
    *///?}
}
