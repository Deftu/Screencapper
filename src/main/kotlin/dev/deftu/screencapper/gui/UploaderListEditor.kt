@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.config.RequestMethod
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.upload.SxcuImporter
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.Uploaders
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.api.TinyFdApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
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
import org.polyfrost.oneconfig.utils.v1.Multithreading
import kotlinx.coroutines.delay

@Composable
fun UploaderListEditor(prop: Property<*>) {
    var profiles by remember(prop) { mutableStateOf(ScreencapperConfig.profiles) }
    var defaultId by remember(prop) { mutableStateOf(ScreencapperConfig.defaultProfile.id) }
    var expanded by remember(prop) { mutableStateOf<String?>(null) }
    LaunchedEffect(prop) {
        while (true) {
            delay(1000)
            val stored = ScreencapperConfig.profiles
            if (stored != profiles) profiles = stored
            val storedDefault = ScreencapperConfig.defaultProfile.id
            if (storedDefault != defaultId) defaultId = storedDefault
        }
    }

    fun commit(next: List<UploaderProfile>) {
        profiles = next
        @Suppress("UNCHECKED_CAST")
        (prop as Property<String>).set(UploaderProfile.encodeList(next))
    }

    fun makeDefault(id: String) {
        defaultId = id
        ScreencapperConfig.defaultUploader = id
    }

    fun add(profile: UploaderProfile) {
        commit(profiles + profile)
        expanded = profile.id
    }

    val spacing = OneConfigTheme.current.spacing
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        for (profile in profiles) {
            UploaderCard(
                profile = profile,
                isDefault = profile.id == defaultId,
                isExpanded = expanded == profile.id,
                onToggle = { expanded = if (expanded == profile.id) null else profile.id },
                onDefault = { makeDefault(profile.id) },
                onDelete = {
                    commit(profiles.filterNot { it.id == profile.id })
                    if (defaultId == profile.id) makeDefault(UploaderProfile.IMGUR_ID)
                },
                onChange = { updated -> commit(profiles.map { if (it.id == updated.id) updated else it }) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            TextButton("Add uploader", "plus", { add(UploaderProfile(name = "Uploader ${profiles.size}")) }, style = ControlStyle.Primary)
            TextButton("Import .sxcu file", "folder", {
                Multithreading.submit {
                    val path = runCatching {
                        TinyFdApi.getInstance().openFileSelector("Import a ShareX uploader", null, arrayOf("*.sxcu"), "ShareX custom uploader (*.sxcu)")
                    }.getOrNull() ?: return@submit
                    runCatching { SxcuImporter.read(path.toFile()) }
                        .onSuccess { profile ->
                            Snapshot.withMutableSnapshot { add(profile) }
                            ScreenshotActions.notifySuccess("Uploader imported", "Added ${profile.name}.")
                        }
                        .onFailure {
                            Screencapper.LOGGER.error("Failed to import {}", path, it)
                            ScreenshotActions.notifyError("Import failed", it.message ?: "That file couldn't be read.")
                        }
                }
            })
        }
    }
}

@Composable
private fun UploaderCard(
    profile: UploaderProfile,
    isDefault: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDefault: () -> Unit,
    onDelete: () -> Unit,
    onChange: (UploaderProfile) -> Unit,
) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .themedSurface(SurfaceKind.Field, selected = isDefault)
            .animateContentSize()
            .contextMenu {
                listOfNotNull(
                    MenuAction("Make default", onDefault, icon = "star", enabled = !isDefault),
                    MenuAction(if (isExpanded) "Collapse" else "Edit", onToggle, icon = if (isExpanded) "up" else "text-input"),
                    if (profile.isBuiltIn) null else MenuSeparator,
                    if (profile.isBuiltIn) null else MenuAction("Remove", onDelete, icon = "trash", danger = true),
                )
            }
            .padding(horizontal = spacing.l, vertical = spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            IconButton(if (isDefault) "star-filled" else "star", if (isDefault) "Default uploader" else "Make default", onDefault, size = ControlSize.Compact, selected = isDefault)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(profile.name.ifBlank { "Unnamed uploader" }, Modifier, theme.textColor, OneConfigTheme.typography.label)
                Text(if (profile.isBuiltIn) "Built in · ${profile.host}" else "${profile.method.name} · ${profile.host}", Modifier, theme.textColorSecondary, OneConfigTheme.typography.small)
            }
            if (isDefault) Badge("DEFAULT", tone = BadgeTone.Accent)
            IconButton(if (isExpanded) "up" else "down", if (isExpanded) "Collapse" else "Edit", onToggle, size = ControlSize.Compact, selected = isExpanded)
            if (!profile.isBuiltIn) IconButton("trash", "Remove uploader", onDelete, size = ControlSize.Compact, danger = true)
        }

        if (isExpanded) {
            Spacer(Modifier.padding(top = spacing.l))
            if (profile.isBuiltIn) BuiltInForm(profile) else ProfileForm(profile, onChange)
        }
    }
}

@Composable
private fun BuiltInForm(profile: UploaderProfile) {
    var keepMetadata by remember(profile.id) { mutableStateOf(Uploaders.keepsMetadata(profile)) }
    val spacing = OneConfigTheme.current.spacing
    Column(Modifier.fillMaxWidth().padding(start = 36.dp, bottom = spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        ToggleRow("Keep metadata", keepMetadata, {
            keepMetadata = !keepMetadata
            Uploaders.setKeepsMetadata(profile, keepMetadata)
        }, Modifier.width(220.dp))
        val limit = profile.sizeLimitBytes
        if (limit != null) Text("Uploads over ${limit / 1_000_000} MB are shrunk to fit.", Modifier, OneConfigTheme.current.textColorSecondary, OneConfigTheme.typography.small)
    }
}

@Composable
private fun ProfileForm(profile: UploaderProfile, onChange: (UploaderProfile) -> Unit) {
    val spacing = OneConfigTheme.current.spacing
    Column(Modifier.fillMaxWidth().padding(start = 36.dp, bottom = spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            LabeledField("Name", Modifier.width(220.dp)) {
                TextField(profile.name, { onChange(profile.copy(name = it)) }, placeholder = "My host", label = "Name")
            }
            LabeledField("Upload URL", Modifier.weight(1f)) {
                TextField(profile.url, { onChange(profile.copy(url = it)) }, placeholder = "https://example.com/upload", label = "Upload URL")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxl)) {
            LabeledField("Request method") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    for (method in RequestMethod.entries) Chip(method.name, profile.method == method, { onChange(profile.copy(method = method)) })
                }
            }
            LabeledField("Request body") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    for (body in RequestBody.entries) Chip(body.displayName, profile.body == body, { onChange(profile.copy(body = body)) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            if (profile.body == RequestBody.MULTIPART) {
                LabeledField("File field", Modifier.width(220.dp)) {
                    TextField(profile.fileField, { onChange(profile.copy(fileField = it)) }, placeholder = "file", label = "File field")
                }
            }
            LabeledField("Link in response", Modifier.weight(1f)) {
                TextField(profile.responseUrl, { onChange(profile.copy(responseUrl = it)) }, placeholder = "data.link, files[0].url, or empty if the response is the link", label = "Link in response")
            }
        }
        LabeledField("Headers") {
            KeyValueList(profile.headers, "Header", "Value", "Add header") { onChange(profile.copy(headers = it)) }
        }
        if (profile.body == RequestBody.MULTIPART) {
            LabeledField("Form fields") {
                KeyValueList(profile.formFields, "Field", "Value", "Add field") { onChange(profile.copy(formFields = it)) }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            LabeledField("Size limit (MB)", Modifier.width(220.dp)) {
                TextField(profile.sizeLimitMb, { value -> onChange(profile.copy(sizeLimitMb = value.filter { it.isDigit() || it == '.' || it == ',' })) }, placeholder = "No limit", label = "Size limit (MB)")
            }
            ToggleRow("Keep metadata", profile.keepMetadata, { onChange(profile.copy(keepMetadata = !profile.keepMetadata)) }, Modifier.width(220.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Icon("alert-circle", null, Modifier.size(14.dp), OneConfigTheme.current.warning)
            Text("Header and field values are stored in plain text in your config.", Modifier, OneConfigTheme.current.textColorSecondary, OneConfigTheme.typography.caption)
        }
    }
}
