@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.polyfrost.oneconfig.api.ui.v1.components.Tooltip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.onClick
import org.polyfrost.oneconfig.api.ui.v1.components.rememberInteractionSource
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

internal val DANGER = Color(0xFFE35B5B)

enum class ButtonHierarchy { PRIMARY, SECONDARY }

/** Matches OneConfig's own button: primary fills with the accent, secondary sits on the component surface; hover dims rather than tints */
@Composable
fun SmallButton(
    label: String,
    icon: String?,
    modifier: Modifier = Modifier,
    hierarchy: ButtonHierarchy = ButtonHierarchy.SECONDARY,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = OneConfigTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val primaryFill = if (destructive) DANGER else OneConfigTheme.accent
    val background by animateColorAsState(
        when (hierarchy) {
            ButtonHierarchy.PRIMARY -> primaryFill.copy(alpha = if (hovered && enabled) 0.75f else 1f)
            ButtonHierarchy.SECONDARY -> theme.componentBackground
        },
    )
    val border by animateColorAsState(
        when {
            hierarchy == ButtonHierarchy.PRIMARY -> Color.Transparent
            hovered && enabled -> theme.textColorSecondary.copy(alpha = 0.6f)
            else -> theme.borderColor
        },
    )
    val foreground = when {
        hierarchy == ButtonHierarchy.PRIMARY -> theme.accentTextColor
        destructive -> DANGER
        else -> theme.textColor
    }.let { if (enabled) it else it.copy(alpha = 0.5f) }

    Row(
        modifier
            .height(32.dp)
            .background(background, theme.buttonShape)
            .border(1.dp, border, theme.buttonShape)
            .hoverable(interaction)
            .let { if (enabled) it.pointerHoverIcon(PointerIcon.Hand) else it }
            .onClick(interaction, enabled, onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, foreground, Modifier.size(16.dp))
        Text(label, Modifier, foreground, 13.sp, null, BaselineShift.None, FontWeight.Normal, TextAlign.Center, 1, TextOverflow.Ellipsis)
    }
}

/** The design system's Dark Tag: a fixed 18px capsule for status, never an action. Over a screenshot it stays dark; elsewhere it takes theme surface colours so it reads in light themes. */
@Composable
fun StatusTag(label: String, modifier: Modifier = Modifier, overImage: Boolean = true) {
    val theme = OneConfigTheme.current
    val shape = theme.checkBoxShape
    val fill = if (overImage) Color.Black.copy(alpha = 0.3f) else theme.componentBackground
    val border = if (overImage) Color(0xFF6B7280).copy(alpha = 0.7f) else theme.borderColor
    val text = if (overImage) Color.White else theme.textColorSecondary
    Box(
        modifier
            .height(18.dp)
            .background(fill, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, Modifier, text, 10.sp, FontWeight.Medium)
    }
}

@Composable
fun TextField(value: String, placeholder: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) theme.textColorSecondary.copy(alpha = 0.6f) else theme.borderColor)
    val focus = remember { FocusRequester() }
    Box(
        modifier
            .height(32.dp)
            .background(theme.componentBackground, theme.buttonShape)
            .border(1.dp, border, theme.buttonShape)
            .hoverable(interaction)
            .clickable(interaction, indication = null) { runCatching { focus.requestFocus() } }
            .pointerHoverIcon(PointerIcon.Text)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, Modifier, theme.textColorSecondary, 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = theme.textColor, fontSize = 12.sp, fontFamily = theme.fontFamily),
            cursorBrush = SolidColor(OneConfigTheme.accent),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, Modifier.padding(bottom = 6.dp), OneConfigTheme.current.textColorSecondary, 12.sp, FontWeight.Medium)
}

@Composable
fun KeyValueList(
    pairs: List<Pair<String, String>>,
    keyPlaceholder: String,
    valuePlaceholder: String,
    addLabel: String,
    onChange: (List<Pair<String, String>>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pairs.forEachIndexed { index, (key, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField(key, keyPlaceholder, Modifier.weight(1f)) { onChange(pairs.toMutableList().apply { set(index, it to value) }) }
                TextField(value, valuePlaceholder, Modifier.weight(1.6f)) { onChange(pairs.toMutableList().apply { set(index, key to it) }) }
                IconAction("trash", "Remove", destructive = true) { onChange(pairs.toMutableList().apply { removeAt(index) }) }
            }
        }
        SmallButton(addLabel, "plus") { onChange(pairs + ("" to "")) }
    }
}

enum class IconActionStyle { GHOST, OVERLAY }

/** The design system's icon-only button: a square with 7px padding, 28px (14px glyph) or 32px (18px glyph) */
@Composable
fun IconAction(
    icon: String,
    tooltip: String?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    style: IconActionStyle = IconActionStyle.GHOST,
    destructive: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = OneConfigTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val size = if (large) 32.dp else 28.dp
    val glyph = if (large) 18.dp else 14.dp
    val idle = when (style) {
        IconActionStyle.GHOST -> Color.Transparent
        IconActionStyle.OVERLAY -> Color.Black.copy(alpha = 0.55f)
    }
    val background by animateColorAsState(
        when {
            hovered && enabled && destructive -> DANGER.copy(alpha = if (style == IconActionStyle.OVERLAY) 0.9f else 0.18f)
            hovered && enabled -> if (style == IconActionStyle.OVERLAY) idle else theme.componentBackground
            else -> idle
        },
    )
    val border by animateColorAsState(
        when {
            !hovered || !enabled -> Color.Transparent
            style == IconActionStyle.OVERLAY -> Color.White.copy(alpha = 0.6f)
            else -> theme.borderColor
        },
    )
    val baseTint = when {
        active -> OneConfigTheme.accent
        style == IconActionStyle.OVERLAY -> Color.White
        else -> theme.textColor
    }
    val tint = when {
        !enabled -> baseTint.copy(alpha = 0.35f)
        hovered || active -> if (destructive && style == IconActionStyle.GHOST) DANGER else baseTint
        else -> baseTint.copy(alpha = 0.7f)
    }

    val button: @Composable () -> Unit = {
        Box(
            modifier
                .size(size)
                .background(background, theme.buttonShape)
                .border(1.dp, border, theme.buttonShape)
                .hoverable(interaction)
                .let { if (enabled) it.pointerHoverIcon(PointerIcon.Hand) else it }
                .onClick(interaction, enabled, onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, tint, Modifier.size(glyph))
        }
    }
    if (tooltip == null) button() else Tooltip({ Text(tooltip, Modifier, theme.textColor, 12.sp) }, Modifier, Alignment.BottomCenter, button)
}
