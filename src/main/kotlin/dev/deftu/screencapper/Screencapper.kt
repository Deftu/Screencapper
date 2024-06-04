package dev.deftu.screencapper

import com.mojang.brigadier.arguments.StringArgumentType
import dev.deftu.lib.client.ClientCommandHelper
import gg.essential.universal.UDesktop
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import okhttp3.OkHttpClient
import dev.deftu.lib.utils.TextHelper
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.config.ShareXConfig
import dev.deftu.screencapper.gui.preview.ScreenshotPreview
import java.io.File

object Screencapper : ClientModInitializer {
    const val NAME = "Screencapper"
    const val ID = "screencapper"

    val httpClient = OkHttpClient.Builder()
        .addInterceptor {
            it.proceed(it.request().newBuilder().addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36").build())
        }.build()
    val configDirectory = File(FabricLoader.getInstance().configDir.toFile(), "Deftu")

    override fun onInitializeClient() {
        ScreencapperConfig.initialize()
        ShareXConfig.initialize(dev.deftu.screencapper.Screencapper.configDirectory)
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

    fun sendMessage(message: String, prefix: Boolean = true) = dev.deftu.screencapper.Screencapper.sendMessage(TextHelper.createLiteralText(message), prefix)
}
