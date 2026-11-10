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

/**
 * Draws the capture wheel and timer countdown on top of the in-game GUI, outside OneConfig's HUD system.
 * <p>
 * The hooks, per version, all at a point that runs whether or not F1 hid the HUD:
 * 26.2+: the tail of {@code Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)}.
 * 26.1: the tail of {@code Gui.extractRenderState(GuiGraphicsExtractor, DeltaTracker)}.
 * 1.21-1.21.11: the tail of {@code Gui.render(GuiGraphics, DeltaTracker)}.
 * 1.8.9: {@code GameRenderer.render(FJ)V} at the {@code Profiler.pop()} closing its "gui" section, since
 * {@code GameGui.render(F)V} is skipped while the HUD is hidden.
 */
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
    /*@Inject(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V", ordinal = 1))
    private void screencapper$drawOverlay(float tickDelta, long nanoTime, CallbackInfo ci) {
        CaptureOverlay.render(null);
    }
    *///?}
}
