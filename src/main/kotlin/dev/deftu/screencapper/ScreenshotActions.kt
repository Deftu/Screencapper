package dev.deftu.screencapper

import dev.deftu.screencapper.clipboard.FileListSelection
import dev.deftu.screencapper.clipboard.MultiCopy
import dev.deftu.screencapper.clipboard.MultiCopyResult
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.ScreenshotPreviewHud
import dev.deftu.screencapper.gui.ScreenshotsPage
import dev.deftu.screencapper.library.UserDataStore
import dev.deftu.screencapper.metadata.MetadataIndex
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import dev.deftu.screencapper.screenshot.UploadHistory
import dev.deftu.screencapper.screenshot.UploadRecord
import dev.deftu.screencapper.screenshot.capture.FastCapture
import dev.deftu.screencapper.screenshot.naming.RenameCheck
import dev.deftu.screencapper.screenshot.naming.ScreenshotPaths
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.Uploaders
import org.polyfrost.oneconfig.api.notifications.v1.NotificationAction
import org.polyfrost.oneconfig.api.notifications.v1.NotificationType
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.utils.v1.ClipboardHelper
import org.polyfrost.oneconfig.utils.v1.Multithreading
import java.util.concurrent.ConcurrentHashMap

object ScreenshotActions {
    private val uploading = ConcurrentHashMap.newKeySet<String>()

    fun onCaptured(screenshot: Screenshot) {
        ScreenshotLibrary.changed()
        ScreenshotPreviewHud.show(screenshot)
        if (ScreencapperConfig.autoCopy) copy(screenshot, quiet = true)
        if (ScreencapperConfig.autoUpload) upload(screenshot)
    }

    fun isUploading(screenshot: Screenshot): Boolean = screenshot.id in uploading

    fun copy(screenshot: Screenshot, quiet: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        Multithreading.submit {
            val copied = runCatching { ClipboardHelper.setImage(screenshot.readImage()) }
                .onFailure { Screencapper.LOGGER.error("Failed to copy {}", screenshot.name, it) }
                .getOrDefault(false)
            when {
                !copied -> notifyError("Copy failed", "Couldn't copy ${screenshot.name} to the clipboard.")
                !quiet -> notifySuccess("Copied", "${screenshot.name} is on your clipboard.")
            }
            onDone(copied)
        }
    }

    fun copyAll(screenshots: List<Screenshot>) {
        if (screenshots.size == 1) {
            copy(screenshots.first())
            return
        }
        Multithreading.submit {
            val files = screenshots.map { it.file }
            val result = MultiCopy.copy(files, { ClipboardHelper.setTransferable(FileListSelection(it)) }, { ClipboardHelper.setString(it) })
            when (result) {
                MultiCopyResult.NothingToCopy -> notifyError("Copy failed", "Those screenshots no longer exist.")
                is MultiCopyResult.Single -> screenshots.firstOrNull { it.file.absoluteFile == result.file }?.let { copy(it) }
                is MultiCopyResult.Files -> notifySuccess("Copied", "${result.files.size} screenshots are on your clipboard.")
                is MultiCopyResult.Paths -> notifySuccess("Copied file paths", "Your clipboard can't hold files here, so the paths of ${result.files.size} screenshots were copied instead.")
                is MultiCopyResult.Failed -> {
                    Screencapper.LOGGER.error("Failed to copy {} screenshots", result.files.size)
                    notifyError("Copy failed", "Couldn't copy ${result.files.size} screenshots to the clipboard.")
                }
            }
        }
    }

    fun copyLink(screenshot: Screenshot) {
        val link = UploadHistory.latestLink(screenshot) ?: return notifyError("No link yet", "${screenshot.name} has no uploaded link.")
        ClipboardHelper.setString(link.url)
        notifySuccess("Link copied", link.url)
    }

    fun upload(screenshot: Screenshot, profile: UploaderProfile? = null, onDone: (String?) -> Unit = {}) {
        if (!uploading.add(screenshot.id)) {
            notifyError("Already uploading", "${screenshot.name} is still uploading.")
            return
        }

        val uploader = profile ?: ScreencapperConfig.defaultProfile
        val service = uploader.name
        val progress = try {
            ScreenshotLibrary.changed()
            ChatCards.uploading(screenshot, uploader)
            Notifications.send("Uploading", "Sending ${screenshot.name} to $service.", NotificationType.PROGRESS, duration = -1f)
        } catch (e: Throwable) {
            uploading.remove(screenshot.id)
            throw e
        }
        Multithreading.submit {
            val result = runCatching { Uploaders.upload(screenshot, uploader) }
                .map { uploaded -> UploadRecord(uploader.id, uploader.name, uploaded.url, System.currentTimeMillis(), uploaded.deletion) }
            try {
                result.onSuccess { UploadHistory.add(screenshot, it) }
            } finally {
                uploading.remove(screenshot.id)
            }
            runCatching { Notifications.dismiss(progress) }
            result.onSuccess { record ->
                ChatCards.uploadFinished(screenshot, record)
                ScreenshotLibrary.changed()
                if (record.hasLink) {
                    ClipboardHelper.setString(record.url)
                    notifyLink(screenshot, record.url, "Uploaded to $service")
                } else {
                    notifySuccess("Uploaded to $service", "${screenshot.name} was sent to $service.")
                }
                onDone(record.url)
            }.onFailure {
                Screencapper.LOGGER.error("Failed to upload {}", screenshot.name, it)
                ScreenshotLibrary.changed()
                ChatCards.uploadFinished(screenshot, null)
                notifyError("Upload failed", it.message ?: it.javaClass.simpleName)
                onDone(null)
            }
        }
    }

    fun open(screenshot: Screenshot) {
        if (!DesktopHelper.open(screenshot.file)) notifyError("Couldn't open", screenshot.name)
    }

    fun openFolder(screenshot: Screenshot? = null) {
        val dir = screenshot?.file?.parentFile?.takeIf { it.isDirectory } ?: ScreenshotLibrary.directory.apply { mkdirs() }
        if (!DesktopHelper.open(dir)) notifyError("Couldn't open", dir.absolutePath)
    }

    fun delete(screenshot: Screenshot, quiet: Boolean = false): Boolean {
        val deleted = screenshot.file.delete()
        if (deleted) {
            UploadHistory.forget(screenshot)
            MetadataIndex.forget(screenshot.id)
            UserDataStore.forget(screenshot.id)
            ScreenshotLibrary.changed()
            ScreenshotPreviewHud.dismiss(screenshot)
            ChatCards.deleted(screenshot)
            if (!quiet) notifySuccess("Deleted", "${screenshot.name} was deleted.")
        } else {
            ChatCards.showActions(screenshot)
            if (!quiet) notifyError("Delete failed", "Couldn't delete ${screenshot.name}.")
        }
        return deleted
    }

    fun rename(screenshot: Screenshot, newName: String): Screenshot? {
        if (isUploading(screenshot)) {
            notifyError("Couldn't rename", "${screenshot.name} is still uploading.")
            return null
        }
        val root = ScreenshotLibrary.directory
        val target = when (val check = ScreenshotPaths.renameTarget(root, screenshot.file, newName)) {
            is RenameCheck.Invalid -> {
                notifyError("Couldn't rename", check.reason)
                return null
            }
            is RenameCheck.Ok -> check
        }
        if (!FastCapture.claim(target.file)) {
            notifyError("Couldn't rename", "A screenshot is being saved as ${target.id} right now.")
            return null
        }
        val moved = try {
            runCatching {
                target.file.parentFile?.mkdirs()
                screenshot.file.renameTo(target.file)
            }.onFailure { Screencapper.LOGGER.error("Failed to rename {}", screenshot.id, it) }.getOrDefault(false)
        } finally {
            FastCapture.release(target.file)
        }
        if (!moved) {
            notifyError("Couldn't rename", "${screenshot.name} couldn't be renamed.")
            return null
        }

        val original = ScreenshotPaths.originalOf(screenshot.file)
        val originalStuck = original.isFile && ScreenshotPaths.ownsOriginal(screenshot.file) && runCatching {
            val movedOriginal = ScreenshotPaths.originalOf(target.file)
            if (movedOriginal.exists()) error("$movedOriginal already exists")
            movedOriginal.parentFile?.mkdirs()
            if (!original.renameTo(movedOriginal)) error("rename failed")
        }.onFailure { Screencapper.LOGGER.warn("Couldn't move the original of {}", screenshot.id, it) }.isFailure

        val renamed = Screenshot(target.file, target.id)
        UploadHistory.move(screenshot.id, renamed.id)
        MetadataIndex.move(screenshot.id, renamed.id)
        UserDataStore.move(screenshot.id, renamed.id)
        ScreenshotPreviewHud.dismiss(screenshot)
        ChatCards.renamed(screenshot, renamed)
        ScreenshotLibrary.changed()
        notifySuccess("Renamed", "${screenshot.name} is now ${renamed.id}.")
        if (originalStuck) notifyError("Original not moved", "The untouched copy of ${screenshot.name} couldn't be renamed and is still at ${ScreenshotPaths.idOf(root, original)}.")
        return renamed
    }

    fun deleteAll(screenshots: List<Screenshot>) {
        if (screenshots.size == 1) {
            delete(screenshots.first())
            return
        }
        val failed = screenshots.count { !delete(it, quiet = true) }
        val deleted = screenshots.size - failed
        if (deleted > 0) notifySuccess("Deleted", "$deleted screenshots were deleted.")
        if (failed > 0) notifyError("Delete failed", "Couldn't delete $failed screenshots.")
    }

    @OptIn(org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi::class)
    fun openManager(focus: Screenshot? = null) {
        ScreenshotsPage.focus(focus)
        OneConfigUI.open(ScreenshotsPage)
    }

    fun browse(url: String) {
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) {
            notifyError("Couldn't open link", url)
            return
        }
        if (!DesktopHelper.browse(uri)) notifyError("Couldn't open link", url)
    }

    fun notifySuccess(title: String, message: String) {
        Notifications.success(title, message)
    }

    fun notifyError(title: String, message: String) {
        Notifications.error(title, message)
    }

    private fun notifyLink(screenshot: Screenshot, url: String, title: String) {
        Notifications.send(
            title, url, NotificationType.SUCCESS,
            actions = listOf(
                NotificationAction("Open link", primary = true) { browse(url) },
                NotificationAction("Copy link") { ClipboardHelper.setString(url) },
            ),
        )
    }
}
