package dev.deftu.screencapper;

import net.minecraft.client.Minecraft;

public final class MainThread {
    private MainThread() {
    }

    public static boolean isCurrent() {
        //? if >=1.21 {
        return Minecraft.getInstance().isSameThread();
        //?} else {
        /*return Minecraft.getInstance().isOnSameThread();
        *///?}
    }

    public static void run(Runnable action) {
        if (isCurrent()) {
            action.run();
            return;
        }

        //? if >=1.21 {
        Minecraft.getInstance().execute(action);
        //?} else {
        /*Minecraft.getInstance().executeTask(action);
        *///?}
    }
}
