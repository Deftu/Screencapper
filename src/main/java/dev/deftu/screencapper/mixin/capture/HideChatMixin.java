package dev.deftu.screencapper.mixin.capture;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.deftu.screencapper.screenshot.capture.input.HiddenLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else if >=1.21 {
/*import net.minecraft.client.gui.Gui;
*///?} else {
/*import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.chat.ChatGui;
*///?}
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else if >=1.21 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21 && <1.21.6 {
/*import net.minecraft.client.gui.components.ChatComponent;
*///?}

//? if >=26.2 {
@Mixin(Hud.class)
//?} else if >=1.21 {
/*@Mixin(Gui.class)
*///?} else {
/*@Mixin(GameGui.class)
*///?}
public class HideChatMixin {
    //? if >=26.2 {
    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractChat(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private boolean screencapper$hideChat(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        return !HiddenLayers.hidesChat();
    }
    //?} else if >=26.1 {
    /*@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractChat(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private boolean screencapper$hideChat(Gui gui, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        return !HiddenLayers.hidesChat();
    }
    *///?} else if >=1.21.6 {
    /*@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderChat(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private boolean screencapper$hideChat(Gui gui, GuiGraphics graphics, DeltaTracker deltaTracker) {
        return !HiddenLayers.hidesChat();
    }
    *///?} else if >=1.21 {
    /*@WrapWithCondition(method = "renderChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V"))
    private boolean screencapper$hideChat(ChatComponent chat, GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused) {
        return !HiddenLayers.hidesChat();
    }
    *///?} else {
    /*@WrapWithCondition(method = "render(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;render(I)V"))
    private boolean screencapper$hideChat(ChatGui chat, int ticks) {
        return !HiddenLayers.hidesChat();
    }
    *///?}
}
