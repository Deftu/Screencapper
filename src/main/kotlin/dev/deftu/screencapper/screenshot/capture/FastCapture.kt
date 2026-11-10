package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.ScreenshotPreviewHud
import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Saves screenshots without touching the render thread or the game's shared IO pool.
 *
 * The render thread only hands over the captured pixels. Everything after that (pixel conversion, the preview, PNG
 * encoding and the write) runs on Screencapper's own threads, so a busy IO pool on a modded client can't delay it.
 */
object FastCapture {
    internal val encoderThreads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 8)
    private val NAME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT)

    private val coordinator = Executors.newSingleThreadExecutor(threads("Screencapper capture"))
    internal val encoderPool = Executors.newFixedThreadPool(encoderThreads, threads("Screencapper encoder"))

    /** Whether captures should go through here instead of vanilla's saving code */
    @JvmStatic
    fun isActive(): Boolean = ScreencapperConfig.enabled && ScreencapperConfig.fastCapture

    /** The file a new screenshot is saved to, matching vanilla's naming */
    @JvmStatic
    @JvmOverloads
    fun nextFile(gameDirectory: File, forceName: String?, kind: CaptureKind = CaptureKind.NORMAL, prefix: String? = null): File {
        val directory = File(gameDirectory, "screenshots").apply { mkdirs() }
        if (forceName != null) return File(directory, forceName)
        val name = prefix ?: synchronized(NAME_FORMAT) { NAME_FORMAT.format(Date()) }
        val extension = ScreenshotEncoding.extension(kind)
        var count = 1
        while (true) {
            val file = File(directory, name + (if (count == 1) "" else "_$count") + ".$extension")
            if (!file.exists()) return file
            count++
        }
    }

    /**
     * Saves pixels captured on the render thread.
     *
     * [pixels] is called on a background thread and must return [width] x [height] ARGB pixels, top row first; [release]
     * frees whatever held them. [onDone] gets null on success or the error, also on a background thread.
     */
    @JvmStatic
    @JvmOverloads
    fun save(
        file: File,
        width: Int,
        height: Int,
        pixels: Supplier<IntArray>,
        release: Runnable,
        onDone: Consumer<Throwable?>,
        kind: CaptureKind = CaptureKind.NORMAL,
        metadata: Map<String, String> = emptyMap(),
    ) {
        coordinator.execute {
            val error = runCatching {
                val argb = try {
                    pixels.get()
                } finally {
                    release.run()
                }
                val image = CapturedImage(argb, width, height)
                runCatching { ScreenshotPreviewHud.showCaptured(file, argb, width, height) }
                    .onFailure { Screencapper.LOGGER.warn("Couldn't show the screenshot preview", it) }
                write(file, image, kind, metadata)
            }.exceptionOrNull()
            if (error != null) Screencapper.LOGGER.error("Couldn't save screenshot {}", file.name, error)
            onDone.accept(error)
        }
    }

    /** Converts bottom-up pixels from an OpenGL read of [sourceWidth] wide rows into a top-down [width] x [height] image */
    @JvmStatic
    fun flipped(source: IntArray, sourceWidth: Int, width: Int, height: Int): IntArray {
        val out = IntArray(width * height)
        for (y in 0 until height) System.arraycopy(source, (height - 1 - y) * sourceWidth, out, y * width, width)
        return out
    }

    /** Saves an image that's already in memory, such as a stitched high-res shot */
    @JvmStatic
    fun saveImage(file: File, image: CapturedImage, kind: CaptureKind, metadata: Map<String, String>, onDone: Consumer<Throwable?>) =
        save(file, image.width, image.height, { image.argb }, {}, onDone, kind, metadata)

    private fun write(file: File, image: CapturedImage, kind: CaptureKind, metadata: Map<String, String>) {
        val temp = File(file.parentFile, ".${file.name}.tmp")
        try {
            BufferedOutputStream(Files.newOutputStream(temp.toPath()), 1 shl 16).use { out ->
                ScreenshotEncoding.encode(image, kind, metadata, out)
            }
            // Moved into place in one step, so nothing ever sees a half-written screenshot.
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temp.delete()
        }
    }

    private fun threads(name: String): ThreadFactory {
        val count = AtomicInteger()
        return ThreadFactory { task ->
            Thread(task, "$name ${count.incrementAndGet()}").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }
}
