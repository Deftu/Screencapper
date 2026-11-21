package dev.deftu.screencapper.screenshot.naming

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.metadata.GameState
import net.fabricmc.loader.api.FabricLoader
import java.time.LocalDateTime

object ScreenshotNames {
    const val HIGH_RES_FALLBACK = "huge_%time%"

    private val gameVersion: String? by lazy {
        runCatching { FabricLoader.getInstance().getModContainer("minecraft").orElse(null)?.metadata?.version?.friendlyString }.getOrNull()
    }

    fun normal(): String = NameTemplate.expand(ScreencapperConfig.fileName, current())

    fun highRes(): String = NameTemplate.expand(ScreencapperConfig.highResFileName, current(), HIGH_RES_FALLBACK)

    fun current(): NameValues = NameValues(
        time = LocalDateTime.now(),
        world = read { GameState.worldName() ?: GameState.server()?.first },
        dimension = read { GameState.dimension() },
        player = read { GameState.playerName() },
        version = gameVersion,
    )

    private fun read(value: () -> String?): String? = try {
        value()?.takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        Screencapper.LOGGER.debug("Couldn't read a value for the screenshot name", e)
        null
    }
}
