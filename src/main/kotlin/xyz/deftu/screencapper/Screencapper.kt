package xyz.deftu.screencapper

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager
import net.fabricmc.loader.api.FabricLoader
import okhttp3.OkHttpClient
import xyz.deftu.screencapper.config.ScreencapperConfig
import xyz.deftu.screencapper.config.ShareXConfig
import xyz.deftu.screencapper.gui.preview.ScreenshotPreview
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
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("screenshot")
            .then(ClientCommandManager.argument("action", StringArgumentType.word())
                .executes { ctx ->
                    when (StringArgumentType.getString(ctx, "action").lowercase()) {
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
}
