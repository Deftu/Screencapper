package dev.deftu.screencapper.mixin;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import net.minecraft.client.gui.components.ChatComponent;
//?} else {
/*import dev.deftu.screencapper.chat.LegacyChatSuppression;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

//? if >=1.21 {
@Mixin(ChatComponent.class)
//?} else {
/*@Mixin(ChatGui.class)
*///?}
public class ChatMixin {
    //? if <1.21 {
    /*@Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void screencapper$dropReplacedScreenshotMessage(Text message, CallbackInfo ci) {
        if (LegacyChatSuppression.consume(message)) ci.cancel();
    }
    *///?}
}
