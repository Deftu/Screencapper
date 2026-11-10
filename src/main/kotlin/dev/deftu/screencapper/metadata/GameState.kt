package dev.deftu.screencapper.metadata

import dev.deftu.screencapper.mixin.ChatAccessor
import net.minecraft.client.Minecraft
import kotlin.math.floor

//? if >=26.1 {
import net.minecraft.client.multiplayer.chat.GuiMessage
//?} else if >=1.21 {
/*import net.minecraft.client.GuiMessage
*///?} else {
/*import net.minecraft.client.gui.ChatMessage
import net.minecraft.util.math.BlockPos
*///?}

/**
 * Version-specific reads of the game state for [MetadataCollector]. Render thread only; each getter returns null when
 * the value isn't available, for example outside a world.
 *
 * Vanilla members used, checked with javap against each version's mapped jar:
 * - 1.21.1-26.3: `Minecraft.player`, `Minecraft.level`, `Minecraft.getSingleplayerServer()`,
 *   `Minecraft.getCurrentServer()`, `Minecraft.getUser().getName()`, `ServerData.name/ip`,
 *   `MinecraftServer.getWorldData().getLevelName()`, `Level.dimension()`, `ResourceKey.location()` (renamed
 *   `identifier()` in 1.21.11), `LevelReader.getBiome(BlockPos)`, `Holder.getRegisteredName()`,
 *   `Entity.blockPosition()/getX()/getY()/getZ()`, `GuiMessage.content()` (moved to `client.multiplayer.chat` in 26.1),
 *   `Gui.getChat()` (`Gui.hud.getChat()` from 26.2).
 * - 1.8.9 (Feather): `Minecraft.player/world`, `Minecraft.getServer().getWorldName()`,
 *   `Minecraft.getCurrentServerEntry()` with `ServerListEntry.name/ip`, `Minecraft.getSession().getUsername()`,
 *   `World.dimension.getId()/getName()`, `World.getBiome(BlockPos).name`, `new BlockPos(Entity)`, `Entity.x/y/z`,
 *   `ChatMessage.getText().getString()`, `Gui.getChat()`.
 *
 * Chat entries come from [ChatAccessor], newest first.
 */
internal object GameState {
    fun worldName(): String? {
        val minecraft = Minecraft.getInstance()
        //? if >=1.21 {
        val server = minecraft.singleplayerServer ?: return null
        if (minecraft.level == null) return null
        return server.worldData.levelName
        //?} else {
        /*val server = minecraft.server ?: return null
        if (minecraft.world == null) return null
        return server.worldName
        *///?}
    }

    /** The server's name and address, or null in singleplayer */
    fun server(): Pair<String?, String?>? {
        val minecraft = Minecraft.getInstance()
        //? if >=1.21 {
        if (minecraft.singleplayerServer != null) return null
        val server = minecraft.currentServer ?: return null
        //?} else {
        /*if (minecraft.server != null) return null
        val server = minecraft.currentServerEntry ?: return null
        *///?}
        return server.name to server.ip
    }

    fun dimension(): String? {
        //? if >=1.21 {
        val level = Minecraft.getInstance().level ?: return null
        //? if >=1.21.11 {
        return level.dimension().identifier().toString()
        //?} else {
        /*return level.dimension().location().toString()
        *///?}
        //?} else {
        /*val world = Minecraft.getInstance().world ?: return null
        return when (world.dimension.id) {
            -1 -> "minecraft:the_nether"
            0 -> "minecraft:overworld"
            1 -> "minecraft:the_end"
            else -> world.dimension.name
        }
        *///?}
    }

    fun biome(): String? {
        val minecraft = Minecraft.getInstance()
        //? if >=1.21 {
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        return level.getBiome(player.blockPosition()).registeredName
        //?} else {
        /*val player = minecraft.player ?: return null
        val world = minecraft.world ?: return null
        return world.getBiome(BlockPos(player))?.name
        *///?}
    }

    fun playerName(): String? {
        val minecraft = Minecraft.getInstance()
        //? if >=1.21 {
        return minecraft.user.name
        //?} else {
        /*return minecraft.session.username
        *///?}
    }

    /** Block coordinates as `x, y, z` */
    fun coordinates(): String? {
        val player = Minecraft.getInstance().player ?: return null
        return "${floor(player.x).toInt()}, ${floor(player.y).toInt()}, ${floor(player.z).toInt()}"
    }

    /** Up to [count] of the latest chat lines as plain text, oldest first */
    fun recentChat(count: Int): List<String> {
        if (count <= 0) return emptyList()
        val messages = chat()?.`screencapper$messages`() ?: return emptyList()
        val lines = ArrayList<String>(minOf(count, messages.size))
        for (entry in messages) {
            if (lines.size >= count) break
            //? if >=1.21 {
            val text = (entry as? GuiMessage)?.content()?.string
            //?} else {
            /*val text = (entry as? ChatMessage)?.text?.string
            *///?}
            if (!text.isNullOrBlank()) lines += text
        }
        lines.reverse()
        return lines
    }

    private fun chat(): ChatAccessor? {
        // A missing gui throws here, which the collector treats as no chat.
        val gui = Minecraft.getInstance().gui
        //? if >=26.2 {
        return gui.hud.chat as Any as? ChatAccessor
        //?} else {
        /*return gui.chat as Any as? ChatAccessor
        *///?}
    }
}
