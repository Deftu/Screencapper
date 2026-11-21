package dev.deftu.screencapper.mixin.capture;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.deftu.screencapper.screenshot.capture.input.HiddenLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}
//? if >=26.3 {
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//?} else if >=26.1 {
/*import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
*///?} else if >=1.21.6 {
/*import org.joml.Matrix4f;
*///?} else if >=1.21 {
/*import net.minecraft.client.Camera;
import org.joml.Matrix4f;
*///?}

@Mixin(GameRenderer.class)
public class HideHandMixin {
    //? if >=26.3 {
    @WrapWithCondition(method = "render3dHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private boolean screencapper$hideHand(GameRenderer renderer, CameraRenderState cameraState, PlayerRenderState playerState, GpuTextureView depthTextureView) {
        return !HiddenLayers.hidesHand();
    }
    //?} else if >=26.1 {
    /*@WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"))
    private boolean screencapper$hideHand(GameRenderer renderer, CameraRenderState cameraState, float partialTick, Matrix4fc modelViewMatrix) {
        return !HiddenLayers.hidesHand();
    }
    *///?} else if >=1.21.6 {
    /*@WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(FZLorg/joml/Matrix4f;)V"))
    private boolean screencapper$hideHand(GameRenderer renderer, float partialTick, boolean sleeping, Matrix4f modelViewMatrix) {
        return !HiddenLayers.hidesHand();
    }
    *///?} else if >=1.21 {
    /*@WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V"))
    private boolean screencapper$hideHand(GameRenderer renderer, Camera camera, float partialTick, Matrix4f modelViewMatrix) {
        return !HiddenLayers.hidesHand();
    }
    *///?} else {
    /*@WrapWithCondition(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderItemInHand(FI)V"))
    private boolean screencapper$hideHand(GameRenderer renderer, float partialTick, int anaglyphPass) {
        return !HiddenLayers.hidesHand();
    }
    *///?}
}
