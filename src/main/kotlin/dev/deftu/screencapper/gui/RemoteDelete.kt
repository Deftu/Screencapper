@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.runtime.Composable
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.SharedUploads
import dev.deftu.screencapper.screenshot.UploadHistory
import dev.deftu.screencapper.screenshot.UploadRecord
import dev.deftu.screencapper.upload.Uploaders
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Confirm
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton

@Composable
fun RemoteDeleteButton(screenshot: Screenshot, record: UploadRecord) {
    val kind = record.deletion?.kind ?: DeletionKind.NONE
    val deleting = Uploaders.isDeleting(record)
    val host = record.uploaderName
    val sharedWith = SharedUploads.describe(UploadHistory.sharing(screenshot, record), screenshot.id)
    val problem = if (sharedWith == null) Uploaders.deletionProblem(record) else null
    val hint = when {
        deleting -> "Deleting..."
        sharedWith != null && kind != DeletionKind.NONE -> "Remove this link. The upload is still used by $sharedWith"
        problem != null -> problem
        kind == DeletionKind.REQUEST -> "Delete from $host"
        kind == DeletionKind.BROWSER -> "Open $host's deletion page"
        else -> "$host can't delete this upload"
    }
    IconButton(
        "trash",
        "Delete upload",
        {
            when {
                sharedWith != null -> Confirm.ask(
                    "Remove link?",
                    "This upload is still used by $sharedWith, so it stays on $host. Only this link is removed from your history.",
                    confirmLabel = "Remove link",
                    onConfirm = { Uploaders.deleteRemote(screenshot, record) },
                )

                problem != null -> Confirm.ask(
                    "Can't delete upload",
                    "$problem Remove the link from your history instead?",
                    confirmLabel = "Remove link",
                    onConfirm = { Uploaders.removeLink(screenshot, record) },
                )

                kind == DeletionKind.BROWSER -> Confirm.ask(
                    "Delete upload?",
                    "$host's deletion page opens in your browser. The link is removed from your history.",
                    confirmLabel = "Open page",
                    onConfirm = { Uploaders.deleteRemote(screenshot, record) },
                )

                else -> Confirm.ask(
                    "Delete upload?",
                    "${screenshot.name} will be deleted from $host. Your local copy stays.",
                    confirmLabel = "Delete",
                    onConfirm = { Uploaders.deleteRemote(screenshot, record) },
                )
            }
        },
        enabled = kind != DeletionKind.NONE && !deleting,
        size = ControlSize.Compact,
        danger = true,
        tooltip = hint,
    )
}
