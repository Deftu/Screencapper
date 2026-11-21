package dev.deftu.screencapper.gui.editor

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

internal object EditorIcons {
    private const val ROOT = "/assets/screencapper/icons"

    private val NAMES = listOf(
        "rotate-left", "rotate-right", "flip-horizontal", "flip-vertical",
        "shapes", "steps", "arrow", "rectangle", "ellipse", "line",
        "eyedropper", "pen", "marker",
    )

    fun path(name: String): String = "$ROOT/$name.svg"

    @OptIn(ExperimentalOneConfigUiApi::class)
    fun register() {
        OneConfigTheme.registerPixelIcons(NAMES.associate { path(it) to "$ROOT/pixel/$it.svg" })
    }
}
