package dev.deftu.screencapper.screenshot.capture.compression

import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.capture.CaptureKind

object CompressionProfiles {
    fun forKind(kind: CaptureKind): CompressionProfile {
        val fields = when (kind) {
            CaptureKind.NORMAL -> ScreencapperConfig.normalCompressionFields
            CaptureKind.HIGH_RES -> ScreencapperConfig.highResCompressionFields
        }
        return fields.profile() ?: CompressionProfile.LOSSLESS
    }

    fun forUpload(): CompressionProfile? = ScreencapperConfig.uploadCompressionFields.profile()
}
