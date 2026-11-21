@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.deftu.screencapper.upload.UploaderPresets
import dev.deftu.screencapper.upload.UploaderProfile
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.Card
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.LabeledField
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

private enum class UploaderPreset(val label: String, val icon: String, val detail: String) {
    IMGUR("Imgur", "upload", "Built in. Anonymous links, up to 20 MB."),
    CATBOX("Catbox", "layers", "Built in. Anonymous links, up to 200 MB."),
    DISCORD("Discord webhook", "bell", "Post screenshots to a channel."),
    IMMICH("Immich", "cloud", "Your own photo server."),
    SXCU("Import ShareX file", "folder", "Use a .sxcu uploader."),
    CUSTOM("Custom", "spanner", "Any host that takes a file upload."),
}

@Composable
internal fun AddUploaderDialog(
    defaultId: String,
    onUseBuiltIn: (String) -> Unit,
    onImport: () -> Unit,
    onAdd: (profile: UploaderProfile, edit: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = OneConfigTheme.current
    var step by remember { mutableStateOf<UploaderPreset?>(null) }
    var integration by remember { mutableStateOf<IntegrationDialog?>(null) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var ownerLink by remember { mutableStateOf(false) }

    val problem = when (step) {
        UploaderPreset.DISCORD -> if (first.isBlank()) "" else UploaderPresets.discordProblem(first)
        UploaderPreset.IMMICH -> when {
            first.isBlank() -> ""
            else -> UploaderPresets.immichProblem(first) ?: if (second.isBlank()) "Enter an API key." else null
        }
        UploaderPreset.CUSTOM -> if (second.isBlank()) "" else null
        else -> null
    }

    fun pick(preset: UploaderPreset) {
        when (preset) {
            UploaderPreset.IMGUR -> onUseBuiltIn(UploaderProfile.IMGUR_ID)
            UploaderPreset.CATBOX -> onUseBuiltIn(UploaderProfile.CATBOX_ID)
            UploaderPreset.SXCU -> onImport()
            else -> {
                first = ""
                second = ""
                ownerLink = false
                step = preset
            }
        }
    }

    fun confirm() {
        if (problem != null) return
        when (step) {
            UploaderPreset.DISCORD -> onAdd(UploaderPresets.discord(first), false)
            UploaderPreset.IMMICH -> onAdd(UploaderPresets.immich(first, second, ownerLink), false)
            UploaderPreset.CUSTOM -> onAdd(UploaderProfile(name = first.ifBlank { "Custom uploader" }, url = second.trim()), true)
            else -> {}
        }
    }

    integration?.let { chosen ->
        chosen.content(null, { onAdd(it, false) }, { integration = null })
        return
    }

    ModalDialog(
        onDismiss,
        width = 460.dp,
        title = { DialogTitle(step?.label ?: "Add an uploader") },
        buttons = {
            if (step == null) {
                TextButton("Cancel", onDismiss)
            } else {
                TextButton("Back", { step = null })
                TextButton(if (step == UploaderPreset.CUSTOM) "Add and edit" else "Add", "plus", { confirm() }, style = ControlStyle.Primary, enabled = problem == null)
            }
        },
    ) {
        when (step) {
            null -> {
                for (preset in UploaderPreset.entries) {
                    if (preset == UploaderPreset.SXCU) {
                        for (entry in IntegrationDialogs.all) IntegrationCard(entry) { integration = entry }
                    }
                    PresetCard(preset, isDefault = presetId(preset) == defaultId) { pick(preset) }
                }
            }

            UploaderPreset.DISCORD -> {
                Hint("Copy the link from the channel's Integrations > Webhooks settings. Anyone with it can post there, so keep it private.")
                SecretTextField(first, { first = it }, Modifier.fillMaxWidth(), placeholder = "https://discord.com/api/webhooks/...", label = "Webhook link", secretRanges = urlSecretRanges)
                Hint("Once it's added, use Edit to set the message text and embed.")
            }

            UploaderPreset.IMMICH -> {
                Hint("In Immich, open Account Settings > API Keys and make a key with asset.upload and asset.delete.")
                TextField(first, { first = it }, Modifier.fillMaxWidth(), placeholder = "https://photos.example.com", label = "Server address")
                SecretTextField(second, { second = it }, Modifier.fillMaxWidth(), placeholder = "API key", label = "API key")
                ToggleRow("Copy a link to the photo", ownerLink, { ownerLink = it }, Modifier.fillMaxWidth(), description = "Only you can open it, while signed in to Immich.")
            }

            UploaderPreset.CUSTOM -> {
                LabeledField("Name", Modifier.fillMaxWidth()) {
                    TextField(first, { first = it }, Modifier.fillMaxWidth(), placeholder = "Custom uploader", label = "Name")
                }
                LabeledField("Upload URL", Modifier.fillMaxWidth()) {
                    SecretTextField(second, { second = it }, Modifier.fillMaxWidth(), placeholder = "https://example.com/upload", label = "Upload URL", secretRanges = urlSecretRanges)
                }
                Hint("Headers, form fields, the link and deletion are under Advanced in the next step.")
            }

            else -> {}
        }
        if (!problem.isNullOrEmpty()) Text(problem, Modifier, theme.error, OneConfigTheme.typography.small)
    }
}

private fun presetId(preset: UploaderPreset): String? = when (preset) {
    UploaderPreset.IMGUR -> UploaderProfile.IMGUR_ID
    UploaderPreset.CATBOX -> UploaderProfile.CATBOX_ID
    else -> null
}

@Composable
private fun IntegrationCard(entry: IntegrationDialog, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    Card(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        label = entry.label,
        contentPadding = PaddingValues(horizontal = spacing.l, vertical = spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            Icon(entry.icon, null, Modifier.size(18.dp), theme.textColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(entry.label, Modifier, theme.textColor, OneConfigTheme.typography.label)
                Text(entry.detail, Modifier, theme.textColorSecondary, OneConfigTheme.typography.small)
            }
        }
    }
}

@Composable
private fun PresetCard(preset: UploaderPreset, isDefault: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val builtIn = presetId(preset) != null
    Card(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        label = preset.label,
        enabled = !isDefault,
        contentPadding = PaddingValues(horizontal = spacing.l, vertical = spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            Icon(preset.icon, null, Modifier.size(18.dp), theme.textColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(preset.label, Modifier, theme.textColor, OneConfigTheme.typography.label)
                Text(preset.detail, Modifier, theme.textColorSecondary, OneConfigTheme.typography.small)
            }
            when {
                isDefault -> Badge("Default", tone = BadgeTone.Accent)
                builtIn -> Text("Make default", Modifier, theme.textColorSecondary, OneConfigTheme.typography.caption)
            }
        }
    }
}
