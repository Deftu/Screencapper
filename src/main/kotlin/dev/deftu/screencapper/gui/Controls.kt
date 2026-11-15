@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

@Composable
fun KeyValueList(
    pairs: List<Pair<String, String>>,
    keyPlaceholder: String,
    valuePlaceholder: String,
    addLabel: String,
    onChange: (List<Pair<String, String>>) -> Unit,
) {
    val spacing = OneConfigTheme.current.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        pairs.forEachIndexed { index, (key, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                TextField(key, { onChange(pairs.toMutableList().apply { set(index, it to value) }) }, Modifier.weight(1f), placeholder = keyPlaceholder, label = keyPlaceholder)
                TextField(value, { onChange(pairs.toMutableList().apply { set(index, key to it) }) }, Modifier.weight(1.6f), placeholder = valuePlaceholder, label = valuePlaceholder)
                IconButton("trash", "Remove", { onChange(pairs.toMutableList().apply { removeAt(index) }) }, size = ControlSize.Compact, danger = true)
            }
        }
        TextButton(addLabel, "plus", { onChange(pairs + ("" to "")) })
    }
}
