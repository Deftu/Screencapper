package dev.deftu.screencapper

import com.mojang.brigadier.arguments.StringArgumentType
import dev.deftu.lib.client.ClientCommandHelper
import gg.essential.universal.UDesktop
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import dev.deftu.lib.utils.TextHelper
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.config.ShareXConfig
import dev.deftu.screencapper.config.deftuDir
import dev.deftu.screencapper.gui.preview.ScreenshotPreview

object Screencapper : ClientModInitializer {

    const val NAME = "@MOD_NAME@"
    const val ID = "@MOD_ID@"

    override fun onInitializeClient() {
        ScreencapperConfig.initialize()
        ShareXConfig.initialize(deftuDir)
        ScreenshotPreview.initialize()

        ClientCommandHelper.register(
            ClientCommandHelper.create("screenshot")
                .then(ClientCommandHelper.argument("action", StringArgumentType.word())
                    .executes { ctx ->
                        when (StringArgumentType.getString(ctx, "action").lowercase()) {
                            "upload" -> {
                                UDesktop.setClipboardString(
                                    dev.deftu.screencapper.ScreenshotHandler.upload().toString()
                                )
                                1
                            }

                            "copy" -> {
                                dev.deftu.screencapper.ScreenshotHandler.copy()
                                1
                            }

                            "delete" -> {
                                dev.deftu.screencapper.ScreenshotHandler.delete()
                                1
                            }

                            else -> 0
                        }
                    })
        )
    }

    fun sendMessage(message: Text, prefix: Boolean = true) {
        val text = TextHelper.createLiteralText("")
        if (prefix) text.append(TextHelper.createLiteralText("[${dev.deftu.screencapper.Screencapper.NAME}]")
            .formatted(Formatting.AQUA))
        text.append(" ").formatted(Formatting.RESET)
        text.append(message)
        MinecraftClient.getInstance().inGameHud.chatHud.addMessage(text)
    }

}
