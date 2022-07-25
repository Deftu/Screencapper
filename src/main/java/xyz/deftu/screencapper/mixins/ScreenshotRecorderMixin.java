package xyz.deftu.screencapper.mixins;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.deftu.screencapper.ScreenshotHandler;

import java.io.File;
import java.util.function.Consumer;

@Mixin({ScreenshotRecorder.class})
public class ScreenshotRecorderMixin {
    @Inject(method = "method_1661", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImage;writeTo(Ljava/io/File;)V", shift = At.Shift.BEFORE))
    private static void onFileWrite(NativeImage nativeImage, File file, Consumer<Text> consumer, CallbackInfo ci) {
        try {
            byte[] bytes = nativeImage.getBytes();
            ScreenshotHandler.INSTANCE.handle(nativeImage, bytes, file);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't read from screenshot.", e);
        }
    }

    @ModifyArg(method = "method_1661", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private static Object acceptText(Object original) {
        return ScreenshotHandler.INSTANCE.createText((Text) original);
    }
}
