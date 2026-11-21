package dev.deftu.screencapper.screenshot.capture.input

import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.ShutterSound
import dev.deftu.screencapper.metadata.MetadataCollector
import dev.deftu.screencapper.screenshot.Panoramas
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import dev.deftu.screencapper.screenshot.capture.CaptureKind
import dev.deftu.screencapper.screenshot.capture.FastCapture
import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfile
import dev.deftu.screencapper.screenshot.capture.highres.PanoramaRenderer
import dev.deftu.screencapper.screenshot.naming.ScreenshotNames
import dev.deftu.screencapper.screenshot.panorama.AutoPanoramas
import dev.deftu.screencapper.screenshot.panorama.PanoramaFolder
import dev.deftu.screencapper.screenshot.panorama.PanoramaLayout
import dev.deftu.screencapper.screenshot.panorama.TitlePanorama
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

internal object PanoramaShot {
    private class Face(val width: Int, val height: Int, val pixels: Supplier<IntArray>, val release: Runnable)

    private class Session(val folder: File, val metadata: Map<String, String>, val onLeave: Boolean) {
        val faces = arrayOfNulls<Face>(PanoramaLayout.FACES.size)
        var failed = false
        var saving = false
        var shownAs: String? = null
    }

    fun start(faceSize: Int): Boolean {
        if (PanoramaRenderer.isCapturing) return false
        val metadata = MetadataCollector.collect()
        val folder = try {
            val base = ScreenshotNames.normal()
            reserveFolder { attempt -> PanoramaLayout.folderPath(base, attempt) }
        } catch (e: IOException) {
            Screencapper.LOGGER.error("Couldn't reserve a panorama folder", e)
            ScreenshotActions.notifyError("Couldn't take panorama", "The screenshots folder couldn't be written to.")
            return true
        }
        val session = Session(folder, metadata, false)
        if (!PanoramaRenderer.start(faceSize, sink(session), onRendered(session))) {
            folder.delete()
            return false
        }
        ShutterSound.play()
        return true
    }

    fun takeOnLeave(faceSize: Int) {
        val began = System.nanoTime()
        val time = LocalDateTime.now()
        val world = ScreenshotNames.current().world
        val folder = reserveFolder { attempt -> AutoPanoramas.folderPath(time, world, attempt) }
        val session = Session(folder, MetadataCollector.collect(), true)
        if (!PanoramaRenderer.renderNow(faceSize, AutoPanoramas.MAX_SUPERSAMPLE, sink(session), onRendered(session))) {
            folder.delete()
            return
        }
        Screencapper.LOGGER.info("Rendered the leave panorama at {}px in {} ms", faceSize, (System.nanoTime() - began) / 1_000_000)
    }

    private fun sink(session: Session) = PanoramaRenderer.FaceSink { face, width, height, pixels, release ->
        MainThread.run { arrive(session, face, Face(width, height, pixels, release)) }
    }

    private fun onRendered(session: Session): (Throwable?) -> Unit = { error -> if (error != null) MainThread.run { fail(session, error) } }

    private fun reserveFolder(path: (Int) -> String): File {
        val root = File(InputBridge.gameDirectory, "screenshots")
        var attempt = 1
        while (true) {
            val folder = File(root, path(attempt))
            if (!folder.exists()) {
                if (folder.mkdirs()) return folder
                throw IOException("Couldn't create the panorama folder ${folder.path}")
            }
            attempt++
        }
    }

    private fun arrive(session: Session, index: Int, face: Face) {
        if (session.failed || session.faces[index] != null) {
            face.release.run()
            return
        }
        session.faces[index] = face
        if (!session.faces.all { it != null }) return
        if (session.onLeave) showOnTitle(session)
        save(session)
    }

    private fun showOnTitle(session: Session) {
        try {
            val id = PanoramaFolder.idOf(ScreenshotLibrary.directory, session.folder) ?: return
            val size = session.faces[0]!!.width
            val pixels = PanoramaLayout.FACES.indices.map { index -> readInPlace(session, index) }
            if (TitlePanorama.showLeave(id, session.folder, size, pixels)) session.shownAs = id
        } catch (e: Throwable) {
            Screencapper.LOGGER.warn("Couldn't show the leave panorama right away, it'll show once it's saved", e)
        }
    }

    private fun readInPlace(session: Session, index: Int): IntArray {
        val face = session.faces[index]!!
        val pixels = try {
            face.pixels.get()
        } catch (e: Throwable) {
            session.faces[index] = Face(face.width, face.height, { throw e }, {})
            throw e
        } finally {
            face.release.run()
        }
        session.faces[index] = Face(face.width, face.height, { pixels }, {})
        return pixels
    }

    private fun fail(session: Session, error: Throwable) {
        if (session.failed || session.saving) return
        session.failed = true
        Screencapper.LOGGER.error("Panorama capture failed", error)
        session.faces.forEachIndexed { index, face ->
            face?.release?.run()
            session.faces[index] = null
        }
        session.folder.delete()
        if (!session.onLeave) ScreenshotActions.notifyError("Couldn't take panorama", "The capture failed or was cancelled.")
    }

    private fun save(session: Session) {
        session.saving = true
        val remaining = AtomicInteger(PanoramaLayout.FACES.size)
        val firstError = AtomicReference<Throwable?>()
        val faceSize = session.faces[0]!!.width
        for (index in PanoramaLayout.FACES.indices) {
            val face = session.faces[index]!!
            session.faces[index] = null
            val file = FastCapture.reserve(session.folder, PanoramaLayout.FACES[index].fileName.substringBeforeLast('.'), "png")
            FastCapture.save(file, face.width, face.height, face.pixels, face.release, { error ->
                if (error != null) firstError.compareAndSet(null, error)
                if (remaining.decrementAndGet() == 0) MainThread.run { finish(session, faceSize, firstError.get()) }
            }, CaptureKind.HIGH_RES, session.metadata, CompressionProfile.LOSSLESS, preview = false)
        }
    }

    private fun finish(session: Session, faceSize: Int, error: Throwable?) {
        if (!session.onLeave) {
            if (error != null) ScreenshotActions.notifyError("Couldn't take panorama", "Some faces couldn't be saved.")
            else Panoramas.saved(session.folder, faceSize)
            return
        }
        if (error != null) Panoramas.discardLeavePanorama(session.folder)
        val kept = error == null && Panoramas.savedOnLeave(session.folder)
        session.shownAs?.let { TitlePanorama.settleLeave(it, kept) }
    }
}
