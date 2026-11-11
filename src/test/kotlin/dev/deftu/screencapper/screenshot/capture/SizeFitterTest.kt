package dev.deftu.screencapper.screenshot.capture

import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfile
import dev.deftu.screencapper.screenshot.capture.compression.FitResult
import dev.deftu.screencapper.screenshot.capture.compression.ImageFormat
import dev.deftu.screencapper.screenshot.capture.compression.SizeFitter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SizeFitterTest {
    private fun fakeSize(profile: CompressionProfile, scale: Double): Int {
        val quality = if (profile.format == ImageFormat.JPEG) profile.jpegQuality / 100.0 else 1.5
        return (10_000_000 * quality * scale * scale).toInt()
    }

    @Test
    fun `no limit encodes once with the profile as is`() {
        var calls = 0
        val result = SizeFitter.fit(null, CompressionProfile.UPLOAD) { p, s -> calls++; ByteArray(fakeSize(p, s)) }
        val fits = assertIs<FitResult.Fits>(result)
        assertEquals(1, calls)
        assertEquals(1.0, fits.scale)
        assertEquals(CompressionProfile.UPLOAD.jpegQuality, fits.profile.jpegQuality)
    }

    @Test
    fun `steps a JPEG down until it is under the limit`() {
        val limit = 3_000_000L
        val attempts = mutableListOf<Int>()
        val result = SizeFitter.fit(limit, CompressionProfile.UPLOAD) { p, s -> ByteArray(fakeSize(p, s)).also { attempts += it.size } }
        val fits = assertIs<FitResult.Fits>(result)
        assertTrue(fits.bytes.size <= limit)
        assertTrue(attempts.size > 1)
        assertTrue(attempts.dropLast(1).all { it > limit }, "stopped late: $attempts")
        assertTrue(attempts.zipWithNext().all { (a, b) -> b < a }, "didn't shrink each step: $attempts")
    }

    @Test
    fun `steps a PNG down by scale`() {
        val limit = 5_000_000L
        val result = SizeFitter.fit(limit, CompressionProfile.LOSSLESS) { p, s -> ByteArray(fakeSize(p, s)) }
        val fits = assertIs<FitResult.Fits>(result)
        assertTrue(fits.bytes.size <= limit)
        assertTrue(fits.scale < 1.0)
    }

    @Test
    fun `reports when even the smallest attempt is too large`() {
        val result = SizeFitter.fit(1_000L, CompressionProfile.UPLOAD) { p, s -> ByteArray(fakeSize(p, s)) }
        val tooLarge = assertIs<FitResult.TooLarge>(result)
        assertTrue(tooLarge.smallestSize > 1_000L)
    }
}
