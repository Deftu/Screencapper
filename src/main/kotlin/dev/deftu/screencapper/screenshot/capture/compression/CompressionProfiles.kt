package dev.deftu.screencapper.screenshot.capture.compression

import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.CaptureKind

/** The compression chosen in the config for each use */
object CompressionProfiles {
    fun forKind(kind: CaptureKind): CompressionProfile {
        val fields = when (kind) {
            CaptureKind.NORMAL -> ScreencapperConfig.normalCompressionFields
            CaptureKind.HIGH_RES -> ScreencapperConfig.highResCompressionFields
        }
        return fields.profile() ?: CompressionProfile.LOSSLESS
    }

    /** The settings uploads are re-encoded with, or null to send the file as it was saved */
    fun forUpload(): CompressionProfile? = ScreencapperConfig.uploadCompressionFields.profile()
}
