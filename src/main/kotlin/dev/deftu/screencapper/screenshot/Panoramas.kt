package dev.deftu.screencapper.screenshot

import dev.deftu.screencapper.ChatCards
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.FastCapture
import dev.deftu.screencapper.screenshot.panorama.AutoPanoramas
import dev.deftu.screencapper.screenshot.panorama.PanoramaFolder
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import java.io.File
import java.nio.file.Files

object Panoramas {
    fun saved(folder: File, faceSize: Int) {
        val id = PanoramaFolder.idOf(ScreenshotLibrary.directory, folder) ?: return
        ChatCards.panoramaSaved(id, faceSize)
    }

    fun savedOnLeave(folder: File): Boolean {
        val id = PanoramaFolder.idOf(ScreenshotLibrary.directory, folder)
        if (id == null || PanoramaFolder.validated(ScreenshotLibrary.directory, id) == null) {
            Screencapper.LOGGER.warn("The leave panorama in {} couldn't be read back, keeping the title background", folder.path)
            return false
        }
        store(id)
        pruneLeavePanoramas(folder.parentFile, AutoPanoramas.nameOf(ScreencapperConfig.titlePanorama))
        return true
    }

    fun discardLeavePanorama(folder: File) {
        FastCapture.encoderPool.execute { deleteFolder(folder) }
    }

    private fun pruneLeavePanoramas(dir: File, protected: String?) {
        FastCapture.encoderPool.execute {
            val names = dir.list()?.toList() ?: return@execute
            for (name in AutoPanoramas.toPrune(names, AutoPanoramas.KEEP, protected)) deleteFolder(File(dir, name))
        }
    }

    private fun deleteFolder(folder: File) {
        if (Files.isSymbolicLink(folder.toPath()) || !folder.isDirectory) return
        folder.listFiles()?.forEach { if (it.isFile) it.delete() }
        if (!folder.delete()) Screencapper.LOGGER.warn("Couldn't delete the leave panorama folder {}", folder.path)
    }

    fun isTitleBackground(id: String): Boolean = id.isNotEmpty() && id == ScreencapperConfig.titlePanorama

    fun setTitleBackground(id: String) {
        if (PanoramaFolder.validated(ScreenshotLibrary.directory, id, allowLegacy = isTitleBackground(id)) == null) {
            ScreenshotActions.notifyError("Couldn't set the title background", "This panorama was moved, or no longer has six square faces of the same size.")
            return
        }
        store(id)
        ScreenshotActions.notifySuccess("Title background set", "The title screen now shows this panorama.")
    }

    fun resetTitleBackground(id: String) {
        if (isTitleBackground(id)) resetTitleBackground() else ChatCards.panoramasChanged()
    }

    fun resetTitleBackground() {
        if (ScreencapperConfig.titlePanorama.isEmpty()) return
        store("")
        ScreenshotActions.notifySuccess("Title background reset", "The title screen shows Minecraft's panorama again.")
    }

    fun openFolder(id: String) {
        val dir = PanoramaFolder.resolve(ScreenshotLibrary.directory, id, allowLegacy = isTitleBackground(id))
        if (dir == null) {
            ScreenshotActions.notifyError("Panorama not found", "It was deleted or moved out of your panoramas folder.")
            return
        }
        if (!DesktopHelper.open(dir)) ScreenshotActions.notifyError("Couldn't open", dir.absolutePath)
    }

    private fun store(id: String) {
        ScreencapperConfig.titlePanorama = id
        ScreencapperConfig.save()
        ChatCards.panoramasChanged()
    }
}
