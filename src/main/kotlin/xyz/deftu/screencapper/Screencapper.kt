package xyz.deftu.screencapper

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gg.essential.universal.UDesktop
import net.fabricmc.api.ClientModInitializer
//#if MC>=11900
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
//#else
//$$ import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager
//$$ import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource
//#endif
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import okhttp3.OkHttpClient
import xyz.deftu.screencapper.config.ScreencapperConfig
import xyz.deftu.screencapper.config.ShareXConfig
import xyz.deftu.screencapper.gui.preview.ScreenshotPreview
import xyz.deftu.screencapper.utils.ChatHelper
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
        ShareXConfig.initialize(configDirectory)
        ScreenshotPreview.initialize()
        registerCommand(ClientCommandManager.literal("screenshot")
            .then(ClientCommandManager.argument("action", StringArgumentType.word())
                .executes { ctx ->
                    when (StringArgumentType.getString(ctx, "action").lowercase()) {
                        "upload" -> {
                            UDesktop.setClipboardString(ScreenshotHandler.upload().toString())
                            1
                        }
                        "copy" -> {
                            ScreenshotHandler.copy()
                            1
                        }
                        "delete" -> {
                            ScreenshotHandler.delete()
                            1
                        }
                        else -> 0
                    }
                }))
    }

    fun sendMessage(message: Text, prefix: Boolean = true) {
        val text = ChatHelper.createLiteralText("")
        if (prefix) text.append(ChatHelper.createLiteralText("[$NAME]")
            .formatted(Formatting.AQUA))
        text.append(" ").formatted(Formatting.RESET)
        text.append(message)
        MinecraftClient.getInstance().inGameHud.chatHud.addMessage(text)
    }
    fun sendMessage(message: String, prefix: Boolean = true) = Screencapper.sendMessage(ChatHelper.createLiteralText(message), prefix)

    private fun registerCommand(builder: LiteralArgumentBuilder<FabricClientCommandSource>) {
        //#if MC>=11900
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
            dispatcher.register(builder)
        }
        //#else
        //$$ ClientCommandManager.DISPATCHER.register(builder)
        //#endif
    }
}
