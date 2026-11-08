package dev.deftu.screencapper.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

//? if >=1.21 {
import net.minecraft.client.gui.components.ChatComponent;
//?} else {
/*import net.minecraft.client.gui.chat.ChatGui;
*///?}

//? if >=1.21 {
@Mixin(ChatComponent.class)
//?} else {
/*@Mixin(ChatGui.class)
*///?}
public interface ChatAccessor {
    //? if >=1.21 {
    @Accessor("allMessages")
    List<Object> screencapper$messages();

    @Invoker("refreshTrimmedMessages")
    void screencapper$refresh();
    //?} else {
    /*@Accessor("messages")
    List<Object> screencapper$messages();

    @Invoker("reset")
    void screencapper$refresh();
    *///?}
}
