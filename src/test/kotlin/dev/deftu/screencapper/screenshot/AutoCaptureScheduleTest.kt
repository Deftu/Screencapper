package dev.deftu.screencapper.screenshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoCaptureScheduleTest {
    private val world = Any()
    private val schedule = AutoCaptureSchedule(delayTicks = 20, windowTicks = 80, cooldownTicks = 100)

    private fun shotsBetween(from: Long, to: Long, blocked: (Long) -> Boolean = { false }): List<Long> =
        (from..to).filter { schedule.shouldShoot(it, blocked(it)) }

    @Test
    fun `an earned advancement is shot once after the delay`() {
        schedule.earned("minecraft:story/mine_stone", world, 0)
        assertEquals(listOf(20L), shotsBetween(0, 200))
    }

    @Test
    fun `the same advancement in the same world is shot only once`() {
        schedule.earned("minecraft:story/mine_stone", world, 0)
        assertEquals(listOf(20L), shotsBetween(0, 300))
        schedule.earned("minecraft:story/mine_stone", world, 300)
        assertEquals(emptyList(), shotsBetween(300, 600))
    }

    @Test
    fun `the same advancement in a new world is shot again`() {
        schedule.earned("minecraft:story/mine_stone", world, 0)
        assertEquals(listOf(20L), shotsBetween(0, 300))
        schedule.earned("minecraft:story/mine_stone", Any(), 300)
        assertEquals(listOf(320L), shotsBetween(300, 600))
    }

    @Test
    fun `several advancements arriving together give one shot`() {
        schedule.earned("minecraft:story/root", world, 0)
        schedule.earned("minecraft:story/mine_stone", world, 2)
        schedule.earned("minecraft:story/upgrade_tools", world, 10)
        assertEquals(listOf(20L), shotsBetween(0, 300))
    }

    @Test
    fun `an advancement soon after a shot waits for the cooldown while its toast is still up`() {
        schedule.earned("minecraft:story/root", world, 0)
        assertEquals(listOf(20L), shotsBetween(0, 50))
        schedule.earned("minecraft:story/mine_stone", world, 60)
        assertEquals(listOf(120L), shotsBetween(51, 400))
    }

    @Test
    fun `an advancement whose toast is gone before the cooldown ends is not shot`() {
        schedule.earned("minecraft:story/root", world, 0)
        assertEquals(listOf(20L), shotsBetween(0, 30))
        schedule.earned("minecraft:story/mine_stone", world, 30)
        assertEquals(emptyList(), shotsBetween(31, 400))
    }

    @Test
    fun `a blocked shot waits until it is unblocked`() {
        schedule.earned("minecraft:story/root", world, 0)
        assertEquals(listOf(50L), shotsBetween(0, 200) { it < 50 })
    }

    @Test
    fun `a shot blocked until its toast is gone is dropped`() {
        schedule.earned("minecraft:story/root", world, 0)
        assertEquals(emptyList(), shotsBetween(0, 200) { it < 100 })
    }

    @Test
    fun `leaving the world cancels a pending shot`() {
        schedule.earned("minecraft:story/root", world, 0)
        schedule.earned("minecraft:story/mine_stone", Any(), 5)
        assertEquals(listOf(25L), shotsBetween(0, 200))
    }

    @Test
    fun `nothing is pending before anything is earned`() {
        assertFalse(schedule.pending)
        schedule.earned("minecraft:story/root", world, 0)
        assertTrue(schedule.pending)
    }
}
