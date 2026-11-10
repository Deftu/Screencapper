package dev.deftu.screencapper.mixin.highres;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import com.mojang.blaze3d.platform.Window;
//?} else {
/*import net.minecraft.client.render.Window;
*///?}

//? if >=1.21.5 {
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/** Keeps the GUI's share of the frame while a high-res capture enlarges the framebuffer */
@Mixin(Window.class)
public class HighResWindowMixin {
    //? if >=1.21.5 {
    @Inject(method = "calculateScale", at = @At("RETURN"), cancellable = true)
    private void screencapper$scaleGui(CallbackInfoReturnable<Integer> cir) {
        int scale = HighResRenderer.guiScale(cir.getReturnValueI());
        if (scale != cir.getReturnValueI()) cir.setReturnValue(scale);
    }
    //?}
}
