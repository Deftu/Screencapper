package dev.deftu.screencapper.mixin.highres;

import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 {
import com.mojang.blaze3d.platform.Window;
//?} else {
/*import net.minecraft.client.render.Window;
*///?}

//? if >=1.21 && <1.21.5 {
/*import org.spongepowered.asm.mixin.gen.Accessor;
*///?}

/** Lets a tiled high-res capture lay the HUD out for a virtual screen of the target size */
@Mixin(Window.class)
public interface HighResWindowAccessor {
    //? if >=1.21 && <1.21.5 {
    /*@Accessor("framebufferWidth")
    void screencapper$setFramebufferWidth(int width);

    @Accessor("framebufferHeight")
    void screencapper$setFramebufferHeight(int height);
    *///?}
}
