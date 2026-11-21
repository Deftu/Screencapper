@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeaderDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

@Composable
internal fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = OneConfigTheme.current
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title,
            expanded = expanded,
            onToggle = onToggle,
            rule = true,
            textStyle = SectionHeaderDefaults.labelStyle,
            color = theme.textColorSecondary,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = Motion.enter(expandVertically()),
            exit = Motion.exit(shrinkVertically()),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = theme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(theme.spacing.l),
                content = content,
            )
        }
    }
}

@Composable
internal fun Hint(text: String, icon: String? = null, color: Color = Color.Unspecified) {
    val theme = OneConfigTheme.current
    val tint = if (color == Color.Unspecified) theme.textColorSecondary else color
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
        if (icon != null) Icon(icon, null, Modifier.size(12.dp), tint)
        Text(text, Modifier, tint, OneConfigTheme.typography.caption)
    }
}
