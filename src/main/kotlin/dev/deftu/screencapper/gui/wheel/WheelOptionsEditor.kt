@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui.wheel

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.CaptureKind
import dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice
import dev.deftu.screencapper.screenshot.panorama.PanoramaLayout
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.LabeledField
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSeparator
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

private fun storedOptions(): List<WheelOption> = WheelOptions.decode(ScreencapperConfig.wheelOptions)

@Composable
fun WheelOptionsEditor(prop: Property<*>) {
    var options by remember(prop) { mutableStateOf(storedOptions()) }
    var written by remember(prop) { mutableStateOf(ScreencapperConfig.wheelOptions) }
    var expanded by remember(prop) { mutableStateOf<String?>(null) }
    LaunchedEffect(prop) {
        while (true) {
            delay(1000)
            val stored = ScreencapperConfig.wheelOptions
            if (stored != written) {
                written = stored
                options = storedOptions()
            }
        }
    }

    fun store(json: String) {
        written = json
        @Suppress("UNCHECKED_CAST")
        (prop as Property<String>).set(json)
    }

    fun commit(next: List<WheelOption>) {
        options = next
        store(WheelOptions.encode(next))
    }

    fun reset() {
        expanded = null
        store("")
        options = storedOptions()
    }

    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val customWidth = ScreencapperConfig.highResWidth.toInt()
    val customHeight = ScreencapperConfig.highResHeight.toInt()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        options.forEachIndexed { index, option ->
            key(option.id) {
                OptionCard(
                    option = option,
                    detail = option.slot(customWidth, customHeight).detail,
                    position = index,
                    count = options.size,
                    isExpanded = expanded == option.id,
                    canRemove = WheelOptions.canRemove(options),
                    onToggle = { expanded = if (expanded == option.id) null else option.id },
                    onMove = { delta -> commit(options.moved(index, delta)) },
                    onRemove = {
                        if (WheelOptions.canRemove(options)) commit(options.filterNot { it.id == option.id })
                    },
                    onChange = { updated -> commit(options.map { if (it.id == updated.id) updated else it }) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            TextButton("Add option", "plus", {
                if (WheelOptions.canAdd(options)) {
                    val created = WheelOptions.create()
                    commit(options + created)
                    expanded = created.id
                }
            }, style = ControlStyle.Primary, enabled = WheelOptions.canAdd(options))
            TextButton("Reset to defaults", "refresh", { reset() }, enabled = written.isNotEmpty())
            Text(
                "${options.size} of ${WheelOptions.MAX_OPTIONS} slots, at least ${WheelOptions.MIN_OPTIONS}",
                Modifier,
                theme.textColorSecondary,
                OneConfigTheme.typography.small,
            )
        }
    }
}

@Composable
private fun OptionCard(
    option: WheelOption,
    detail: String,
    position: Int,
    count: Int,
    isExpanded: Boolean,
    canRemove: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onChange: (WheelOption) -> Unit,
) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val first = position == 0
    val last = position == count - 1
    Column(
        Modifier
            .fillMaxWidth()
            .themedSurface(SurfaceKind.Field)
            .animateContentSize()
            .contextMenu {
                listOf(
                    MenuAction(if (isExpanded) "Collapse" else "Edit", onToggle, icon = if (isExpanded) "up" else "text-input"),
                    MenuAction("Move up", { onMove(-1) }, icon = "up", enabled = !first),
                    MenuAction("Move down", { onMove(1) }, icon = "down", enabled = !last),
                    MenuSeparator,
                    MenuAction("Remove", onRemove, icon = "trash", danger = true, enabled = canRemove),
                )
            }
            .padding(horizontal = spacing.l, vertical = spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            Text("${position + 1}", Modifier.width(14.dp), theme.textColorSecondary, OneConfigTheme.typography.label)
            Icon(option.icon, null, Modifier.size(18.dp), theme.textColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(option.label.ifBlank { option.shot.kind.label }, Modifier, theme.textColor, OneConfigTheme.typography.label)
                Text("${option.shot.kind.label} · $detail", Modifier, theme.textColorSecondary, OneConfigTheme.typography.small)
            }
            IconButton("up", "Move up", { onMove(-1) }, enabled = !first, size = ControlSize.Compact)
            IconButton("down", "Move down", { onMove(1) }, enabled = !last, size = ControlSize.Compact)
            IconButton("text-input", if (isExpanded) "Collapse" else "Edit", onToggle, size = ControlSize.Compact, selected = isExpanded)
            IconButton("trash", "Remove option", onRemove, enabled = canRemove, size = ControlSize.Compact, danger = true)
        }

        if (isExpanded) {
            Spacer(Modifier.padding(top = spacing.l))
            OptionForm(option, onChange)
        }
    }
}

@Composable
private fun OptionForm(option: WheelOption, onChange: (WheelOption) -> Unit) {
    val spacing = OneConfigTheme.current.spacing
    Column(Modifier.fillMaxWidth().padding(start = 28.dp, bottom = spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            LabeledField("Label", Modifier.width(220.dp)) {
                TextField(option.label, { onChange(option.copy(label = it.take(WheelOptions.MAX_LABEL))) }, placeholder = option.shot.kind.label, label = "Label")
            }
            LabeledField("Kind") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    for (kind in WheelShotKind.entries) Chip(kind.label, option.shot.kind == kind, { onChange(option.withKind(kind)) })
                }
            }
        }

        LabeledField("Icon") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                for (icon in WheelOptions.ICONS) {
                    IconButton(icon, icon, { onChange(option.copy(icon = icon)) }, size = ControlSize.Compact, selected = option.icon == icon)
                }
            }
        }

        ShotFields(option, onChange)

        if (option.shot !is WheelShot.Panorama) {
            LabeledField("Hide for this shot") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                    ToggleRow("HUD", option.hideHud, { onChange(option.copy(hideHud = it)) }, Modifier.width(140.dp))
                    ToggleRow("Chat", option.hideChat || option.hideHud, { onChange(option.copy(hideChat = it)) }, Modifier.width(140.dp), enabled = !option.hideHud)
                    ToggleRow("Hand", option.hideHand || option.hideHud, { onChange(option.copy(hideHand = it)) }, Modifier.width(140.dp), enabled = !option.hideHud)
                }
            }

            LabeledField("Compression") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Chip(if (option.shot.captureKind == CaptureKind.HIGH_RES) "High-res default" else "Default", option.compression == null, { onChange(option.copy(compression = null)) })
                    for (choice in CompressionChoice.PRESETS) Chip(choice.label, option.compression == choice, { onChange(option.copy(compression = choice)) })
                }
            }
        }
    }
}

@Composable
private fun ShotFields(option: WheelOption, onChange: (WheelOption) -> Unit) {
    val spacing = OneConfigTheme.current.spacing
    when (val shot = option.shot) {
        is WheelShot.HighRes -> Column(verticalArrangement = Arrangement.spacedBy(spacing.l)) {
            ToggleRow(
                "Use the High-res size setting",
                shot.usesConfiguredSize,
                { useSetting -> onChange(option.copy(shot = if (useSetting) WheelShot.HighRes(0, 0) else WheelShot.HighRes(3840, 2160))) },
                Modifier.width(300.dp),
            )
            if (!shot.usesConfiguredSize) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                    NumberField("Width (px)", shot.width) { onChange(option.copy(shot = shot.copy(width = it))) }
                    NumberField("Height (px)", shot.height) { onChange(option.copy(shot = shot.copy(height = it))) }
                }
            }
        }

        is WheelShot.Timer -> NumberField("Seconds", shot.seconds) { onChange(option.copy(shot = shot.copy(seconds = it))) }

        is WheelShot.Panorama -> LabeledField("Face size") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                for (size in PanoramaLayout.SIZES) Chip("$size × $size", shot.faceSize == size, { onChange(option.copy(shot = shot.copy(faceSize = size))) })
            }
        }

        WheelShot.Normal -> {}
    }
}

@Composable
private fun NumberField(label: String, value: Int, onValue: (Int) -> Unit) {
    var text by remember(label) { mutableStateOf(value.toString()) }
    LabeledField(label, Modifier.width(140.dp)) {
        TextField(text, { input ->
            text = input.filter { it.isDigit() }.take(5)
            text.toIntOrNull()?.takeIf { it > 0 }?.let(onValue)
        }, placeholder = value.toString(), label = label)
    }
}
