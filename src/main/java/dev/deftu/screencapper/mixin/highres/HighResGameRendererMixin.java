package dev.deftu.screencapper.mixin.highres;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}

//? if <1.21.5 {
/*import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?}
//? if >=1.21 && <1.21.5 {
/*import org.joml.Matrix4f;
*///?}
//? if <1.21 {
/*import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Points the projection at one tile of a tiled high-res capture.
 * <p>
 * The aspect ratio becomes the target image's, and the tile's zoom goes on the drawing projection only, so culling still
 * sees the whole frustum and no tile loses chunks that sit outside its own slice. The first-person hand gets the same zoom,
 * and on 1.8.9 so does the GUI's ortho while a tiled capture draws the HUD.
 */
@Mixin(GameRenderer.class)
public class HighResGameRendererMixin {
    //? if >=1.21.4 && <1.21.5 {
    /*@ModifyArg(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;", remap = false), index = 1)
    private float screencapper$tiledAspect(float aspect) {
        float tiled = HighResRenderer.getTiledAspect();
        return tiled > 0f ? tiled : aspect;
    }

    @ModifyArg(method = {"renderLevel", "renderItemInHand"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/ProjectionType;)V"), index = 0)
    private Matrix4f screencapper$tile(Matrix4f projection) {
        return HighResRenderer.applyTile(projection);
    }
    *///?} else if >=1.21 && <1.21.4 {
    /*@ModifyArg(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;", remap = false), index = 1)
    private float screencapper$tiledAspect(float aspect) {
        float tiled = HighResRenderer.getTiledAspect();
        return tiled > 0f ? tiled : aspect;
    }

    @ModifyArg(method = {"renderLevel", "renderItemInHand"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V"), index = 0)
    private Matrix4f screencapper$tile(Matrix4f projection) {
        return HighResRenderer.applyTile(projection);
    }
    *///?} else if <1.21 {
    /*@ModifyArg(method = {"setupCamera", "renderItemInHand", "render(IFJ)V", "renderClouds"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false), index = 1)
    private float screencapper$tiledAspect(float aspect) {
        float tiled = HighResRenderer.getTiledAspect();
        return tiled > 0f ? tiled : aspect;
    }

    @Inject(method = {"renderItemInHand", "render(IFJ)V", "renderClouds"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false))
    private void screencapper$tile(CallbackInfo ci) {
        HighResRenderer.multiplyTile();
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Frustum;getInstance()Lnet/minecraft/client/render/FrustumData;", shift = At.Shift.AFTER))
    private void screencapper$tileAfterCulling(CallbackInfo ci) {
        HighResRenderer.prependTileToProjection();
    }

    @Inject(method = "setupGuiState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;ortho(DDDDDD)V"))
    private void screencapper$tileHud(CallbackInfo ci) {
        HighResRenderer.multiplyHudTile();
    }
    *///?}
}
