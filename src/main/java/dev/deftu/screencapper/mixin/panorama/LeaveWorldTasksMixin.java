package dev.deftu.screencapper.mixin.panorama;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

//? if >=1.21 && <1.21.5 {
/*import dev.deftu.screencapper.screenshot.capture.input.LeavePanorama;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

@Mixin(Minecraft.class)
public class LeaveWorldTasksMixin {
    //? if >=1.21 && <1.21.5 {
    /*@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;dropAllTasks()V", shift = At.Shift.AFTER))
    private void screencapper$panoramaOnLeave(CallbackInfo ci) {
        LeavePanorama.afterTasksDropped();
    }
    *///?}
}
