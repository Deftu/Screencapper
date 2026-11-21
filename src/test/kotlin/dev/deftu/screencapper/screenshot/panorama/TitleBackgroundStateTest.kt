package dev.deftu.screencapper.screenshot.panorama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TitleBackgroundStateTest {
    private val leave = "panoramas/auto/2026-11-20_14.03.22_My World"
    private val older = "panoramas/auto/2026-11-19_10.00.00_My World"

    @Test
    fun aChangedConfigValueIsAppliedOnce() {
        val state = TitleBackgroundState()
        assertEquals(TitleBackgroundState.Change(older, false), state.change(older))
        assertNull(state.change(older))
    }

    @Test
    fun goingBackToVanillaRestoresTheOverriddenTexture() {
        val state = TitleBackgroundState()
        state.change(older)
        assertEquals(TitleBackgroundState.Change("", true), state.change(""))
    }

    @Test
    fun aShownLeavePanoramaIsNotRevertedWhileItIsBeingSaved() {
        val state = TitleBackgroundState()
        state.change(older)
        state.show(leave)
        assertNull(state.change(older))
    }

    @Test
    fun aSavedLeavePanoramaIsNotReloadedFromItsFiles() {
        val state = TitleBackgroundState()
        state.change(older)
        state.show(leave)
        state.settle(leave, kept = true)
        assertNull(state.change(leave))
    }

    @Test
    fun aLeavePanoramaThatFailedToSaveGivesWayToTheConfiguredOne() {
        val state = TitleBackgroundState()
        state.change(older)
        state.show(leave)
        state.settle(leave, kept = false)
        assertEquals(TitleBackgroundState.Change(older, true), state.change(older))
    }

    @Test
    fun aLeavePanoramaThatFailedToSaveGivesWayToVanilla() {
        val state = TitleBackgroundState()
        state.show(leave)
        state.settle(leave, kept = false)
        assertEquals(TitleBackgroundState.Change("", true), state.change(""))
    }

    @Test
    fun aLeavePanoramaReloadedBeforeItsFilesExistIsLoadedAgainOnceSaved() {
        val state = TitleBackgroundState()
        state.show(leave)
        state.missed()
        state.settle(leave, kept = true)
        assertEquals(TitleBackgroundState.Change(leave, true), state.change(leave))
        assertNull(state.change(leave))
    }

    @Test
    fun aMissedLoadOutsideALeaveDoesNotRetryEveryTick() {
        val state = TitleBackgroundState()
        state.change(older)
        state.missed()
        assertNull(state.change(older))
    }

    @Test
    fun settlingAnEarlierLeaveKeepsTheNewerOneHeld() {
        val state = TitleBackgroundState()
        state.show(older)
        state.show(leave)
        state.settle(older, kept = false)
        assertNull(state.change(older))
        state.settle(leave, kept = true)
        assertNull(state.change(leave))
    }
}
