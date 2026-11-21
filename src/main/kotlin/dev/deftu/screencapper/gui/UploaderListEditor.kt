@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.upload.SxcuImporter
import dev.deftu.screencapper.upload.UploaderPresets
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.UploaderSecrets
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.api.TinyFdApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSeparator
import org.polyfrost.oneconfig.api.ui.v1.components.MoreButton
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.Multithreading

@Composable
fun UploaderListEditor(prop: Property<*>) {
    var profiles by remember(prop) { mutableStateOf(ScreencapperConfig.profiles) }
    var defaultId by remember(prop) { mutableStateOf(ScreencapperConfig.defaultProfile.id) }
    var adding by remember(prop) { mutableStateOf(false) }
    var editing by remember(prop) { mutableStateOf<String?>(null) }
    var deleting by remember(prop) { mutableStateOf<UploaderProfile?>(null) }
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
        (prop as Property<String>).set(ScreencapperConfig.encodeProfiles(next))
    }

    fun makeDefault(id: String) {
        defaultId = id
        ScreencapperConfig.defaultUploader = id
    }

    fun add(profile: UploaderProfile) {
        commit(profiles + profile)
    }

    fun delete(profile: UploaderProfile) {
        commit(profiles.filterNot { it.id == profile.id })
        if (defaultId == profile.id) makeDefault(UploaderProfile.IMGUR_ID)
    }

    val spacing = OneConfigTheme.current.spacing
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        for (profile in profiles) {
            key(profile.id) {
                UploaderRow(
                    profile = profile,
                    isDefault = profile.id == defaultId,
                    onDefault = { makeDefault(profile.id) },
                    onEdit = { editing = profile.id },
                    onDuplicate = { add(UploaderEdits.duplicate(profile)) },
                    onDelete = { deleting = profile },
                )
            }
        }
        Row(Modifier.padding(top = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            TextButton("Add uploader", "plus", { adding = true }, style = ControlStyle.Primary)
        }
    }

    if (adding) {
        AddUploaderDialog(
            defaultId = defaultId,
            onUseBuiltIn = { id ->
                adding = false
                makeDefault(id)
            },
            onImport = {
                adding = false
                importSxcu(::add)
            },
            onAdd = { profile, edit ->
                adding = false
                add(profile)
                if (edit) editing = profile.id
            },
            onDismiss = { adding = false },
        )
    }

    editing?.let { id -> profiles.firstOrNull { it.id == id } }?.let { profile ->
        UploaderEditDialog(
            profile,
            onSave = { updated ->
                editing = null
                commit(profiles.map { if (it.id == updated.id) UploaderPresets.refreshed(updated) else it })
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { profile ->
        ConfirmDialog(
            "Delete ${profile.name.ifBlank { "this uploader" }}?",
            "Its settings and saved keys are removed from Screencapper.",
            "Delete",
            {
                deleting = null
                delete(profile)
            },
            { deleting = null },
            danger = true,
            icon = "trash",
        )
    }
}

private fun importSxcu(add: (UploaderProfile) -> Unit) {
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

@Composable
private fun UploaderRow(
    profile: UploaderProfile,
    isDefault: Boolean,
    onDefault: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val menu: List<MenuEntry> = listOfNotNull(
        MenuAction("Make default", onDefault, icon = "star", enabled = !isDefault),
        MenuAction("Edit", onEdit, icon = "text-input"),
        if (profile.isBuiltIn) null else MenuAction("Duplicate", onDuplicate, icon = "copy"),
        if (profile.isBuiltIn) null else MenuSeparator,
        if (profile.isBuiltIn) null else MenuAction("Delete", onDelete, icon = "trash", danger = true),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .themedSurface(SurfaceKind.Field, selected = isDefault)
            .contextMenu { menu }
            .padding(spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        IconButton(
            if (isDefault) "star-filled" else "star",
            if (isDefault) "Default uploader" else "Make default",
            onDefault,
            size = ControlSize.Compact,
            selected = isDefault,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(profile.name.ifBlank { "Unnamed uploader" }, Modifier, theme.textColor, OneConfigTheme.typography.label)
            Text("${UploaderDisplay.kind(profile)} · ${UploaderDisplay.host(profile)}", Modifier, theme.textColorSecondary, OneConfigTheme.typography.small)
        }
        if (UploaderSecrets.lockedFields(profile).isNotEmpty()) Badge("Re-enter secrets", tone = BadgeTone.Warning, icon = "alert-circle")
        TextButton("Edit", onEdit, size = ControlSize.Compact)
        MoreButton(menu, contentDescription = "More for ${profile.name}")
    }
}
