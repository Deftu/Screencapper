package dev.deftu.screencapper.mixin;

import dev.deftu.screencapper.screenshot.AutoCapture;
import dev.deftu.screencapper.screenshot.DoneAdvancement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21 {
import java.util.Collections;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.StatsS2CPacket;
import net.minecraft.stat.Stat;
*///?}

//? if >=1.21 {
@Mixin(ClientAdvancements.class)
//?} else {
/*@Mixin(ClientPlayNetworkHandler.class)
*///?}
public class AdvancementEarnedMixin {
    //? if >=1.21 {
    @Shadow
    @Final
    private Map<AdvancementHolder, AdvancementProgress> progress;

    @Unique
    private Set<String> screencapper$doneBefore = Collections.emptySet();

    @Inject(method = "update(Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;)V", at = @At("HEAD"))
    private void screencapper$rememberDone(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        Set<String> done = new HashSet<>();
        for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progress.entrySet()) {
            if (entry.getValue().isDone()) {
                done.add(entry.getKey().id().toString());
            }
        }

        screencapper$doneBefore = done;
    }

    @Inject(method = "update(Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;)V", at = @At("RETURN"))
    private void screencapper$reportEarned(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        List<DoneAdvancement> done = new ArrayList<>();
        for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progress.entrySet()) {
            if (entry.getValue().isDone()) {
                AdvancementHolder holder = entry.getKey();
                Optional<DisplayInfo> display = holder.value().display();
                done.add(new DoneAdvancement(holder.id().toString(), display.isPresent() && screencapper$toasts(display.get())));
            }
        }

        Set<String> before = screencapper$doneBefore;
        screencapper$doneBefore = Collections.emptySet();
        AutoCapture.onAdvancementsUpdated(before, done, packet.shouldReset(), screencapper$shown(packet));
    }

    @Unique
    private static boolean screencapper$toasts(DisplayInfo display) {
        //? if >=26.3 {
        return display.showToast();
        //?} else {
        /*return display.shouldShowToast();
        *///?}
    }

    @Unique
    private static boolean screencapper$shown(ClientboundUpdateAdvancementsPacket packet) {
        //? if >=26.3 {
        return packet.showAdvancements();
        //?} else if >=1.21.5 {
        /*return packet.shouldShowAdvancements();
        *///?} else {
        /*return true;
        *///?}
    }
    //?} else {
    /*@Shadow
    private boolean hasAchievements;

    @Inject(method = "handleStats(Lnet/minecraft/network/packet/s2c/play/StatsS2CPacket;)V", at = @At("HEAD"))
    private void screencapper$reportEarned(StatsS2CPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isOnSameThread()) {
            return;
        }

        Set<String> before = new HashSet<>();
        List<DoneAdvancement> after = new ArrayList<>();
        for (Map.Entry<Stat, Integer> entry : packet.getStats().entrySet()) {
            Stat stat = entry.getKey();
            if (!stat.isAchievement()) {
                continue;
            }

            if (minecraft.player.getStats().get(stat) != 0) {
                before.add(stat.key);
            }

            if (entry.getValue() > 0) {
                after.add(new DoneAdvancement(stat.key, true));
            }
        }

        AutoCapture.onAdvancementsUpdated(before, after, false, hasAchievements);
    }
    *///?}
}
