package dev.deftu.screencapper.upload

import com.google.gson.JsonParser
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploaderSecretsTest {
    private val sandbox = Files.createTempDirectory("screencapper-profile-secrets").toFile()
    private val keyFile = File(sandbox, ".key")
    private val cipher = SecretCipher(keyFile)
    private val webhook = "https://discord.com/api/webhooks/123456789/abcDEF-123_xyzTOKEN"
    private val immichKey = "immich-key-0123456789"

    @AfterTest
    fun cleanup() {
        sandbox.deleteRecursively()
    }

    private val profiles = listOf(
        UploaderPresets.discord(webhook),
        UploaderPresets.immich("https://photos.example", immichKey, ownerLink = false),
        UploaderProfile(
            id = "custom",
            name = "Custom",
            url = "https://user:pass1234@host.example/up?api_key=query-secret-1&name={name}",
            headers = listOf("Authorization" to "Bearer bearer-secret-1", "Accept" to "application/json"),
            formFields = listOf("userhash" to "hash-secret-1", "reqtype" to "fileupload"),
        ),
    )

    @Test
    fun `saved config holds no plain secrets and loads back the same`() {
        val saved = UploaderProfile.encodeList(profiles, cipher)
        for (secret in listOf("abcDEF-123_xyzTOKEN", immichKey, "user:pass1234", "query-secret-1", "bearer-secret-1", "hash-secret-1")) {
            assertFalse(secret in saved, secret)
        }
        assertTrue("enc:v1:" in saved)
        assertTrue("application/json" in saved)
        assertTrue("fileupload" in saved)
        assertTrue("https://discord.com/api/webhooks/123456789/" in saved)
        assertEquals(profiles, UploaderProfile.decodeList(saved, cipher))
    }

    @Test
    fun `plain configs from older versions load and are sealed on the next save`() {
        val old = UploaderProfile.encodeList(profiles)
        assertTrue(immichKey in old)
        val loaded = UploaderProfile.decodeList(old, cipher)
        assertEquals(profiles, loaded)
        assertTrue(UploaderProfile.decodeList(old).any { UploaderSecrets.hasPlainSecrets(it) })
        val resaved = UploaderProfile.encodeList(loaded, cipher)
        assertFalse(immichKey in resaved)
        assertFalse(UploaderProfile.decodeList(resaved).any { UploaderSecrets.hasPlainSecrets(it) })
    }

    @Test
    fun `sealing twice does not double encrypt`() {
        val once = profiles.map { UploaderSecrets.seal(it, cipher) }
        assertEquals(once, once.map { UploaderSecrets.seal(it, cipher) })
    }

    @Test
    fun `without the key secrets need re-entry but the profile is kept`() {
        val saved = UploaderProfile.encodeList(profiles, cipher)
        keyFile.delete()
        val loaded = UploaderProfile.decodeList(saved, SecretCipher(keyFile))
        assertEquals(profiles.map { it.id }, loaded.map { it.id })
        val immich = loaded.single { it.name == "Immich" }
        assertEquals("https://photos.example/api/assets", immich.url)
        assertEquals(listOf("Header x-api-key", "Deletion header x-api-key"), UploaderSecrets.lockedFields(immich))
        assertEquals(listOf("Upload URL", "Deletion URL"), UploaderSecrets.lockedFields(loaded.single { it.name == "Discord webhook" }))
        val resaved = JsonParser.parseString(UploaderProfile.encodeList(loaded, SecretCipher(keyFile))).asJsonArray
        assertEquals(JsonParser.parseString(saved).asJsonArray.map { it.asJsonObject.get("headers") }, resaved.map { it.asJsonObject.get("headers") })
    }

    @Test
    fun `profiles with nothing locked report no fields`() {
        assertTrue(profiles.all { UploaderSecrets.lockedFields(it).isEmpty() })
    }

    @Test
    fun `named secrets list deletion secrets first`() {
        val immich = profiles[1]
        assertEquals(
            listOf("deletion.header.x-api-key" to immichKey, "header.x-api-key" to immichKey),
            UploaderSecrets.named(immich),
        )
        val discord = profiles[0]
        assertEquals(
            listOf("deletion.url.webhook.123456789", "url.webhook.123456789"),
            UploaderSecrets.named(discord).map { it.first },
        )
        assertEquals(listOf("header.authorization" to "Client-ID ${UploaderProfile.IMGUR_CLIENT_ID}"), UploaderSecrets.named(UploaderProfile.IMGUR))
    }

    @Test
    fun `secret fields in a deletion body are sealed and load back`() {
        val profile = UploaderProfile(
            id = "body",
            name = "Body",
            url = "https://host.example/up",
            deletion = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.POST, "https://host.example/delete", body = """{"api_key":"body-secret-1","id":"{json:id}"}"""),
        )
        val saved = UploaderProfile.encodeList(listOf(profile), cipher)
        assertFalse("body-secret-1" in saved)
        assertTrue("{json:id}" in saved)
        assertEquals(listOf(profile), UploaderProfile.decodeList(saved, cipher))
    }
}
