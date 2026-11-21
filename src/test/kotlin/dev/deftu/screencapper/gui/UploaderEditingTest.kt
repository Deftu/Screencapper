package dev.deftu.screencapper.gui

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.DiscordOptions
import dev.deftu.screencapper.upload.UploaderPresets
import dev.deftu.screencapper.upload.UploaderProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UploaderEditingTest {
    private val webhook = "https://discord.com/api/webhooks/123456789/abcDEF-123_xyz"

    @Test
    fun `token goes in at the cursor and the cursor moves past it`() {
        assertEquals("ab{world}cd" to 9, TemplateInsert.insert("abcd", 2, 2, "{world}"))
    }

    @Test
    fun `token replaces the selected text`() {
        assertEquals("a{player}d" to 9, TemplateInsert.insert("abcd", 1, 3, "{player}"))
    }

    @Test
    fun `reversed selection is treated like a normal one`() {
        assertEquals("a{player}d" to 9, TemplateInsert.insert("abcd", 3, 1, "{player}"))
    }

    @Test
    fun `out of range cursor appends the token`() {
        assertEquals("abc{date}" to 9, TemplateInsert.insert("abc", 10, 10, "{date}"))
    }

    @Test
    fun `token that would pass the length limit is refused`() {
        assertNull(TemplateInsert.insert("abcd", 4, 4, "{world}", maxLength = 10))
        assertEquals("abc{date}" to 9, TemplateInsert.insert("abc", 3, 3, "{date}", maxLength = 9))
    }

    @Test
    fun `host shows only the domain and never the path or query`() {
        val profile = UploaderProfile(url = "https://user:secret@files.example.com/upload?key=abc123")
        assertEquals("files.example.com", UploaderDisplay.host(profile))
    }

    @Test
    fun `host never falls back to the raw address`() {
        assertEquals("No address set", UploaderDisplay.host(UploaderProfile(url = "")))
        assertEquals("Invalid address", UploaderDisplay.host(UploaderProfile(url = "not a url?token=secret")))
        assertEquals("Invalid address", UploaderDisplay.host(UploaderProfile(url = "token=secret")))
    }

    @Test
    fun `built in hosts are named`() {
        assertEquals("imgur.com", UploaderDisplay.host(UploaderProfile.IMGUR))
        assertEquals("catbox.moe", UploaderDisplay.host(UploaderProfile.CATBOX))
    }

    @Test
    fun `kind names the preset an uploader came from`() {
        assertEquals("Built in", UploaderDisplay.kind(UploaderProfile.IMGUR))
        assertEquals("Discord", UploaderDisplay.kind(UploaderPresets.discord(webhook)))
        assertEquals("Immich", UploaderDisplay.kind(UploaderPresets.immich("https://photos.example.com", "key", false)))
        assertEquals("Custom", UploaderDisplay.kind(UploaderProfile(url = "https://example.com/upload")))
    }

    @Test
    fun `immich key is read from the upload headers`() {
        val profile = UploaderPresets.immich("https://photos.example.com", "first", false)
        assertTrue(ImmichKey.isImmich(profile))
        assertEquals("first", ImmichKey.read(profile))
        assertFalse(ImmichKey.isImmich(UploaderProfile(url = "https://example.com/api/assets")))
    }

    @Test
    fun `changing the immich key matches a fresh preset`() {
        val old = UploaderPresets.immich("https://photos.example.com", "first", true)
        val fresh = UploaderPresets.immich("https://photos.example.com", "second", true)
        val changed = ImmichKey.write(old, " second ")
        assertEquals(fresh.copy(id = old.id), changed)
    }

    @Test
    fun `duplicate gets a new id and a copy name`() {
        val original = UploaderPresets.discord(webhook).copy(name = "Shots")
        val copy = UploaderEdits.duplicate(original)
        assertNotEquals(original.id, copy.id)
        assertEquals("Shots copy", copy.name)
        assertEquals(original.copy(id = copy.id, name = copy.name), copy)
    }

    @Test
    fun `saving with unchanged discord options keeps hand edits`() {
        val profile = UploaderPresets.discord(webhook)
        val initial = UploaderEdits.discordOptions(profile)
        val draft = profile.copy(deletion = DeletionRequest(DeletionKind.BROWSER, DeletionMethod.DELETE, "https://example.com/{json:id}"))
        assertEquals(draft, UploaderEdits.saved(draft, initial, initial))
    }

    @Test
    fun `saving with changed discord options rebuilds the message`() {
        val profile = UploaderPresets.discord(webhook)
        val initial = UploaderEdits.discordOptions(profile)
        val changed = initial.copy(content = "{player} in {world}", silent = true)
        val draft = profile.copy(name = "Renamed", keepMetadata = true)
        assertEquals(UploaderPresets.withDiscord(draft, changed), UploaderEdits.saved(draft, initial, changed))
    }

    @Test
    fun `discord options carry the thread from the webhook link`() {
        val profile = UploaderPresets.discord("$webhook?thread_id=42")
        assertEquals("42", UploaderEdits.discordOptions(profile).threadId)
        assertEquals(DiscordOptions(threadId = "42"), UploaderEdits.discordOptions(profile.copy(discord = null)))
    }

    @Test
    fun `non discord uploaders save the draft as is`() {
        val draft = UploaderProfile(name = "Mine", url = "https://example.com/upload")
        assertEquals(draft, UploaderEdits.saved(draft, null, null))
    }
}
