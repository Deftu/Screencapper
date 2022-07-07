package xyz.deftu.screencapper

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import okhttp3.OkHttpClient
import xyz.deftu.screencapper.config.ScreencapperConfig
import xyz.deftu.screencapper.config.ShareXConfig
import xyz.deftu.screencapper.gui.preview.ScreenshotPreview
import java.io.File

object Screencapper : ClientModInitializer {
    const val NAME = "@MOD_NAME@"
    const val ID = "@MOD_ID@"

    val httpClient = OkHttpClient.Builder()
        .addInterceptor {
            it.proceed(it.request().newBuilder().addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36").build())
        }.build()
    val configDirectory = File(FabricLoader.getInstance().configDir.toFile(), "Deftu")

    override fun onInitializeClient() {
        ScreencapperConfig.initialize()
        ShareXConfig.initialize(configDirectory)
        ScreenshotPreview.initialize()
    }
}
