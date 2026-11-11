package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfiles
import dev.deftu.screencapper.screenshot.capture.compression.ImageCompressor
import java.io.OutputStream

object ScreenshotEncoding {
    fun extension(kind: CaptureKind): String = CompressionProfiles.forKind(kind).format.extension

    fun encode(image: CapturedImage, kind: CaptureKind, text: Map<String, String>, out: OutputStream) {
        ImageCompressor.encode(image, CompressionProfiles.forKind(kind), text, out)
    }
}
