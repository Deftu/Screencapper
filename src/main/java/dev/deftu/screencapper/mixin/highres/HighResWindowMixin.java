package dev.deftu.screencapper.mixin.highres;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import com.mojang.blaze3d.platform.Window;
//?} else {
/*import net.minecraft.client.render.Window;
*///?}

//? if >=1.21.5 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.deftu.screencapper.screenshot.capture.highres.HighResRenderer;
import org.spongepowered.asm.mixin.injection.At;
//?}

@Mixin(Window.class)
public class HighResWindowMixin {
    //? if >=1.21.5 {
    @ModifyReturnValue(method = "calculateScale", at = @At("RETURN"))
    private int screencapper$scaleGui(int original) {
        return HighResRenderer.guiScale(original);
    }
    //?}
}
