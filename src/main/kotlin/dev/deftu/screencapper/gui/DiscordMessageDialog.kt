@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.deftu.screencapper.upload.DiscordMessage
import dev.deftu.screencapper.upload.DiscordOptions
import dev.deftu.screencapper.upload.ShotDetails
import dev.deftu.screencapper.upload.TemplateValues
import dev.deftu.screencapper.upload.UploadTemplate
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ColorSwatchButton
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.LabeledField
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import java.time.Instant
import java.time.OffsetDateTime

private val SAMPLE_DETAILS = ShotDetails(
    player = "Steve",
    world = "New World",
    dimension = "overworld",
    biome = "cherry grove",
    coords = "120, 64, -38",
    version = "26.3",
    takenAt = OffsetDateTime.now().withNano(0),
)

private val PLACE_TOKENS = listOf("{player}", "{world}", "{dimension}", "{biome}", "{coords}", "{version}")
private val TOKENS = listOf("{name}", "{date}", "{time}") + PLACE_TOKENS

private enum class TextSlot(val limit: Int) {
    CONTENT(DiscordMessage.CONTENT_LIMIT),
    TITLE(DiscordMessage.TITLE_LIMIT),
    DESCRIPTION(DiscordMessage.DESCRIPTION_LIMIT),
    FOOTER(DiscordMessage.FOOTER_LIMIT),
}

private fun DiscordOptions.text(slot: TextSlot): String = when (slot) {
    TextSlot.CONTENT -> content
    TextSlot.TITLE -> title
    TextSlot.DESCRIPTION -> description
    TextSlot.FOOTER -> footer
}

private fun DiscordOptions.withText(slot: TextSlot, text: String): DiscordOptions = when (slot) {
    TextSlot.CONTENT -> copy(content = text)
    TextSlot.TITLE -> copy(title = text)
    TextSlot.DESCRIPTION -> copy(description = text)
    TextSlot.FOOTER -> copy(footer = text)
}

private class SlotState(initial: String) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    val source = MutableInteractionSource()
    val focus = FocusRequester()
}

@Composable
internal fun DiscordMessageEditor(
    options: DiscordOptions,
    onChange: (DiscordOptions) -> Unit,
    keepMetadata: Boolean,
    onKeepMetadata: (Boolean) -> Unit,
) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val slots = remember { TextSlot.entries.associateWith { SlotState(options.text(it)) } }
    var target by remember { mutableStateOf(TextSlot.CONTENT) }
    var embedOpen by remember { mutableStateOf(options.embed) }
    var moreOpen by remember { mutableStateOf(false) }

    for ((slot, state) in slots) {
        LaunchedEffect(state) {
            state.source.interactions.collect { if (it is FocusInteraction.Focus) target = slot }
        }
    }

    @Composable
    fun SlotField(slot: TextSlot, label: String, placeholder: String, modifier: Modifier = Modifier, multiLine: Boolean = false) {
        val state = slots.getValue(slot)
        LabeledField(label, modifier) {
            TextField(
                state.value,
                { next ->
                    state.value = next
                    if (next.text != options.text(slot)) onChange(options.withText(slot, next.text))
                },
                Modifier.fillMaxWidth(),
                placeholder = placeholder,
                singleLine = !multiLine,
                maxLength = slot.limit,
                label = label,
                interactionSource = state.source,
                focusRequester = state.focus,
            )
        }
    }

    fun insert(token: String) {
        val slot = if (options.embed && embedOpen) target else TextSlot.CONTENT
        val state = slots.getValue(slot)
        val (text, cursor) = TemplateInsert.insert(state.value.text, state.value.selection.start, state.value.selection.end, token, slot.limit) ?: return
        state.value = TextFieldValue(text, TextRange(cursor))
        onChange(options.withText(slot, text))
        runCatching { state.focus.requestFocus() }
    }

    SlotField(TextSlot.CONTENT, "Message", "Optional, like: {player} in {world}", Modifier.fillMaxWidth(), multiLine = true)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        for (token in TOKENS) Chip(token, false, { insert(token) }, size = ControlSize.Compact, contentDescription = "Insert $token")
    }
    val usesPlace = TextSlot.entries.any { slot -> PLACE_TOKENS.any { options.text(slot).contains(it) } }
    if (usesPlace && !keepMetadata) Hint("{player} and the place tokens stay empty until you turn on Keep metadata under More options.", "info")
    DiscordPreview(options, keepMetadata)

    CollapsibleSection("Embed", embedOpen, { embedOpen = !embedOpen }) {
        ToggleRow("Post as embed", options.embed, { onChange(options.copy(embed = it)) }, Modifier.fillMaxWidth(), description = "A card with a title, colour and the screenshot inside.")
        if (options.embed) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                SlotField(TextSlot.TITLE, "Title", "Optional", Modifier.weight(1f))
                ColorSwatchButton(
                    Color(options.color or 0xFF000000.toInt()),
                    { onChange(options.copy(color = it.toArgb() and 0xFFFFFF)) },
                    label = "Colour",
                    hasAlpha = false,
                    onReset = { onChange(options.copy(color = DiscordMessage.DEFAULT_COLOR)) },
                )
            }
            SlotField(TextSlot.DESCRIPTION, "Description", "Optional", Modifier.fillMaxWidth(), multiLine = true)
            SlotField(TextSlot.FOOTER, "Footer", "Optional", Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                ToggleRow("Screenshot inside", options.imageInEmbed, { onChange(options.copy(imageInEmbed = it)) }, Modifier.weight(1f))
                ToggleRow("Time taken", options.timestamp, { onChange(options.copy(timestamp = it)) }, Modifier.weight(1f))
            }
        }
    }

    CollapsibleSection("More options", moreOpen, { moreOpen = !moreOpen }) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            LabeledField("Bot name", Modifier.weight(1f)) {
                TextField(options.username, { onChange(options.copy(username = it)) }, placeholder = "Webhook default", maxLength = DiscordMessage.USERNAME_LIMIT, label = "Bot name")
            }
            LabeledField("Avatar link", Modifier.weight(1f)) {
                TextField(options.avatarUrl, { onChange(options.copy(avatarUrl = it.trim())) }, placeholder = "Webhook default", label = "Avatar link")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            ToggleRow("Silent", options.silent, { onChange(options.copy(silent = it)) }, Modifier.weight(1f), description = "No push or desktop notification.")
            ToggleRow("Allow mentions", options.allowMentions, { onChange(options.copy(allowMentions = it)) }, Modifier.weight(1f), description = "Lets @everyone, roles and users ping.")
        }
        if (!options.embed) {
            ToggleRow("Hide link previews", options.hideLinkPreviews, { onChange(options.copy(hideLinkPreviews = it)) }, Modifier.fillMaxWidth(), description = "Links in the message won't expand.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            LabeledField("Forum post title", Modifier.weight(1f)) {
                TextField(options.threadName, { onChange(options.copy(threadName = it)) }, placeholder = "Forum channels only", maxLength = DiscordMessage.THREAD_NAME_LIMIT, label = "Forum post title")
            }
            LabeledField("Thread ID", Modifier.weight(1f)) {
                TextField(options.threadId, { value -> onChange(options.copy(threadId = value.filter { it.isDigit() })) }, placeholder = "Post into a thread", label = "Thread ID")
            }
        }
        ToggleRow("Keep metadata", keepMetadata, onKeepMetadata, Modifier.fillMaxWidth(), description = "Fills {player} and the place tokens, and leaves those details in the file.")
    }
}

@Composable
private fun DiscordPreview(options: DiscordOptions, keepMetadata: Boolean) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val values = TemplateValues.now("2026-01-01_12.00.00.png", Instant.now(), Instant.now(), if (keepMetadata) SAMPLE_DETAILS else ShotDetails.NONE)
    fun expand(text: String) = UploadTemplate.expand(text, values)
    val embed = options.embed
    Column(
        Modifier.fillMaxWidth().themedSurface(SurfaceKind.Field).padding(horizontal = spacing.l, vertical = spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Text("Preview · ${options.username.ifBlank { "Webhook" }}", Modifier, theme.textColorSecondary, OneConfigTheme.typography.caption)
        if (options.content.isNotBlank()) Text(expand(options.content), Modifier, theme.textColor, OneConfigTheme.typography.body)
        if (embed) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Color(options.color or 0xFF000000.toInt())))
                Column(Modifier.padding(start = spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    if (options.title.isNotBlank()) Text(expand(options.title), Modifier, theme.textColor, OneConfigTheme.typography.label)
                    if (options.description.isNotBlank()) Text(expand(options.description), Modifier, theme.textColor, OneConfigTheme.typography.small)
                    if (options.imageInEmbed) Hint("Screenshot", "maximise")
                    val footer = listOfNotNull(options.footer.takeIf { it.isNotBlank() }?.let(::expand), "Today at 12:00".takeIf { options.timestamp })
                    if (footer.isNotEmpty()) Text(footer.joinToString(" · "), Modifier, theme.textColorSecondary, OneConfigTheme.typography.caption)
                }
            }
        }
        if (!embed || !options.imageInEmbed) Hint("Screenshot attached", "maximise")
    }
}
