package dev.deftu.screencapper.gui

import androidx.compose.runtime.Composable
import dev.deftu.screencapper.gui.integration.FlickrDialogEntry
import dev.deftu.screencapper.gui.integration.GooglePhotosDialogEntry
import dev.deftu.screencapper.gui.integration.NextcloudDialogEntry
import dev.deftu.screencapper.upload.UploaderProfile

internal class IntegrationDialog(
    val kind: String,
    val label: String,
    val icon: String,
    val detail: String,
    val content: @Composable (existing: UploaderProfile?, onSave: (UploaderProfile) -> Unit, onDismiss: () -> Unit) -> Unit,
)

internal object IntegrationDialogs {
    val all: List<IntegrationDialog> = listOf(NextcloudDialogEntry, GooglePhotosDialogEntry, FlickrDialogEntry)

    fun find(kind: String): IntegrationDialog? = all.firstOrNull { it.kind == kind }

    fun of(profile: UploaderProfile): IntegrationDialog? = profile.integration?.let { find(it.kind) }
}
