@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.deftu.screencapper.config.ScreencapperConfig
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import kotlin.reflect.KMutableProperty0

private val ACTIONS: List<Pair<String, KMutableProperty0<Boolean>>> = listOf(
    "View" to ScreencapperConfig::chatView,
    "Upload" to ScreencapperConfig::chatUpload,
    "Copy" to ScreencapperConfig::chatCopy,
    "Open" to ScreencapperConfig::chatOpen,
    "Open folder" to ScreencapperConfig::chatOpenFolder,
    "Delete" to ScreencapperConfig::chatDelete,
)

@Composable
internal fun ChatActionsRow() {
    val spacing = OneConfigTheme.current.spacing
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        for ((label, property) in ACTIONS) {
            var shown by remember(property) { mutableStateOf(property.get()) }
            Chip(label, shown, {
                shown = !shown
                property.set(shown)
            }, icon = if (shown) "tick" else null)
        }
    }
}
