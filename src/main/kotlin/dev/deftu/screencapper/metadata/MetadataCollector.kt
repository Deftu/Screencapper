package dev.deftu.screencapper.metadata

/**
 * Gathers what's known about the game at the moment a screenshot is taken.
 *
 * Called on the render thread, once per capture, before the pixels are read. Keys are PNG text chunk keywords (Latin-1,
 * 1-79 characters); values are free text.
 */
object MetadataCollector {
    @JvmStatic
    fun collect(): Map<String, String> = emptyMap()
}
