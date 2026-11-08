package dev.deftu.screencapper

import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.ScreenshotPreviewHud
import dev.deftu.screencapper.gui.ScreenshotsPage
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import dev.deftu.screencapper.screenshot.UploadHistory
import dev.deftu.screencapper.screenshot.UploadRecord
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

    fun isUploading(screenshot: Screenshot): Boolean = screenshot.name in uploading

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

    fun copyLink(screenshot: Screenshot) {
        val link = UploadHistory.latest(screenshot) ?: return notifyError("No link yet", "${screenshot.name} hasn't been uploaded.")
        ClipboardHelper.setString(link.url)
        notifySuccess("Link copied", link.url)
    }

    fun upload(screenshot: Screenshot, profile: UploaderProfile? = null, onDone: (String?) -> Unit = {}) {
        if (!uploading.add(screenshot.name)) {
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
            uploading.remove(screenshot.name)
            throw e
        }
        Multithreading.submit {
            val result = runCatching { Uploaders.upload(screenshot, uploader) }
            uploading.remove(screenshot.name)
            runCatching { Notifications.dismiss(progress) }
            result.onSuccess { url ->
                val record = UploadRecord(uploader.id, uploader.name, url, System.currentTimeMillis())
                UploadHistory.add(screenshot, record)
                ChatCards.uploadFinished(screenshot, record)
                ScreenshotLibrary.changed()
                ClipboardHelper.setString(url)
                notifyLink(screenshot, url, "Uploaded to $service")
                onDone(url)
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

    fun openFolder() {
        val dir = ScreenshotLibrary.directory.apply { mkdirs() }
        if (!DesktopHelper.open(dir)) notifyError("Couldn't open", dir.absolutePath)
    }

    fun delete(screenshot: Screenshot, quiet: Boolean = false): Boolean {
        val deleted = screenshot.file.delete()
        if (deleted) {
            UploadHistory.forget(screenshot)
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

    /** Opens an uploaded link in the browser, refusing anything that isn't a well-formed http(s) URL */
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
