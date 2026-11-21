package dev.deftu.screencapper.mixin.panorama;

import dev.deftu.screencapper.screenshot.capture.input.LeavePanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21.10 {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else if >=1.21.5 {
/*import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} else if >=1.21 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
*///?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.world.ClientWorld;
*///?}

//? if >=1.21.10 {
@Mixin(Minecraft.class)
//?} else if >=1.21 {
/*@Mixin(PauseScreen.class)
*///?} else {
/*@Mixin(GameMenuScreen.class)
*///?}
public class LeaveWorldMixin {
    //? if >=1.21.10 {
    @Inject(method = "disconnectFromWorld(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void screencapper$panoramaOnLeave(CallbackInfo ci) {
        LeavePanorama.take();
    }
    //?} else if >=1.21.8 {
    /*@Inject(method = "disconnectFromWorld(Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private static void screencapper$panoramaOnLeave(CallbackInfo ci) {
        LeavePanorama.take();
    }
    *///?} else if >=1.21.5 {
    /*@Inject(method = "onDisconnect()V", at = @At("HEAD"))
    private void screencapper$panoramaOnLeave(CallbackInfo ci) {
        LeavePanorama.take();
    }
    *///?} else if >=1.21 {
    /*@WrapOperation(method = "onDisconnect()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;disconnect(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void screencapper$leaveWithSavingScreen(Minecraft minecraft, Screen screen, Operation<Void> original) {
        LeavePanorama.setLeaving(true);
        try {
            original.call(minecraft, screen);
        } finally {
            LeavePanorama.setLeaving(false);
        }
    }

    @WrapOperation(method = "onDisconnect()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;disconnect()V"))
    private void screencapper$leave(Minecraft minecraft, Operation<Void> original) {
        LeavePanorama.setLeaving(true);
        try {
            original.call(minecraft);
        } finally {
            LeavePanorama.setLeaving(false);
        }
    }
    *///?} else {
    /*@WrapOperation(method = "buttonClicked(Lnet/minecraft/client/gui/widget/ButtonWidget;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;disconnect()V"))
    private void screencapper$panoramaOnLeave(ClientWorld world, Operation<Void> original) {
        LeavePanorama.take();
        original.call(world);
    }
    *///?}
}
