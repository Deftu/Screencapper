package dev.deftu.screencapper.metadata

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.ScreencapperConfig
import net.fabricmc.loader.api.FabricLoader
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Gathers what's known about the game at the moment a screenshot is taken.
 *
 * Called on the render thread, once per capture, before the pixels are read. Keys are PNG text chunk keywords (Latin-1,
 * 1-79 characters); values are free text. The key set is [MetadataKeys].
 */
object MetadataCollector {
    private val gameVersion: String? by lazy {
        runCatching { FabricLoader.getInstance().getModContainer("minecraft").orElse(null)?.metadata?.version?.friendlyString }.getOrNull()
    }

    @JvmStatic
    fun collect(): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        try {
            val config = ScreencapperConfig
            fun put(key: String, read: () -> String?) {
                val value = try {
                    read()
                } catch (e: Throwable) {
                    Screencapper.LOGGER.debug("Couldn't read {} for screenshot metadata", key, e)
                    null
                }
                if (!value.isNullOrBlank()) fields[key] = value
            }

            if (config.metadataTime) put(MetadataKeys.TIME) { OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString() }
            if (config.metadataPlace) {
                put(MetadataKeys.WORLD) { GameState.worldName() }
                val server = runCatching { GameState.server() }.getOrNull()
                if (server != null) {
                    put(MetadataKeys.SERVER) { server.first }
                    put(MetadataKeys.SERVER_ADDRESS) { server.second }
                }
            }
            if (config.metadataDimension) put(MetadataKeys.DIMENSION) { GameState.dimension() }
            if (config.metadataBiome) put(MetadataKeys.BIOME) { GameState.biome() }
            if (config.metadataVersion) put(MetadataKeys.VERSION) { gameVersion }
            if (config.metadataPlayer) put(MetadataKeys.PLAYER) { GameState.playerName() }
            if (config.metadataCoordinates) put(MetadataKeys.COORDINATES) { GameState.coordinates() }
            if (config.metadataChat) {
                put(MetadataKeys.CHAT) {
                    GameState.recentChat(config.metadataChatLines.toInt()).joinToString("\n") { it.replace('\n', ' ') }
                }
            }
        } catch (e: Throwable) {
            Screencapper.LOGGER.warn("Couldn't collect screenshot metadata", e)
        }
        return fields
    }
}
