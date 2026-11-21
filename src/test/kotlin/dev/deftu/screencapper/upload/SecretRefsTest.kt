package dev.deftu.screencapper.upload

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SecretRefsTest {
    private val sandbox = Files.createTempDirectory("screencapper-refs").toFile()
    private val keyFile = File(sandbox, ".key")
    private val cipher = SecretCipher(keyFile)
    private val values = TemplateValues("shot.png", Instant.EPOCH, Instant.EPOCH, Instant.ofEpochMilli(42), "uuid-1")
    private val token = "abcDEF-123_xyzTOKEN"
    private val webhook = "https://discord.com/api/webhooks/123456789/$token"
    private val immichKey = "immich-key-0123456789"

    private val discord = UploaderPresets.discord(webhook).copy(id = "discord-1")
    private val immich = UploaderPresets.immich("https://photos.example", immichKey, ownerLink = false).copy(id = "immich-1")

    @AfterTest
    fun cleanup() {
        sandbox.deleteRecursively()
    }

    private fun rendered(profile: UploaderProfile, response: String) = profile.deletion.render(values, response)!!

    @Test
    fun `discord deletion stores a reference instead of the webhook token`() {
        val stored = SecretRefs.toReferences(rendered(discord, """{"id":"777"}"""), discord)
        assertEquals("https://discord.com/api/webhooks/123456789/{secret:discord-1:deletion.url.webhook.123456789}/messages/777", stored.url)
        assertFalse(token in stored.toJson().toString())
    }

    @Test
    fun `immich deletion stores a reference instead of the api key`() {
        val stored = SecretRefs.toReferences(rendered(immich, """{"id":"asset-1"}"""), immich)
        assertEquals(listOf("x-api-key" to "{secret:immich-1:deletion.header.x-api-key}", "Content-Type" to "application/json"), stored.headers)
        assertEquals("""{"ids":["asset-1"]}""", stored.body)
    }

    @Test
    fun `profile secrets inside the body or other headers become references`() {
        val profile = UploaderProfile(id = "p", headers = listOf("Authorization" to "Bearer long-secret-value"))
        val request = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.POST, "https://h.example/del", listOf("X-Note" to "x Bearer long-secret-value"), """{"auth":"Bearer long-secret-value"}""")
        val stored = SecretRefs.toReferences(request, profile)
        assertEquals("x {secret:p:header.authorization}", stored.headers.single().second)
        assertEquals("""{"auth":"{secret:p:header.authorization}"}""", stored.body)
    }

    @Test
    fun `short secrets are matched only in secret slots`() {
        val profile = UploaderProfile(id = "p", headers = listOf("x-api-key" to "k"), deletion = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "https://h.example/k", listOf("x-api-key" to "k")))
        val stored = SecretRefs.toReferences(profile.deletion, profile)
        assertEquals("https://h.example/k", stored.url)
        assertEquals("{secret:p:deletion.header.x-api-key}", stored.headers.single().second)
    }

    @Test
    fun `references resolve from the current profile`() {
        val stored = SecretRefs.toReferences(rendered(immich, """{"id":"asset-1"}"""), immich)
        val resolved = assertIs<SecretRefs.Resolution.Ready>(SecretRefs.resolve(stored, listOf(discord, immich))).request
        assertEquals(rendered(immich, """{"id":"asset-1"}"""), resolved)

        val rotated = UploaderPresets.immich("https://photos.example", "new-key-0123456789", ownerLink = false).copy(id = "immich-1")
        val again = assertIs<SecretRefs.Resolution.Ready>(SecretRefs.resolve(stored, listOf(rotated))).request
        assertEquals("new-key-0123456789", again.headers.first().second)
    }

    @Test
    fun `a removed profile or secret does not resolve`() {
        val stored = SecretRefs.toReferences(rendered(discord, """{"id":"777"}"""), discord)
        assertIs<SecretRefs.Resolution.Missing>(SecretRefs.resolve(stored, listOf(immich)))
        val otherWebhook = UploaderPresets.discord("https://discord.com/api/webhooks/42/otherTOKEN").copy(id = "discord-1")
        assertIs<SecretRefs.Resolution.Missing>(SecretRefs.resolve(stored, listOf(otherWebhook)))
    }

    @Test
    fun `a locked profile secret does not resolve`() {
        val stored = SecretRefs.toReferences(rendered(immich, """{"id":"asset-1"}"""), immich)
        val locked = immich.copy(deletion = immich.deletion.copy(headers = listOf("x-api-key" to "enc:v1:AAAA")))
        val result = assertIs<SecretRefs.Resolution.Locked>(SecretRefs.resolve(stored, listOf(locked)))
        assertTrue(result.inProfile)
    }

    @Test
    fun `a locked literal in the record does not resolve`() {
        val request = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "https://h.example/a", listOf("Authorization" to "enc:v1:AAAA"))
        assertFalse(assertIs<SecretRefs.Resolution.Locked>(SecretRefs.resolve(request, emptyList())).inProfile)
    }

    @Test
    fun `records without secrets resolve unchanged`() {
        val request = DeletionRequest(DeletionKind.BROWSER, DeletionMethod.GET, "https://h.example/delete/abc")
        assertEquals(request, assertIs<SecretRefs.Resolution.Ready>(SecretRefs.resolve(request, emptyList())).request)
    }

    @Test
    fun `old records with literal secrets migrate to references`() {
        val old = rendered(discord, """{"id":"777"}""")
        val loaded = SecretRefs.load(old, discord, cipher)
        assertTrue(loaded.changed)
        assertEquals(SecretRefs.toReferences(old, discord), loaded.request)
    }

    @Test
    fun `old imgur records migrate to the built in client id reference`() {
        val old = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "https://api.imgur.com/3/image/hash", listOf("Authorization" to "Client-ID ${UploaderProfile.IMGUR_CLIENT_ID}"))
        val loaded = SecretRefs.load(old, UploaderProfile.IMGUR, cipher)
        assertEquals("{secret:imgur:header.authorization}", loaded.request.headers.single().second)
        assertEquals(old, assertIs<SecretRefs.Resolution.Ready>(SecretRefs.resolve(loaded.request, UploaderProfile.BUILT_IN)).request)
    }

    @Test
    fun `unmatched literal secrets stay but are sealed when stored`() {
        val old = rendered(immich, """{"id":"asset-1"}""")
        val loaded = SecretRefs.load(old, null, cipher)
        assertEquals(old, loaded.request)
        assertTrue(loaded.changed)
        val sealed = SecretRefs.seal(loaded.request, cipher)
        assertFalse(immichKey in sealed.toJson().toString())
        assertEquals(sealed.target, SecretRefs.seal(loaded.request, cipher).target)
        val reloaded = SecretRefs.load(sealed, null, cipher)
        assertFalse(reloaded.changed)
        assertEquals(old, reloaded.request)
    }

    @Test
    fun `sealed literals stay locked when the key is gone`() {
        val sealed = SecretRefs.seal(rendered(immich, """{"id":"asset-1"}"""), cipher)
        keyFile.delete()
        val reloaded = SecretRefs.load(sealed, null, SecretCipher(keyFile))
        assertIs<SecretRefs.Resolution.Locked>(SecretRefs.resolve(reloaded.request, emptyList()))
    }

    @Test
    fun `stored references are not sealed and need no migration`() {
        val stored = SecretRefs.toReferences(rendered(discord, """{"id":"777"}"""), discord)
        assertEquals(stored, SecretRefs.seal(stored, cipher))
        assertFalse(SecretRefs.load(stored, discord, cipher).changed)
    }

    @Test
    fun `shared asset matching uses the reference form`() {
        val first = SecretRefs.load(rendered(immich, """{"id":"asset-1"}"""), immich, cipher).request
        val second = SecretRefs.toReferences(rendered(immich, """{"id":"asset-1"}"""), immich)
        assertEquals(first.target, second.target)
        val a = SecretRefs.toReferences(rendered(discord, """{"id":"1"}"""), discord)
        val b = SecretRefs.load(rendered(discord, """{"id":"1"}"""), discord, cipher).request
        assertEquals(a.target, b.target)
    }
}
