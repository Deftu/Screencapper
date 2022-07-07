package xyz.deftu.screencapper.gui.preview

import xyz.deftu.screencapper.Screencapper

enum class PreviewPosition(
    val displayName: String
) {
    TOP_LEFT("${Screencapper.ID}.config_option.general.preview_position.top_left"),
    TOP_RIGHT("${Screencapper.ID}.config_option.general.preview_position.top_right"),
    BOTTOM_LEFT("${Screencapper.ID}.config_option.general.preview_position.bottom_left"),
    BOTTOM_RIGHT("${Screencapper.ID}.config_option.general.preview_position.bottom_right");

    fun isLeft() =
        this == TOP_LEFT || this == BOTTOM_LEFT
}
