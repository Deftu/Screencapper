package dev.deftu.screencapper.mixins;

import net.minecraft.client.main.Main;
import org.apache.commons.lang3.SystemUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Main.class})
public class MainMixin {

    @Inject(method = "main*", at = @At("HEAD"), remap = false)
    private static void disableHeadlessMode(CallbackInfo ci) {
        if (!SystemUtils.IS_OS_MAC) System.setProperty("java.awt.headless", "false");
    }

}
