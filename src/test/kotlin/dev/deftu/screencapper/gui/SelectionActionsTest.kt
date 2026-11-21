package dev.deftu.screencapper.gui

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionActionsTest {
    @Test
    fun `one selected screenshot offers copy upload and delete`() {
        assertEquals(listOf(SelectionAction.COPY, SelectionAction.UPLOAD, SelectionAction.DELETE), selectionActions(1))
    }

    @Test
    fun `several selected screenshots still offer copy`() {
        assertEquals(listOf(SelectionAction.COPY, SelectionAction.UPLOAD, SelectionAction.DELETE), selectionActions(2))
        assertEquals(listOf(SelectionAction.COPY, SelectionAction.UPLOAD, SelectionAction.DELETE), selectionActions(12))
    }

    @Test
    fun `nothing selected offers nothing`() {
        assertEquals(emptyList(), selectionActions(0))
    }
}
