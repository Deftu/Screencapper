package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.ScreenshotPreviewHud
import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfile
import dev.deftu.screencapper.screenshot.capture.compression.ImageCompressor
import dev.deftu.screencapper.screenshot.naming.NameTemplate
import dev.deftu.screencapper.screenshot.naming.PathSanitizer
import dev.deftu.screencapper.screenshot.naming.ScreenshotNames
import java.io.BufferedOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier

object FastCapture {
    internal val encoderThreads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 8)

    private val reserved: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val FORMATS = listOf("png", "jpg", "jpeg")

    private val coordinator =Executors.newSingleThreadExecutor(threads("Screencapper capture"))
    internal val encoderPool = Executors.newFixedThreadPool(encoderThreads, threads("Screencapper encoder"))

    @JvmStatic
    fun isActive(): Boolean = ScreencapperConfig.enabled && ScreencapperConfig.fastCapture

    @JvmStatic
    @JvmOverloads
    fun nextFile(gameDirectory: File, forceName: String?, kind: CaptureKind = CaptureKind.NORMAL, prefix: String? = null, extension: String? = null): File {
        val directory = File(gameDirectory, "screenshots").apply { mkdirs() }
        if (forceName != null) return File(directory, forceName)
        val name = prefix?.let(PathSanitizer::path) ?: ScreenshotNames.normal()
        return reserve(directory, name, extension ?: ScreenshotEncoding.extension(kind))
    }

    internal fun reserve(directory: File, name: String, extension: String): File {
        val counted = NameTemplate.COUNT in name
        var count = 1
        while (true) {
            val path = if (counted) name.replace(NameTemplate.COUNT, count.toString()) else name + (if (count == 1) "" else "_$count")
            val file = File(directory, "$path.$extension")
            if ((FORMATS + extension).none { File(directory, "$path.$it").exists() } && reserved.add(stem(file))) {
                file.parentFile?.mkdirs()
                return file
            }
            count++
        }
    }

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
        profile: CompressionProfile? = null,
        preview: Boolean = true,
    ) {
        coordinator.execute {
            val error = runCatching {
                val argb = try {
                    pixels.get()
                } finally {
                    release.run()
                }
                val image = CapturedImage(argb, width, height)
                if (preview) {
                    runCatching { ScreenshotPreviewHud.showCaptured(file, argb, width, height) }
                        .onFailure { Screencapper.LOGGER.warn("Couldn't show the screenshot preview", it) }
                }
                write(file, image, kind, metadata, profile)
            }.exceptionOrNull()
            release(file)
            if (error != null) Screencapper.LOGGER.error("Couldn't save screenshot {}", file.name, error)
            onDone.accept(error)
        }
    }

    internal fun release(file: File) {
        reserved.remove(stem(file))
    }

    internal fun claim(file: File): Boolean = reserved.add(stem(file))

    private fun stem(file: File): String {
        val stem = File(file.parentFile, file.nameWithoutExtension)
        return runCatching { stem.canonicalPath }.getOrDefault(stem.absolutePath)
    }

    @JvmStatic
    fun flipped(source: IntArray, sourceWidth: Int, width: Int, height: Int): IntArray {
        val out = IntArray(width * height)
        for (y in 0 until height) System.arraycopy(source, (height - 1 - y) * sourceWidth, out, y * width, width)
        return out
    }

    @JvmStatic
    fun saveImage(file: File, image: CapturedImage, kind: CaptureKind, metadata: Map<String, String>, onDone: Consumer<Throwable?>) =
        save(file, image.width, image.height, { image.argb }, {}, onDone, kind, metadata)

    private fun write(file: File, image: CapturedImage, kind: CaptureKind, metadata: Map<String, String>, override: CompressionProfile?) {
        fun writeAtomically(target: File, encode: (java.io.OutputStream) -> Unit) {
            val temp = File(target.parentFile, ".${target.name}.tmp")
            try {
                BufferedOutputStream(Files.newOutputStream(temp.toPath()), 1 shl 16).use(encode)
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                temp.delete()
            }
        }

        writeAtomically(file) { out ->
            if (override != null) ImageCompressor.encode(image, override, metadata, out) else ScreenshotEncoding.encode(image, kind, metadata, out)
        }

        val profile = override ?: dev.deftu.screencapper.screenshot.capture.compression.CompressionProfiles.forKind(kind)
        if (profile.keepOriginal && !profile.isLossless) {
            runCatching {
                val directory = File(file.parentFile, "original").apply { mkdirs() }
                writeAtomically(File(directory, "${file.nameWithoutExtension}.png")) { out ->
                    dev.deftu.screencapper.screenshot.capture.compression.ImageCompressor.encodeOriginal(image, metadata, out)
                }
            }.onFailure { Screencapper.LOGGER.warn("Couldn't keep the original of {}", file.name, it) }
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
