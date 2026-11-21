package dev.deftu.screencapper.mixin;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import net.minecraft.client.gui.components.ChatComponent;
//?} else {
/*import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.deftu.screencapper.chat.LegacyChatSuppression;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.injection.At;
*///?}

//? if >=1.21 {
@Mixin(ChatComponent.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class ChatMixin {
    //? if <1.21 {
    /*@WrapWithCondition(method = "handleGuiKeyBindings", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;addMessage(Lnet/minecraft/text/Text;)V"))
    private boolean screencapper$dropReplacedScreenshotMessage(ChatGui chat, Text message) {
        return !LegacyChatSuppression.consume(message);
    }
    *///?}
}
