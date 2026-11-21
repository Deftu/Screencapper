package dev.deftu.screencapper.screenshot

import kotlin.test.Test
import kotlin.test.assertEquals

class NewlyEarnedAdvancementsTest {
    private fun done(id: String, toasts: Boolean = true) = DoneAdvancement(id, toasts)

    @Test
    fun `an advancement that becomes done is newly earned`() {
        val earned = newlyEarnedAdvancements(emptySet(), listOf(done("minecraft:story/mine_stone")), reset = false, shown = true)
        assertEquals(listOf("minecraft:story/mine_stone"), earned)
    }

    @Test
    fun `an advancement that was already done is not earned again`() {
        val earned = newlyEarnedAdvancements(
            setOf("minecraft:story/root"),
            listOf(done("minecraft:story/root"), done("minecraft:story/mine_stone")),
            reset = false,
            shown = true,
        )
        assertEquals(listOf("minecraft:story/mine_stone"), earned)
    }

    @Test
    fun `an advancement without a toast is not earned`() {
        val earned = newlyEarnedAdvancements(emptySet(), listOf(done("minecraft:recipes/misc/stick", toasts = false)), reset = false, shown = true)
        assertEquals(emptyList(), earned)
    }

    @Test
    fun `a reset packet earns nothing`() {
        val earned = newlyEarnedAdvancements(emptySet(), listOf(done("minecraft:story/mine_stone")), reset = true, shown = true)
        assertEquals(emptyList(), earned)
    }

    @Test
    fun `an update the server does not show earns nothing`() {
        val earned = newlyEarnedAdvancements(emptySet(), listOf(done("minecraft:story/mine_stone")), reset = false, shown = false)
        assertEquals(emptyList(), earned)
    }

    @Test
    fun `several advancements done in one update are all earned`() {
        val earned = newlyEarnedAdvancements(
            emptySet(),
            listOf(done("minecraft:story/root"), done("minecraft:story/mine_stone")),
            reset = false,
            shown = true,
        )
        assertEquals(listOf("minecraft:story/root", "minecraft:story/mine_stone"), earned)
    }
}
