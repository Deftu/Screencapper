package dev.deftu.screencapper.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class TextFitTest {
    private val mono: (String) -> Float = { it.length * 10f }

    @Test
    fun `words wrap to the width`() {
        assertEquals(listOf("one two", "three"), TextFit.wrap("one two three", 75f, mono))
    }

    @Test
    fun `line breaks are kept and extra spaces collapse`() {
        assertEquals(listOf("a b", "c"), TextFit.wrap("a   b\nc", 100f, mono))
    }

    @Test
    fun `a word too long for the box is split`() {
        assertEquals(listOf("abcd", "efgh", "ij"), TextFit.wrap("abcdefghij", 40f, mono))
    }

    @Test
    fun `blank text has no lines`() {
        assertEquals(emptyList(), TextFit.wrap("   ", 100f, mono))
    }
}
