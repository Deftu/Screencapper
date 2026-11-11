package dev.deftu.screencapper.metadata

object MetadataKeys {
    const val PREFIX = "Screencapper:"

    const val TIME = "${PREFIX}Time"

    const val WORLD = "${PREFIX}World"

    const val SERVER = "${PREFIX}Server"

    const val SERVER_ADDRESS = "${PREFIX}ServerAddress"

    const val DIMENSION = "${PREFIX}Dimension"

    const val BIOME = "${PREFIX}Biome"

    const val VERSION = "${PREFIX}Version"

    const val PLAYER = "${PREFIX}Player"

    const val COORDINATES = "${PREFIX}Coordinates"

    const val CHAT = "${PREFIX}Chat"

    val ALL = listOf(TIME, WORLD, SERVER, SERVER_ADDRESS, DIMENSION, BIOME, VERSION, PLAYER, COORDINATES, CHAT)
}
