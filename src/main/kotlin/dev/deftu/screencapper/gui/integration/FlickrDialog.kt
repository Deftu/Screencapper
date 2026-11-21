@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui.integration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.deftu.screencapper.upload.Redaction
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import dev.deftu.screencapper.upload.integration.Integrations
import dev.deftu.screencapper.upload.integration.LoopbackReceiver
import dev.deftu.screencapper.upload.integration.flickr.FlickrAccount
import dev.deftu.screencapper.upload.integration.flickr.FlickrSettings
import dev.deftu.screencapper.upload.integration.flickr.FlickrSignIn
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.LabeledField
import org.polyfrost.oneconfig.api.ui.v1.components.LinkText
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.Multithreading
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

private const val NAME = "Flickr"
private const val CREATE_APP_URL = "https://www.flickr.com/services/apps/create/"

internal val FlickrDialogEntry = IntegrationDialog(FlickrSettings.KIND, NAME, "dots-grid", "Your Flickr photostream.") { existing, onSave, onDismiss ->
    FlickrDialog(existing, onSave, onDismiss)
}

private sealed interface SignInState {
    data object Idle : SignInState
    data object Waiting : SignInState
    data class Failed(val message: String) : SignInState
}

@Composable
private fun FlickrDialog(existing: UploaderProfile?, onSave: (UploaderProfile) -> Unit, onDismiss: () -> Unit) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val key = existing?.id
    var settings by remember(key) {
        val saved = existing?.let { Integrations.current(it.id) ?: it.integration }?.takeIf { it.kind == FlickrSettings.KIND }
        mutableStateOf(saved ?: FlickrSettings.initial())
    }
    var name by remember(key) { mutableStateOf(existing?.name ?: NAME) }
    var keepMetadata by remember(key) { mutableStateOf(existing?.keepMetadata ?: false) }
    var advanced by remember(key) { mutableStateOf(false) }
    var state by remember(key) { mutableStateOf<SignInState>(SignInState.Idle) }
    var pending by remember(key) { mutableStateOf<LoopbackReceiver?>(null) }

    DisposableEffect(key) { onDispose { pending?.cancel() } }

    val signedIn = FlickrSettings.signedIn(settings)
    val hasKeys = settings[FlickrSettings.API_KEY].isNotBlank() && settings[FlickrSettings.API_SECRET].isNotBlank()

    fun finish(account: FlickrAccount, apiKey: String, apiSecret: String) {
        val signIn: (IntegrationSettings) -> IntegrationSettings = { FlickrSettings.withAccount(FlickrSettings.withKeys(it, apiKey, apiSecret), account) }
        Snapshot.withMutableSnapshot {
            settings = signIn(settings)
            state = SignInState.Idle
        }
        existing?.let { profile -> Integrations.update(profile.id, signIn) }
    }

    fun signIn() {
        val apiKey = settings[FlickrSettings.API_KEY].trim()
        val apiSecret = settings[FlickrSettings.API_SECRET].trim()
        val receiver = runCatching { LoopbackReceiver.open() }.getOrElse {
            state = SignInState.Failed("Couldn't start the sign-in. Try again.")
            return
        }
        pending = receiver
        state = SignInState.Waiting
        Multithreading.submit {
            val result = runCatching { FlickrSignIn.run(apiKey, apiSecret, receiver) { DesktopHelper.browse(it) } }
            if (pending !== receiver) return@submit
            Snapshot.withMutableSnapshot { pending = null }
            result.onSuccess { finish(it, apiKey, apiSecret) }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                Snapshot.withMutableSnapshot { state = SignInState.Failed(describe(error, listOf(apiKey, apiSecret))) }
            }
        }
    }

    fun cancelSignIn() {
        pending?.cancel()
        pending = null
        state = SignInState.Idle
    }

    fun save() {
        if (!signedIn || state == SignInState.Waiting) return
        val profile = existing ?: UploaderProfile(name = NAME)
        onSave(profile.copy(name = name.ifBlank { NAME }, keepMetadata = keepMetadata, integration = settings))
    }

    ModalDialog(
        onDismiss,
        width = 500.dp,
        dismissOnScrim = false,
        title = { DialogTitle(existing?.name?.ifBlank { null } ?: NAME) },
        buttons = {
            TextButton("Cancel", onDismiss)
            TextButton(if (existing == null) "Add" else "Save", if (existing == null) "plus" else "tick", { save() }, style = ControlStyle.Primary, enabled = signedIn && state != SignInState.Waiting)
        },
    ) {
        existing?.let { LockedSecretsWarning(it.copy(integration = settings)) }

        if (!signedIn) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Step(1, "Open Flickr's app page, click Request an API Key and pick the non-commercial key.")
                LinkText("flickr.com/services/apps/create", { DesktopHelper.browse(URI(CREATE_APP_URL)) }, icon = "external-link")
                Step(2, "Give the app a name and a short description, accept the terms and submit.")
                Step(3, "Copy the Key and the Secret Flickr shows you into the fields below.")
                Step(4, "Click Sign in and allow access in your browser.")
            }
            Hint("There's no callback URL to set. Screencapper gives Flickr its own 127.0.0.1 address each time you sign in.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            LabeledField("Key", Modifier.weight(1f)) {
                SecretTextField(settings[FlickrSettings.API_KEY], { settings = FlickrSettings.withKeys(settings, it, settings[FlickrSettings.API_SECRET]) }, Modifier.fillMaxWidth(), placeholder = "Key", label = "Key")
            }
            LabeledField("Secret", Modifier.weight(1f)) {
                SecretTextField(settings[FlickrSettings.API_SECRET], { settings = FlickrSettings.withKeys(settings, settings[FlickrSettings.API_KEY], it) }, Modifier.fillMaxWidth(), placeholder = "Secret", label = "Secret")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            when {
                state == SignInState.Waiting -> {
                    Text("Waiting for you to allow access in your browser...", Modifier.weight(1f), theme.textColorSecondary, OneConfigTheme.typography.small)
                    TextButton("Cancel sign-in", { cancelSignIn() })
                }

                signedIn -> {
                    Text("Signed in as ${settings[FlickrSettings.USERNAME].ifBlank { settings[FlickrSettings.USER_NSID] }}", Modifier.weight(1f), theme.textColor, OneConfigTheme.typography.label)
                    TextButton("Sign in again", { signIn() }, enabled = hasKeys)
                }

                else -> {
                    Text(if (hasKeys) "Not signed in yet." else "Enter the key and secret first.", Modifier.weight(1f), theme.textColorSecondary, OneConfigTheme.typography.small)
                    TextButton("Sign in", "external-link", { signIn() }, style = ControlStyle.Primary, enabled = hasKeys)
                }
            }
        }
        (state as? SignInState.Failed)?.let { Text(it.message, Modifier, theme.error, OneConfigTheme.typography.small) }

        val public = FlickrSettings.isPublic(settings)
        LabeledField("Who can see uploads") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                Chip("Public", public, { settings = settings.with(FlickrSettings.VISIBILITY to FlickrSettings.PUBLIC) })
                Chip("Private", !public, { settings = settings.with(FlickrSettings.VISIBILITY to FlickrSettings.PRIVATE) })
            }
        }
        val shared = FlickrSettings.flag(settings, FlickrSettings.FRIENDS) || FlickrSettings.flag(settings, FlickrSettings.FAMILY)
        Hint(
            when {
                public -> "Anyone with the link can see the photo."
                shared -> "You still get a link, but it only opens for you and the people you picked."
                else -> "You still get a link, but only you can open it."
            },
        )

        CollapsibleSection("Advanced", advanced, { advanced = !advanced }) {
            LabeledField("Name", Modifier.fillMaxWidth()) {
                TextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = NAME, label = "Name")
            }
            LabeledField("Title", Modifier.fillMaxWidth()) {
                TextField(settings[FlickrSettings.TITLE], { settings = settings.with(FlickrSettings.TITLE to it) }, Modifier.fillMaxWidth(), placeholder = "No title", label = "Title")
            }
            LabeledField("Tags", Modifier.fillMaxWidth()) {
                TextField(settings[FlickrSettings.TAGS], { settings = settings.with(FlickrSettings.TAGS to it) }, Modifier.fillMaxWidth(), placeholder = "minecraft screenshot", label = "Tags")
            }
            Hint("Tags are split by spaces. The title and tags can use {name}, {date}, {time} and, with Keep metadata, {world}, {biome} and more.")
            if (!public) {
                ToggleRow("Friends can see it", FlickrSettings.flag(settings, FlickrSettings.FRIENDS), { settings = settings.with(FlickrSettings.FRIENDS to it.toString()) }, Modifier.fillMaxWidth())
                ToggleRow("Family can see it", FlickrSettings.flag(settings, FlickrSettings.FAMILY), { settings = settings.with(FlickrSettings.FAMILY to it.toString()) }, Modifier.fillMaxWidth())
            }
            ToggleRow("Keep metadata", keepMetadata, { keepMetadata = it }, Modifier.fillMaxWidth(), description = "Leaves player, world and place details in the uploaded file.")
        }

        SecretsNote()
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val theme = OneConfigTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
        Text("$number.", Modifier, theme.textColorSecondary, OneConfigTheme.typography.small)
        Text(text, Modifier.weight(1f), theme.textColor, OneConfigTheme.typography.small)
    }
}

private fun describe(error: Throwable, secrets: List<String>): String {
    val message = when (error) {
        is UploadException -> error.message.orEmpty()
        is TimeoutException -> "The sign-in timed out. Try again."
        else -> "The sign-in failed: ${error.message ?: error.javaClass.simpleName}"
    }
    return Redaction.redact(message, secrets.filter { it.isNotEmpty() })
}
