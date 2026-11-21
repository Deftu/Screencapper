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
import dev.deftu.screencapper.config.LinkMode
import dev.deftu.screencapper.gui.CollapsibleSection
import dev.deftu.screencapper.gui.Hint
import dev.deftu.screencapper.gui.IntegrationDialog
import dev.deftu.screencapper.gui.LockedSecretsWarning
import dev.deftu.screencapper.gui.SecretTextField
import dev.deftu.screencapper.gui.SecretsNote
import dev.deftu.screencapper.upload.SecretCipher
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.integration.nextcloud.ConnectionCheck
import dev.deftu.screencapper.upload.integration.nextcloud.NextcloudConfig
import dev.deftu.screencapper.upload.integration.nextcloud.NextcloudCredentials
import dev.deftu.screencapper.upload.integration.nextcloud.NextcloudLink
import dev.deftu.screencapper.upload.integration.nextcloud.NextcloudPaths
import dev.deftu.screencapper.upload.integration.nextcloud.NextcloudReplies
import dev.deftu.screencapper.upload.integration.nextcloud.NextcloudSignIn
import dev.deftu.screencapper.upload.integration.nextcloud.NextcloudUser
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.LabeledField
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.Multithreading
import java.io.IOException
import java.net.URI
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

internal val NextcloudDialogEntry = IntegrationDialog(
    NextcloudConfig.KIND,
    "Nextcloud",
    "cloud",
    "Your own cloud. Saves to a folder, with a link if you want one.",
) { existing, onSave, onDismiss -> NextcloudDialog(existing, onSave, onDismiss) }

private sealed interface NextcloudProgress {
    object Idle : NextcloudProgress
    class Working(val text: String) : NextcloudProgress
    class Done(val text: String) : NextcloudProgress
    class Failed(val text: String) : NextcloudProgress
}

private class NextcloudBackground {
    private val generation = AtomicInteger()
    private var task: Future<*>? = null

    fun run(block: (current: () -> Boolean) -> Unit) {
        cancel()
        val mine = generation.get()
        task = Multithreading.submit(Runnable { block { generation.get() == mine } })
    }

    fun cancel() {
        generation.incrementAndGet()
        task?.cancel(true)
        task = null
    }
}

private fun onUi(current: () -> Boolean, change: () -> Unit) {
    Snapshot.withMutableSnapshot { if (current()) change() }
}

private fun failureText(error: Throwable): String = when (error) {
    is UploadException -> error.message.orEmpty()
    is IOException -> NextcloudReplies.UNREACHABLE
    else -> "Something went wrong (${error.javaClass.simpleName}). Try again."
}

private fun connectedUser(check: ConnectionCheck): NextcloudUser = when (check) {
    is ConnectionCheck.Ok -> check.user
    is ConnectionCheck.Failed -> throw UploadException(check.message)
}

@Composable
private fun NextcloudDialog(existing: UploaderProfile?, onSave: (UploaderProfile) -> Unit, onDismiss: () -> Unit) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val initial = remember(existing?.id) { existing?.integration?.let { NextcloudConfig.from(it) } }
    var server by remember(existing?.id) { mutableStateOf(initial?.server.orEmpty()) }
    var user by remember(existing?.id) { mutableStateOf(initial?.user.orEmpty()) }
    var appPassword by remember(existing?.id) { mutableStateOf(initial?.appPassword.orEmpty()) }
    var userId by remember(existing?.id) { mutableStateOf(initial?.userId.orEmpty()) }
    var userIdFor by remember(existing?.id) { mutableStateOf(initial?.let { it.server to it.user }) }
    var folder by remember(existing?.id) { mutableStateOf(initial?.folder ?: NextcloudConfig.DEFAULT_FOLDER) }
    var link by remember(existing?.id) { mutableStateOf(initial?.link ?: NextcloudLink.PUBLIC) }
    var manual by remember(existing?.id) { mutableStateOf(false) }
    var progress by remember(existing?.id) { mutableStateOf<NextcloudProgress>(NextcloudProgress.Idle) }
    val background = remember { NextcloudBackground() }
    DisposableEffect(Unit) { onDispose { background.cancel() } }

    val serverProblem = if (server.isBlank()) "" else NextcloudPaths.serverProblem(server)
    val folderProblem = NextcloudPaths.folderProblem(folder)
    val signedIn = user.isNotBlank() && appPassword.isNotBlank() && !SecretCipher.isSealed(appPassword)
    val busy = progress is NextcloudProgress.Working
    val canSave = serverProblem == null && folderProblem == null && user.isNotBlank() && appPassword.isNotBlank()

    fun edited() {
        if (!busy) progress = NextcloudProgress.Idle
    }

    fun draft(): UploaderProfile {
        val address = if (serverProblem == null) NextcloudPaths.normalizeServer(server) else server.trim()
        val login = user.trim()
        val config = NextcloudConfig(
            server = address,
            user = login,
            appPassword = appPassword.trim(),
            userId = if (userIdFor == (address to login)) userId else "",
            folder = folder.trim(),
            link = link,
        )
        return (existing ?: UploaderProfile(name = "Nextcloud")).copy(
            integration = config.toSettings(),
            linkMode = if (link == NextcloudLink.PUBLIC) LinkMode.LINK else LinkMode.NONE,
        )
    }

    fun adopt(credentials: NextcloudCredentials, account: NextcloudUser) {
        server = credentials.server
        user = credentials.user
        appPassword = credentials.appPassword
        userId = account.id
        userIdFor = credentials.server to credentials.user
    }

    fun signIn() {
        if (serverProblem != null) return
        val address = NextcloudPaths.normalizeServer(server)
        progress = NextcloudProgress.Working("Opening your browser...")
        background.run { current ->
            val outcome = runCatching {
                val signIn = NextcloudSignIn()
                val start = signIn.begin(address)
                if (!DesktopHelper.browse(URI(start.loginUrl))) throw UploadException("Couldn't open your browser.")
                onUi(current) { progress = NextcloudProgress.Working("Finish signing in in your browser, then come back here.") }
                val grant = signIn.await(start)
                val granted = grant.server.takeIf { it.isNotBlank() && NextcloudPaths.serverProblem(it) == null }?.let(NextcloudPaths::normalizeServer) ?: address
                val credentials = NextcloudCredentials(granted, grant.loginName, grant.appPassword)
                credentials to connectedUser(signIn.check(credentials))
            }
            onUi(current) {
                outcome
                    .onSuccess { (credentials, account) ->
                        adopt(credentials, account)
                        progress = NextcloudProgress.Done("Signed in as ${account.displayName}.")
                    }
                    .onFailure { progress = NextcloudProgress.Failed(failureText(it)) }
            }
        }
    }

    fun testConnection() {
        if (serverProblem != null || !signedIn) return
        val credentials = NextcloudCredentials(NextcloudPaths.normalizeServer(server), user.trim(), appPassword.trim())
        progress = NextcloudProgress.Working("Checking...")
        background.run { current ->
            val outcome = runCatching { connectedUser(NextcloudSignIn().check(credentials)) }
            onUi(current) {
                outcome
                    .onSuccess { account ->
                        adopt(credentials, account)
                        progress = NextcloudProgress.Done("Connected as ${account.displayName}.")
                    }
                    .onFailure { progress = NextcloudProgress.Failed(failureText(it)) }
            }
        }
    }

    ModalDialog(
        onDismiss,
        width = 480.dp,
        dismissOnScrim = false,
        title = { DialogTitle(existing?.name ?: "Nextcloud") },
        buttons = {
            TextButton("Cancel", onDismiss)
            TextButton(
                if (existing == null) "Add" else "Save",
                if (existing == null) "plus" else "tick",
                { if (canSave) onSave(draft()) },
                style = ControlStyle.Primary,
                enabled = canSave,
            )
        },
    ) {
        LockedSecretsWarning(draft())
        TextField(
            server,
            {
                server = it
                edited()
            },
            Modifier.fillMaxWidth(),
            placeholder = "https://cloud.example.com",
            label = "Server address",
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            TextButton(
                if (signedIn) "Sign in again" else "Sign in with browser",
                { signIn() },
                style = if (signedIn) ControlStyle.Secondary else ControlStyle.Primary,
                enabled = serverProblem == null && !busy,
            )
            if (busy) {
                TextButton("Stop", {
                    background.cancel()
                    progress = NextcloudProgress.Idle
                })
            } else {
                TextButton("Test connection", { testConnection() }, enabled = serverProblem == null && signedIn)
            }
        }
        when (val current = progress) {
            is NextcloudProgress.Working -> Hint(current.text)
            is NextcloudProgress.Done -> Hint(current.text, "tick")
            is NextcloudProgress.Failed -> Hint(current.text, "alert-circle", theme.error)
            NextcloudProgress.Idle -> if (signedIn) Hint("Signed in as ${user.trim()}.", "tick") else Hint("Opens your Nextcloud in the browser. Works with two-factor sign-in.")
        }

        CollapsibleSection("Use an app password instead", manual, { manual = !manual }) {
            Hint("1. In Nextcloud, open Settings > Security.")
            Hint("2. Under Devices & sessions, click Create new app password and name it Screencapper.")
            Hint("3. Copy the login and password it shows into these fields.")
            TextField(
                user,
                {
                    user = it
                    edited()
                },
                Modifier.fillMaxWidth(),
                placeholder = "Login",
                label = "Username",
            )
            SecretTextField(
                appPassword,
                {
                    appPassword = it
                    edited()
                },
                Modifier.fillMaxWidth(),
                placeholder = "App password",
                label = "App password",
            )
        }

        LabeledField("Folder", Modifier.fillMaxWidth()) {
            TextField(folder, { folder = it }, Modifier.fillMaxWidth(), placeholder = "Top folder", label = "Folder")
        }
        Hint("Made for you if it's missing. Use / for folders inside folders.")

        LabeledField("Link") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                Chip("Public link", link == NextcloudLink.PUBLIC, { link = NextcloudLink.PUBLIC })
                Chip("No link", link == NextcloudLink.NONE, { link = NextcloudLink.NONE })
            }
        }
        Hint(if (link == NextcloudLink.PUBLIC) "Anyone with the link can see the screenshot." else "Only saves the screenshot to your Nextcloud.")

        val problem = serverProblem?.takeIf { it.isNotEmpty() } ?: folderProblem
        if (problem != null) Text(problem, Modifier, theme.error, OneConfigTheme.typography.small)
        SecretsNote()
    }
}
