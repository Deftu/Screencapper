package dev.deftu.screencapper.screenshot.capture.compression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CompressionOverrideTest {
    private val base = CompressionProfile(format = ImageFormat.JPEG, jpegQuality = 70, keepOriginal = true)

    @Test
    fun noOverrideKeepsTheKindsProfile() {
        assertSame(base, CompressionProfiles.resolve(base, null))
    }

    @Test
    fun presetOverrideReplacesTheEncodingButKeepsTheOriginalSetting() {
        assertEquals(CompressionChoice.SMALL.preset!!.copy(keepOriginal = true), CompressionProfiles.resolve(base, CompressionChoice.SMALL))
        assertEquals(CompressionProfile.LOSSLESS.copy(keepOriginal = true), CompressionProfiles.resolve(base, CompressionChoice.LOSSLESS))
    }

    @Test
    fun choicesWithoutAPresetFallBackToTheKindsProfile() {
        assertSame(base, CompressionProfiles.resolve(base, CompressionChoice.CUSTOM))
        assertSame(base, CompressionProfiles.resolve(base, CompressionChoice.AS_SAVED))
    }
}
