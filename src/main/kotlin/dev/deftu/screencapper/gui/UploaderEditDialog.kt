@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.config.LinkMode
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.config.RequestMethod
import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.DiscordMessage
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.Uploaders
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.LabeledField
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

@Composable
internal fun UploaderEditDialog(profile: UploaderProfile, onSave: (UploaderProfile) -> Unit, onDismiss: () -> Unit) {
    if (profile.isBuiltIn) {
        BuiltInDialog(profile, onDismiss)
        return
    }
    IntegrationDialogs.of(profile)?.let { entry ->
        entry.content(profile, onSave, onDismiss)
        return
    }
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val discord = remember(profile.id) { UploaderDisplay.isDiscord(profile) }
    val immich = remember(profile.id) { ImmichKey.isImmich(profile) }
    val initialOptions = remember(profile.id) { if (discord) UploaderEdits.discordOptions(profile) else null }
    var draft by remember(profile.id) { mutableStateOf(profile) }
    var options by remember(profile.id) { mutableStateOf(initialOptions) }
    var advanced by remember(profile.id) { mutableStateOf(false) }
    val problem = options?.let { DiscordMessage.problem(it) }

    ModalDialog(
        onDismiss,
        width = if (discord) 600.dp else 540.dp,
        dismissOnScrim = false,
        title = { DialogTitle(profile.name.ifBlank { "Edit uploader" }) },
        buttons = {
            TextButton("Cancel", onDismiss)
            TextButton("Save", "tick", { if (problem == null) onSave(UploaderEdits.saved(draft, initialOptions, options)) }, style = ControlStyle.Primary, enabled = problem == null)
        },
    ) {
        LockedSecretsWarning(draft)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            LabeledField("Name", Modifier.width(180.dp)) {
                TextField(draft.name, { draft = draft.copy(name = it) }, placeholder = "My host", label = "Name")
            }
            val urlLabel = if (discord) "Webhook link" else "Upload URL"
            LabeledField(urlLabel, Modifier.weight(1f)) {
                SecretTextField(draft.url, { draft = draft.copy(url = it) }, placeholder = if (discord) "https://discord.com/api/webhooks/..." else "https://example.com/upload", label = urlLabel, secretRanges = urlSecretRanges)
            }
        }
        if (immich) {
            LabeledField("API key", Modifier.fillMaxWidth()) {
                SecretTextField(ImmichKey.read(draft), { draft = ImmichKey.write(draft, it) }, Modifier.fillMaxWidth(), placeholder = "API key", label = "API key")
            }
        }
        if (!discord) {
            LabeledField("Result") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Chip("Copy a link", draft.linkMode == LinkMode.LINK, { draft = draft.copy(linkMode = LinkMode.LINK) })
                    Chip("No link", draft.linkMode == LinkMode.NONE, { draft = draft.copy(linkMode = LinkMode.NONE) })
                }
            }
        }

        options?.let { current ->
            DiscordMessageEditor(current, { options = it }, draft.keepMetadata, { draft = draft.copy(keepMetadata = it) })
        }

        CollapsibleSection("Advanced", advanced, { advanced = !advanced }) {
            AdvancedFields(draft, showKeepMetadata = !discord) { draft = it }
        }

        if (problem != null) Text(problem, Modifier, theme.error, OneConfigTheme.typography.small)
        SecretsNote()
    }
}

@Composable
private fun AdvancedFields(profile: UploaderProfile, showKeepMetadata: Boolean, onChange: (UploaderProfile) -> Unit) {
    val spacing = OneConfigTheme.current.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxl)) {
        LabeledField("Method") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                for (method in RequestMethod.entries) Chip(method.name, profile.method == method, { onChange(profile.copy(method = method)) })
            }
        }
        LabeledField("Body") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                for (body in RequestBody.entries) Chip(body.displayName, profile.body == body, { onChange(profile.copy(body = body)) })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
        if (profile.body == RequestBody.MULTIPART) {
            LabeledField("File field", Modifier.width(140.dp)) {
                TextField(profile.fileField, { onChange(profile.copy(fileField = it)) }, placeholder = "file", label = "File field")
            }
        }
        if (profile.linkMode == LinkMode.LINK) {
            LabeledField("Link in response", Modifier.weight(1f)) {
                TextField(profile.responseUrl, { onChange(profile.copy(responseUrl = it)) }, placeholder = "data.link, or empty if the response is the link", label = "Link in response")
            }
        }
    }
    LabeledField("Headers") {
        SecretKeyValueList(profile.headers, "Header", "Value", "Add header") { onChange(profile.copy(headers = it)) }
    }
    if (profile.body == RequestBody.MULTIPART) {
        LabeledField("Form fields") {
            SecretKeyValueList(profile.formFields, "Field", "Value", "Add field") { onChange(profile.copy(formFields = it)) }
        }
    }
    DeletionFields(profile.deletion) { onChange(profile.copy(deletion = it)) }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
        LabeledField("Size limit (MB)", Modifier.width(140.dp)) {
            TextField(profile.sizeLimitMb, { value -> onChange(profile.copy(sizeLimitMb = value.filter { it.isDigit() || it == '.' || it == ',' })) }, placeholder = "No limit", label = "Size limit (MB)")
        }
        if (showKeepMetadata) {
            ToggleRow("Keep metadata", profile.keepMetadata, { onChange(profile.copy(keepMetadata = it)) }, Modifier.weight(1f))
        }
    }
    Hint(
        "Text can use {uuid}, {fileName}, {name}, {createdAt}, {modifiedAt}, {timestamp}, {date} and {time}, plus {player}, {world}, {dimension}, {biome}, {coords} and {version} with Keep metadata. " +
            "The link and deletion can use {json:path} from the response.",
    )
}

@Composable
private fun DeletionFields(deletion: DeletionRequest, onChange: (DeletionRequest) -> Unit) {
    val spacing = OneConfigTheme.current.spacing
    LabeledField("Delete uploads") {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            for (kind in DeletionKind.entries) Chip(kind.displayName, deletion.kind == kind, { onChange(deletion.copy(kind = kind)) })
        }
    }
    if (deletion.kind == DeletionKind.NONE) return
    val request = deletion.kind == DeletionKind.REQUEST
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
        if (request) {
            LabeledField("Method") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    for (method in DeletionMethod.entries) Chip(method.name, deletion.method == method, { onChange(deletion.copy(method = method)) })
                }
            }
        }
        val label = if (request) "Deletion URL" else "Deletion page"
        LabeledField(label, Modifier.weight(1f)) {
            SecretTextField(deletion.url, { onChange(deletion.copy(url = it)) }, placeholder = "https://example.com/delete/{json:id}", label = label, secretRanges = urlSecretRanges)
        }
    }
    if (request) {
        LabeledField("Deletion headers") {
            SecretKeyValueList(deletion.headers, "Header", "Value", "Add header") { onChange(deletion.copy(headers = it)) }
        }
        LabeledField("Deletion body") {
            TextField(deletion.body, { onChange(deletion.copy(body = it)) }, Modifier.fillMaxWidth(), placeholder = "Empty, or JSON like {\"ids\":[\"{json:id}\"]}", label = "Deletion body")
        }
    }
}

@Composable
private fun BuiltInDialog(profile: UploaderProfile, onDismiss: () -> Unit) {
    var keepMetadata by remember(profile.id) { mutableStateOf(Uploaders.keepsMetadata(profile)) }
    ModalDialog(
        onDismiss,
        title = { DialogTitle(profile.name) },
        buttons = {
            TextButton("Cancel", onDismiss)
            TextButton("Save", "tick", {
                Uploaders.setKeepsMetadata(profile, keepMetadata)
                onDismiss()
            }, style = ControlStyle.Primary)
        },
    ) {
        Hint("Built in. Nothing to set up.")
        ToggleRow("Keep metadata", keepMetadata, { keepMetadata = it }, Modifier.fillMaxWidth(), description = "Leaves player, world and place details in the uploaded file.")
        profile.sizeLimitBytes?.let { Hint("Uploads over ${it / 1_000_000} MB are shrunk to fit.") }
    }
}
