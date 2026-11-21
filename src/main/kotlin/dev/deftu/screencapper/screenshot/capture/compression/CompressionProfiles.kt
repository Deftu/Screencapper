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

    fun forKind(kind: CaptureKind, override: CompressionChoice?): CompressionProfile = resolve(forKind(kind), override)

    fun resolve(base: CompressionProfile, override: CompressionChoice?): CompressionProfile =
        override?.preset?.copy(keepOriginal = base.keepOriginal) ?: base

    fun forUpload(): CompressionProfile? = ScreencapperConfig.uploadCompressionFields.profile()
}
