package dev.deftu.screencapper.upload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResponseUrlFallbackTest {
    private val embedResponse = """{"id":"1","attachments":[],"embeds":[{"image":{"url":"https://cdn.example/shot.png"}}]}"""

    @Test
    fun anEmbedImageIsFoundWhenThereAreNoAttachments() {
        assertEquals("https://cdn.example/shot.png", ResponseUrl.extract(embedResponse, "attachments[0].url||embeds[0].image.url"))
    }

    @Test
    fun theFirstMatchingAlternativeWins() {
        val both = """{"attachments":[{"url":"https://cdn.example/a.png"}],"embeds":[{"image":{"url":"https://cdn.example/b.png"}}]}"""
        assertEquals("https://cdn.example/a.png", ResponseUrl.extract(both, "attachments[0].url||embeds[0].image.url"))
    }

    @Test
    fun noMatchingAlternativeStillFails() {
        assertFailsWith<UploadException> { ResponseUrl.extract("""{"id":"1"}""", "attachments[0].url||embeds[0].image.url") }
    }

    @Test
    fun oldDiscordProfilesUseTheFallback() {
        val old = UploaderPresets.discord("https://discord.com/api/webhooks/1/abc").copy(responseUrl = "attachments[0].url")
        assertEquals("https://cdn.example/shot.png", ResponseUrl.extract(embedResponse, UploaderPresets.responsePath(old)))
    }
}
