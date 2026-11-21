@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui.integration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.deftu.screencapper.gui.CollapsibleSection
import dev.deftu.screencapper.gui.Hint
import dev.deftu.screencapper.gui.IntegrationDialog
import dev.deftu.screencapper.gui.LockedSecretsWarning
import dev.deftu.screencapper.gui.SecretTextField
import dev.deftu.screencapper.gui.SecretsNote
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import dev.deftu.screencapper.upload.integration.Integrations
import dev.deftu.screencapper.upload.integration.googlephotos.GooglePhotosForm
import dev.deftu.screencapper.upload.integration.googlephotos.GooglePhotosSettings
import dev.deftu.screencapper.upload.integration.googlephotos.GoogleSignIn
import dev.deftu.screencapper.upload.integration.googlephotos.SignedIn
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.LabeledField
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.Multithreading
import java.net.URI
import java.util.concurrent.CancellationException

private const val NAME = "Google Photos"

internal val GooglePhotosDialogEntry = IntegrationDialog(GooglePhotosSettings.KIND, NAME, "cloud", "Your Google Photos library.") { existing, onSave, onDismiss ->
    GooglePhotosDialog(existing, onSave, onDismiss)
}

@Composable
private fun GooglePhotosDialog(existing: UploaderProfile?, onSave: (UploaderProfile) -> Unit, onDismiss: () -> Unit) {
    val theme = OneConfigTheme.current
    val key = existing?.id
    val initial = remember(key) {
        val saved = existing?.let { Integrations.current(it.id) ?: it.integration }?.takeIf { it.kind == GooglePhotosSettings.KIND }
        GooglePhotosSettings(saved ?: IntegrationSettings(GooglePhotosSettings.KIND))
    }
    var name by remember(key) { mutableStateOf(existing?.name ?: NAME) }
    var clientId by remember(key) { mutableStateOf(initial.clientId) }
    var clientSecret by remember(key) { mutableStateOf(initial.clientSecret) }
    var useAlbum by remember(key) { mutableStateOf(initial.useAlbum) }
    var copyLink by remember(key) { mutableStateOf(initial.copyLink) }
    var signedIn by remember(key) { mutableStateOf<SignedIn?>(null) }
    var pending by remember(key) { mutableStateOf<GoogleSignIn?>(null) }
    var starting by remember(key) { mutableStateOf(false) }
    var problem by remember(key) { mutableStateOf<String?>(null) }
    var howTo by remember(key) { mutableStateOf(!initial.isSignedIn) }

    DisposableEffect(key) { onDispose { pending?.cancel() } }

    val trimmedId = clientId.trim()
    val isSignedIn = signedIn?.clientId == trimmedId || (initial.isSignedIn && initial.clientId == trimmedId)
    val busy = starting || pending != null

    fun signIn() {
        problem = null
        starting = true
        val id = trimmedId
        val secret = clientSecret.trim()
        Multithreading.submit {
            val attempt = runCatching { GoogleSignIn(id, secret) }.getOrElse {
                Snapshot.withMutableSnapshot {
                    starting = false
                    problem = "Couldn't start sign-in."
                }
                return@submit
            }
            Snapshot.withMutableSnapshot {
                starting = false
                pending = attempt
            }
            val result = runCatching { attempt.run() }
            Snapshot.withMutableSnapshot {
                if (pending === attempt) {
                    pending = null
                    result.onSuccess {
                        signedIn = it
                        howTo = false
                    }.onFailure {
                        if (it !is CancellationException) problem = (it as? UploadException)?.message ?: "Sign-in failed."
                    }
                }
            }
        }
    }

    fun save() {
        val latest = existing?.let { Integrations.current(it.id) ?: it.integration }
        val settings = GooglePhotosSettings.saved(latest, GooglePhotosForm(clientId, clientSecret, useAlbum, copyLink), signedIn)
        onSave(existing?.copy(name = name.ifBlank { NAME }, integration = settings) ?: UploaderProfile(name = NAME, sizeLimitMb = "200", integration = settings))
    }

    ModalDialog(
        onDismiss,
        width = 520.dp,
        dismissOnScrim = false,
        title = { DialogTitle(existing?.name?.ifBlank { null } ?: NAME) },
        buttons = {
            TextButton("Cancel", onDismiss)
            TextButton(
                if (existing == null) "Add" else "Save",
                if (existing == null) "plus" else "tick",
                { save() },
                style = ControlStyle.Primary,
                enabled = trimmedId.isNotEmpty() && !busy && (existing != null || isSignedIn),
            )
        },
    ) {
        if (existing != null) {
            LockedSecretsWarning(existing)
            LabeledField("Name", Modifier.fillMaxWidth()) {
                TextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = NAME, label = "Name")
            }
        }

        CollapsibleSection("How to set it up", howTo, { howTo = !howTo }) {
            Step("1. Open the Google Cloud console and make a new project.")
            TextButton("Open Google Cloud console", "external-link", { DesktopHelper.browse(URI("https://console.cloud.google.com/")) })
            Step("2. Go to APIs & Services > Library, find Photos Library API and turn it on.")
            Step("3. Open Google Auth platform and click Get started. Pick Personal if it's offered. If not, pick External, then add your own Google account under Audience > Test users.")
            Step("4. Under Clients, create a client of type Desktop app. Copy its client ID and secret into the fields below. No redirect URL is needed.")
            Hint("External apps in Testing sign you out after 7 days. To stop that, click Publish app under Audience. Google then warns that the app isn't verified when you sign in. It's your own app, so you can continue.")
        }

        LabeledField("Client ID", Modifier.fillMaxWidth()) {
            TextField(clientId, { clientId = it }, Modifier.fillMaxWidth(), placeholder = "123-abc.apps.googleusercontent.com", label = "Client ID")
        }
        LabeledField("Client secret", Modifier.fillMaxWidth()) {
            SecretTextField(clientSecret, { clientSecret = it }, Modifier.fillMaxWidth(), placeholder = "Client secret", label = "Client secret")
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.l)) {
            val status = when {
                busy -> "Finish signing in in your browser."
                isSignedIn -> "Signed in to Google Photos."
                else -> "Not signed in."
            }
            Text(status, Modifier.weight(1f), if (isSignedIn && !busy) theme.textColor else theme.textColorSecondary, OneConfigTheme.typography.small)
            if (busy) {
                TextButton("Cancel", {
                    pending?.cancel()
                    pending = null
                }, enabled = pending != null)
            } else {
                TextButton(
                    if (isSignedIn) "Sign in again" else "Sign in",
                    "external-link",
                    { signIn() },
                    style = if (isSignedIn) ControlStyle.Secondary else ControlStyle.Primary,
                    enabled = trimmedId.isNotEmpty(),
                )
            }
        }
        problem?.let { Text(it, Modifier, theme.error, OneConfigTheme.typography.small) }

        ToggleRow("Add to a Screencapper album", useAlbum, { useAlbum = it }, Modifier.fillMaxWidth(), description = "Makes the album on your first upload.")
        ToggleRow("Copy a link to the photo", copyLink, { copyLink = it }, Modifier.fillMaxWidth(), description = "Only you can open it, while signed in to Google.")
        Hint("Apps can't delete from Google Photos. Remove uploads in Google Photos itself.", "info")
        SecretsNote()
    }
}

@Composable
private fun Step(text: String) {
    val theme = OneConfigTheme.current
    Text(text, Modifier.fillMaxWidth(), theme.textColor, OneConfigTheme.typography.small)
}
