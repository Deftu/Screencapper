@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.config.RequestMethod
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.upload.SxcuImporter
import dev.deftu.screencapper.upload.UploaderProfile
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.api.TinyFdApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.Multithreading
import kotlinx.coroutines.delay

@Composable
fun UploaderListEditor(prop: Property<*>) {
    var profiles by remember(prop) { mutableStateOf(ScreencapperConfig.profiles) }
    var defaultId by remember(prop) { mutableStateOf(ScreencapperConfig.defaultProfile.id) }
    var expanded by remember(prop) { mutableStateOf<String?>(null) }
    // Picks up changes made outside this editor, such as a config reset or profile switch, before they get overwritten.
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

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Add uploader", "plus", hierarchy = ButtonHierarchy.PRIMARY) { add(UploaderProfile(name = "Uploader ${profiles.size}")) }
            SmallButton("Import .sxcu file", "folder") {
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
            }
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(theme.componentBackground, theme.buttonShape)
            .border(1.dp, if (isDefault) OneConfigTheme.accent.copy(alpha = 0.6f) else theme.borderColor, theme.buttonShape)
            .animateContentSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconAction(if (isDefault) "star-filled" else "star", if (isDefault) "Default uploader" else "Make default", active = isDefault, onClick = onDefault)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name.ifBlank { "Unnamed uploader" }, Modifier, theme.textColor, 14.sp, FontWeight.Medium)
                Text(if (profile.isBuiltIn) "Built in · imgur.com" else "${profile.method.name} · ${profile.host}", Modifier, theme.textColorSecondary, 12.sp)
            }
            if (isDefault) StatusTag("DEFAULT")
            if (!profile.isBuiltIn) {
                IconAction(if (isExpanded) "up" else "down", if (isExpanded) "Collapse" else "Edit", onClick = onToggle)
                IconAction("trash", "Remove uploader", destructive = true, onClick = onDelete)
            }
        }

        if (isExpanded && !profile.isBuiltIn) {
            Spacer(Modifier.padding(top = 12.dp))
            ProfileForm(profile, onChange)
        }
    }
}

@Composable
private fun ProfileForm(profile: UploaderProfile, onChange: (UploaderProfile) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 36.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(220.dp)) {
                FieldLabel("Name")
                TextField(profile.name, "My host") { onChange(profile.copy(name = it)) }
            }
            Column(Modifier.weight(1f)) {
                FieldLabel("Upload URL")
                TextField(profile.url, "https://example.com/upload") { onChange(profile.copy(url = it)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                FieldLabel("Request method")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (method in RequestMethod.entries) Chip(method.name, profile.method == method) { onChange(profile.copy(method = method)) }
                }
            }
            Column {
                FieldLabel("Request body")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (body in RequestBody.entries) Chip(body.displayName, profile.body == body) { onChange(profile.copy(body = body)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (profile.body == RequestBody.MULTIPART) {
                Column(Modifier.width(220.dp)) {
                    FieldLabel("File field")
                    TextField(profile.fileField, "file") { onChange(profile.copy(fileField = it)) }
                }
            }
            Column(Modifier.weight(1f)) {
                FieldLabel("Link in response")
                TextField(profile.responseUrl, "data.link, files[0].url, or empty if the response is the link") { onChange(profile.copy(responseUrl = it)) }
            }
        }
        Column {
            FieldLabel("Headers")
            KeyValueList(profile.headers, "Header", "Value", "Add header") { onChange(profile.copy(headers = it)) }
        }
        if (profile.body == RequestBody.MULTIPART) {
            Column {
                FieldLabel("Form fields")
                KeyValueList(profile.formFields, "Field", "Value", "Add field") { onChange(profile.copy(formFields = it)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon("info", OneConfigTheme.current.textColorSecondary, Modifier.size(14.dp))
            Text("Header and field values are stored in plain text in your config.", Modifier, OneConfigTheme.current.textColorSecondary, 11.sp)
        }
    }
}
