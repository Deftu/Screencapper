package dev.deftu.screencapper.metadata

/**
 * Every PNG text keyword Screencapper writes into a screenshot, and the same keys in `config/screencapper/index.json`.
 *
 * Keywords must stay Latin-1 and 1-79 characters long. Values are UTF-8 text.
 */
object MetadataKeys {
    const val PREFIX = "Screencapper:"

    /** When the shot was taken, ISO-8601 with the local offset, for example `2026-10-14T18:22:01+02:00` */
    const val TIME = "${PREFIX}Time"

    /** The singleplayer world's level name */
    const val WORLD = "${PREFIX}World"

    /** The multiplayer server's name from the server list */
    const val SERVER = "${PREFIX}Server"

    /** The multiplayer server's address */
    const val SERVER_ADDRESS = "${PREFIX}ServerAddress"

    /** Dimension id, for example `minecraft:the_nether` */
    const val DIMENSION = "${PREFIX}Dimension"

    /** Biome at the player's feet: an id such as `minecraft:cherry_grove`, or the biome's name on 1.8.9 */
    const val BIOME = "${PREFIX}Biome"

    const val VERSION = "${PREFIX}Version"

    const val PLAYER = "${PREFIX}Player"

    /** Block coordinates as `x, y, z` */
    const val COORDINATES = "${PREFIX}Coordinates"

    /** The most recent chat lines, oldest first, one per line */
    const val CHAT = "${PREFIX}Chat"

    val ALL = listOf(TIME, WORLD, SERVER, SERVER_ADDRESS, DIMENSION, BIOME, VERSION, PLAYER, COORDINATES, CHAT)
}
