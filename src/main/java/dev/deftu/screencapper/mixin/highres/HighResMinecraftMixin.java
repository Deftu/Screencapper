package dev.deftu.screencapper.mixin.highres;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}
//? if >=1.21 && <26.3 {
/*import net.minecraft.client.DeltaTracker;
*///?}

@Mixin(Minecraft.class)
public class HighResMinecraftMixin {
    //? if >=26.3 {
    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V"))
    private void screencapper$afterFrame(GameRenderer renderer, Operation<Void> original) {
        original.call(renderer);
        HighResRenderer.afterFrame();
    }
    //?} else if >=26.1 {
    /*@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void screencapper$afterFrame(GameRenderer renderer, DeltaTracker deltaTracker, boolean advanceGameTime, Operation<Void> original) {
        original.call(renderer, deltaTracker, advanceGameTime);
        HighResRenderer.afterFrame();
    }
    *///?} else if >=1.21.5 {
    /*@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void screencapper$afterFrame(GameRenderer renderer, DeltaTracker deltaTracker, boolean advanceGameTime, Operation<Void> original) {
        original.call(renderer, deltaTracker, advanceGameTime);
        HighResRenderer.afterFrame();
    }
    *///?} else if >=1.21 {
    /*@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void screencapper$beforeFrame(GameRenderer renderer, DeltaTracker deltaTracker, boolean advanceGameTime, Operation<Void> original) {
        HighResRenderer.beforeFrame(0f);
        original.call(renderer, deltaTracker, advanceGameTime);
    }
    *///?} else {
    /*@WrapOperation(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V"))
    private void screencapper$beforeFrame(GameRenderer renderer, float partialTick, long nanoTime, Operation<Void> original) {
        HighResRenderer.beforeFrame(partialTick);
        original.call(renderer, partialTick, nanoTime);
    }
    *///?}
}
