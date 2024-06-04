package dev.deftu.screencapper.gui.preview

import dev.deftu.screencapper.Screencapper

enum class PreviewPosition(
    val displayName: String
) {
    TOP_LEFT("${dev.deftu.screencapper.Screencapper.ID}.config_option.general.preview_position.top_left"),
    TOP_RIGHT("${dev.deftu.screencapper.Screencapper.ID}.config_option.general.preview_position.top_right"),
    BOTTOM_LEFT("${dev.deftu.screencapper.Screencapper.ID}.config_option.general.preview_position.bottom_left"),
    BOTTOM_RIGHT("${dev.deftu.screencapper.Screencapper.ID}.config_option.general.preview_position.bottom_right");

    fun isLeft() =
        this == TOP_LEFT || this == BOTTOM_LEFT
}
