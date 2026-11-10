package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfiles
import dev.deftu.screencapper.screenshot.capture.compression.ImageCompressor
import java.io.OutputStream

/**
 * Turns captured pixels into the bytes saved on disk, following the compression profile for each [CaptureKind].
 *
 * Contract between the capture pipeline and the compression profiles: [extension] decides the file name before anything
 * is encoded, and [encode] must write exactly that format.
 */
object ScreenshotEncoding {
    /** The file extension, without a dot, for screenshots of [kind] */
    fun extension(kind: CaptureKind): String = CompressionProfiles.forKind(kind).format.extension

    /** Writes [image] to [out]; [text] goes into the file as metadata where the format allows it */
    fun encode(image: CapturedImage, kind: CaptureKind, text: Map<String, String>, out: OutputStream) {
        ImageCompressor.encode(image, CompressionProfiles.forKind(kind), text, out)
    }
}
