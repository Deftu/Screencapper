package dev.deftu.screencapper.mixin.highres;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}

//? if <1.21.5 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer;
import org.spongepowered.asm.mixin.injection.At;
*///?}
//? if >=1.21 && <1.21.5 {
/*import org.joml.Matrix4f;
*///?}
//? if >=1.21.4 && <1.21.5 {
/*import com.mojang.blaze3d.ProjectionType;
*///?}
//? if <1.21 {
/*import net.minecraft.client.render.FrustumData;
*///?}

@Mixin(GameRenderer.class)
public class HighResGameRendererMixin {
    //? if >=1.21 && <1.21.5 {
    /*@WrapOperation(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;", remap = false))
    private Matrix4f screencapper$tiledAspect(Matrix4f matrix, float fov, float aspect, float near, float far, Operation<Matrix4f> original) {
        return original.call(matrix, fov, screencapper$aspect(aspect), near, far);
    }
    *///?}

    //? if >=1.21.4 && <1.21.5 {
    /*@WrapOperation(method = {"renderLevel", "renderItemInHand"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/ProjectionType;)V"))
    private void screencapper$tile(Matrix4f projection, ProjectionType type, Operation<Void> original) {
        original.call(HighResRenderer.applyTile(projection), type);
    }
    *///?} else if >=1.21 && <1.21.4 {
    /*@WrapOperation(method = {"renderLevel", "renderItemInHand"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V"))
    private void screencapper$tile(GameRenderer renderer, Matrix4f projection, Operation<Void> original) {
        original.call(renderer, HighResRenderer.applyTile(projection));
    }
    *///?} else if <1.21 {
    /*@WrapOperation(method = "setupCamera", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false))
    private void screencapper$tiledAspect(float fov, float aspect, float near, float far, Operation<Void> original) {
        original.call(fov, screencapper$aspect(aspect), near, far);
    }

    @WrapOperation(method = {"renderItemInHand", "render(IFJ)V", "renderClouds"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false))
    private void screencapper$tile(float fov, float aspect, float near, float far, Operation<Void> original) {
        HighResRenderer.multiplyTile();
        original.call(fov, screencapper$aspect(aspect), near, far);
    }

    @WrapOperation(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Frustum;getInstance()Lnet/minecraft/client/render/FrustumData;"))
    private FrustumData screencapper$tileAfterCulling(Operation<FrustumData> original) {
        FrustumData frustum = original.call();
        HighResRenderer.prependTileToProjection();
        return frustum;
    }

    @WrapOperation(method = "setupGuiState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;ortho(DDDDDD)V"))
    private void screencapper$tileHud(double left, double right, double bottom, double top, double near, double far, Operation<Void> original) {
        HighResRenderer.multiplyHudTile();
        original.call(left, right, bottom, top, near, far);
    }
    *///?}

    //? if <1.21.5 {
    /*private static float screencapper$aspect(float aspect) {
        float tiled = HighResRenderer.getTiledAspect();
        return tiled > 0f ? tiled : aspect;
    }
    *///?}
}
