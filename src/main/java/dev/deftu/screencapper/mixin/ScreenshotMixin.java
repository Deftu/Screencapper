package dev.deftu.screencapper.mixin;

import dev.deftu.screencapper.screenshot.ScreenshotCapture;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;
//?} else {
/*import dev.deftu.screencapper.chat.LegacyChatSuppression;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

@Mixin(Screenshot.class)
public class ScreenshotMixin {
    //? if >=1.21 {
    @ModifyVariable(method = "grab", at = @At("HEAD"), argsOnly = true)
    private static Consumer<Component> screencapper$wrapMessage(Consumer<Component> consumer) {
        return ScreenshotCapture.wrap(consumer, Component::getString);
    }
    //?} else {
    /*@Inject(method = "take(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;", at = @At("RETURN"))
    private static void screencapper$onTaken(CallbackInfoReturnable<Text> cir) {
        Text message = cir.getReturnValue();
        if (message != null && ScreenshotCapture.onMessage(message.getString())) LegacyChatSuppression.suppress(message);
    }
    *///?}
}
