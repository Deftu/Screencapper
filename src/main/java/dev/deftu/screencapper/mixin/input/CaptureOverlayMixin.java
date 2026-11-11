package dev.deftu.screencapper.mixin.input;

import dev.deftu.screencapper.gui.wheel.CaptureOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else if >=1.21 {
/*import net.minecraft.client.gui.Gui;
*///?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else if >=1.21 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

//? if >=26.2 {
@Mixin(Hud.class)
//?} else if >=1.21 {
/*@Mixin(Gui.class)
*///?} else {
/*@Mixin(GameRenderer.class)
*///?}
public class CaptureOverlayMixin {
    //? if >=26.1 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void screencapper$drawOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        CaptureOverlay.render(graphics);
    }
    //?} else if >=1.21 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void screencapper$drawOverlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        CaptureOverlay.render(graphics);
    }
    *///?} else {
    /*@Inject(method = "render(FJ)V", at = @At("TAIL"))
    private void screencapper$drawOverlay(float tickDelta, long nanoTime, CallbackInfo ci) {
        CaptureOverlay.render(null);
    }
    *///?}
}
