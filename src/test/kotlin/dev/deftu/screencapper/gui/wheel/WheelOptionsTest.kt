package dev.deftu.screencapper.gui.wheel

import dev.deftu.screencapper.screenshot.capture.CaptureKind
import dev.deftu.screencapper.screenshot.capture.compression.CompressionChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WheelOptionsTest {
    private val defaultSlots = listOf(
        WheelSlot("Clean", "eye-off", "Hides the HUD for this shot"),
        WheelSlot("No chat", "hud", "Hides chat for this shot"),
        WheelSlot("4K", "maximise", "3840 × 2160"),
        WheelSlot("Panorama", "sun", "Six 1024 × 1024 faces"),
        WheelSlot("Timer", "bell", "Takes it in 3 seconds"),
    )

    @Test
    fun emptyConfigGivesTheEverydayWheel() {
        val options = WheelOptions.decode("")
        assertEquals(defaultSlots, options.map { it.slot(2560, 1440) })
        assertEquals(
            listOf(WheelShot.Normal, WheelShot.Normal, WheelShot.HighRes(3840, 2160), WheelShot.Panorama(1024), WheelShot.Timer(3)),
            options.map { it.shot },
        )
        assertEquals(listOf(true, false, true, false, true), options.map { it.hideHud })
        assertEquals(listOf(false, true, false, false, false), options.map { it.hideChat })
        assertTrue(options.none { it.hideHand || it.compression != null })
    }

    @Test
    fun customHighResUsesTheConfiguredSize() {
        assertEquals(1920 to 1080, WheelShot.HighRes(0, 0).size(1920, 1080))
        assertEquals(3840 to 2160, WheelShot.HighRes(3840, 2160).size(1920, 1080))
    }

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val options = listOf(
            WheelOption("a", WheelShot.HighRes(5000, 3000), "Wide", "layers", hideHud = true, hideChat = false, hideHand = true, compression = CompressionChoice.TINY),
            WheelOption("b", WheelShot.Normal, "Clean", "eye", hideHud = false, hideChat = true, hideHand = true, compression = null),
            WheelOption("c", WheelShot.Timer(10), "Slow", "bell", hideHud = false, hideChat = true, hideHand = false, compression = CompressionChoice.BALANCED),
        )
        assertEquals(options, WheelOptions.decode(WheelOptions.encode(options)))
    }

    @Test
    fun defaultsSurviveARoundTrip() {
        val defaults = WheelOptions.defaults()
        assertEquals(defaults, WheelOptions.decode(WheelOptions.encode(defaults)))
    }

    @Test
    fun brokenConfigFallsBackToDefaults() {
        assertEquals(WheelOptions.defaults(), WheelOptions.decode("{not json"))
        assertEquals(WheelOptions.defaults(), WheelOptions.decode("[]"))
    }

    @Test
    fun unknownKindsAreSkipped() {
        val json = """[{"id":"x","kind":"hologram","label":"Holo"},{"id":"n","kind":"normal","label":"Plain"},{"id":"t","kind":"timer","label":"T","seconds":5}]"""
        val options = WheelOptions.decode(json)
        assertEquals(listOf("n", "t"), options.map { it.id })
    }

    @Test
    fun tooFewUsableOptionsFallBackToDefaults() {
        val json = """[{"id":"n","kind":"normal","label":"Plain"}]"""
        assertEquals(WheelOptions.defaults(), WheelOptions.decode(json))
    }

    @Test
    fun tooManyOptionsAreCutToTheWheelLimit() {
        val many = List(WheelOptions.MAX_OPTIONS + 3) { WheelOption("o$it", WheelShot.Normal, "Shot $it", "eye") }
        val options = WheelOptions.decode(WheelOptions.encode(many))
        assertEquals(WheelOptions.MAX_OPTIONS, options.size)
        assertEquals(many.take(WheelOptions.MAX_OPTIONS).map { it.id }, options.map { it.id })
    }

    @Test
    fun valuesAreClamped() {
        assertEquals(WheelShot.HighRes(16, 16384), WheelShot.HighRes(3, 99999).validated())
        assertEquals(WheelShot.HighRes(0, 0), WheelShot.HighRes(4000, -1).validated())
        assertEquals(WheelShot.Timer(1), WheelShot.Timer(0).validated())
        assertEquals(WheelShot.Timer(60), WheelShot.Timer(600).validated())

        val option = WheelOption("x", WheelShot.Timer(5), "   ", "not-an-icon").validated()
        assertEquals(WheelShotKind.TIMER.label, option.label)
        assertEquals(WheelShotKind.TIMER.icon, option.icon)
        assertEquals(WheelOptions.MAX_LABEL, WheelOption("y", WheelShot.Normal, "x".repeat(100), "eye").validated().label.length)
    }

    @Test
    fun onlySavingPresetsAreAllowedAsCompression() {
        assertNull(WheelOption("x", WheelShot.Normal, "A", "eye", compression = CompressionChoice.CUSTOM).validated().compression)
        assertNull(WheelOption("x", WheelShot.Normal, "A", "eye", compression = CompressionChoice.AS_SAVED).validated().compression)
        val json = """[{"id":"a","kind":"normal","label":"A","compression":"CUSTOM"},{"id":"b","kind":"normal","label":"B","compression":"SMALL"}]"""
        assertEquals(listOf(null, CompressionChoice.SMALL), WheelOptions.decode(json).map { it.compression })
    }

    @Test
    fun slotDetailsDescribeTheShot() {
        assertEquals("Takes it in 1 second", WheelOption("a", WheelShot.Timer(1), "T", "bell").slot(1, 1).detail)
        assertEquals("Hides chat for this shot", WheelOption("a", WheelShot.Normal, "C", "eye", hideChat = true).slot(1, 1).detail)
        assertEquals("Hides your hand for this shot", WheelOption("a", WheelShot.Normal, "H", "eye", hideHand = true).slot(1, 1).detail)
        assertEquals("Hides chat and your hand for this shot", WheelOption("a", WheelShot.Normal, "B", "eye", hideChat = true, hideHand = true).slot(1, 1).detail)
        assertEquals("Takes a normal screenshot", WheelOption("a", WheelShot.Normal, "N", "eye").slot(1, 1).detail)
    }

    @Test
    fun captureKindPicksTheCompressionDefault() {
        assertEquals(CaptureKind.HIGH_RES, WheelShot.HighRes(0, 0).captureKind)
        assertEquals(CaptureKind.NORMAL, WheelShot.Normal.captureKind)
        assertEquals(CaptureKind.NORMAL, WheelShot.Timer(3).captureKind)
    }

    @Test
    fun changingKindSwapsDefaultsButKeepsCustomText() {
        val plain = WheelOption("a", WheelShot.Normal, WheelShotKind.NORMAL.label, WheelShotKind.NORMAL.icon)
        val timer = plain.withKind(WheelShotKind.TIMER)
        assertEquals(WheelShot.Timer(3), timer.shot)
        assertEquals(WheelShotKind.TIMER.label, timer.label)
        assertEquals(WheelShotKind.TIMER.icon, timer.icon)

        val named = WheelOption("b", WheelShot.Normal, "Mine", "star").withKind(WheelShotKind.HIGH_RES)
        assertEquals("Mine", named.label)
        assertEquals("star", named.icon)
        assertEquals(WheelShot.HighRes(0, 0), named.shot)
    }

    @Test
    fun panoramaSurvivesARoundTripAndKeepsOnlyKnownSizes() {
        val options = listOf(
            WheelOption("p", WheelShot.Panorama(2048), "Sky", "sun"),
            WheelOption("n", WheelShot.Normal, "Plain", "eye"),
        )
        assertEquals(options, WheelOptions.decode(WheelOptions.encode(options)))
        assertEquals(WheelShot.Panorama(1024), WheelShot.Panorama(1500).validated())
        assertEquals(WheelShot.Panorama(4096), WheelShot.Panorama(4096).validated())
        val json = """[{"id":"p","kind":"panorama","label":"P"},{"id":"n","kind":"normal","label":"N"}]"""
        assertEquals(WheelShot.Panorama(1024), WheelOptions.decode(json)[0].shot)
    }

    @Test
    fun panoramaDescribesItsFaces() {
        assertEquals("Six 2048 × 2048 faces", WheelOption("p", WheelShot.Panorama(2048), "P", "sun").slot(1, 1).detail)
        val panorama = WheelOption("a", WheelShot.Normal, WheelShotKind.NORMAL.label, WheelShotKind.NORMAL.icon).withKind(WheelShotKind.PANORAMA)
        assertEquals(WheelShot.Panorama(1024), panorama.shot)
        assertEquals("Panorama", panorama.label)
        assertEquals(CaptureKind.HIGH_RES, WheelShot.Panorama(1024).captureKind)
    }

    @Test
    fun movingKeepsEverythingAndStopsAtTheEnds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), list.moved(0, 1))
        assertEquals(listOf("a", "c", "b"), list.moved(2, -1))
        assertEquals(list, list.moved(0, -1))
        assertEquals(list, list.moved(2, 1))
    }

    @Test
    fun addingAndRemovingRespectTheLimits() {
        val defaults = WheelOptions.defaults()
        assertTrue(WheelOptions.canAdd(defaults))
        assertTrue(WheelOptions.canRemove(defaults))
        assertTrue(!WheelOptions.canAdd(List(WheelOptions.MAX_OPTIONS) { defaults[0] }))
        assertTrue(!WheelOptions.canRemove(List(WheelOptions.MIN_OPTIONS) { defaults[0] }))
    }
}
