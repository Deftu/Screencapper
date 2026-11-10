package dev.deftu.screencapper.screenshot.capture;

import dev.deftu.screencapper.MainThread;
import dev.deftu.screencapper.screenshot.ScreenshotCapture;
import net.minecraft.client.Minecraft;

import java.io.File;

//? if >=1.21 {
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
//?} else {
/*import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
*///?}

/** Vanilla's screenshot chat messages, and the hand-off from a finished save to Screencapper's chat card */
public final class CaptureResults {
    private CaptureResults() {
    }

    /**
     * Reports a finished save the way vanilla would: Screencapper's chat card when it's on, otherwise vanilla's message.
     * Safe to call from any thread.
     */
    public static void deliver(File file, Throwable error) {
        MainThread.run(() -> {
            //? if >=1.21 {
            Component message = error == null ? success(file) : failure(error);
            //?} else {
            /*Text message = error == null ? success(file) : failure(error);
            *///?}
            if (error == null && ScreenshotCapture.onMessage(message.getString())) return;
            show(message);
        });
    }

    //? if >=1.21 {
    public static Component success(File file) {
        //? if >=1.21.5 {
        ClickEvent click = new ClickEvent.OpenFile(file.getAbsoluteFile());
        //?} else {
        /*ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath());
        *///?}
        Component name = Component.literal(file.getName()).withStyle(ChatFormatting.UNDERLINE).withStyle(style -> style.withClickEvent(click));
        return Component.translatable("screenshot.success", name);
    }

    public static Component failure(Throwable error) {
        return Component.translatable("screenshot.failure", String.valueOf(error.getMessage()));
    }

    private static void show(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) return;
        //? if >=26.2 {
        minecraft.gui.hud.getChat().addClientSystemMessage(message);
        //?} else if >=26.1 {
        /*minecraft.gui.getChat().addClientSystemMessage(message);
        *///?} else {
        /*minecraft.gui.getChat().addMessage(message);
        *///?}
    }
    //?} else {
    /*public static Text success(File file) {
        Text name = new LiteralText(file.getName());
        name.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()));
        name.getStyle().setUnderlined(true);
        return new TranslatableText("screenshot.success", name);
    }

    public static Text failure(Throwable error) {
        return new TranslatableText("screenshot.failure", String.valueOf(error.getMessage()));
    }

    private static void show(Text message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) minecraft.gui.getChat().addMessage(message);
    }
    *///?}
}
