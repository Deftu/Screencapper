package dev.deftu.screencapper.upload.integration.googlephotos

import dev.deftu.screencapper.upload.SecretCipher
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GooglePhotosSettingsTest {
    private val tokens = GoogleTokens("ya29.access", 5_000_000L, "1//refresh", GoogleOAuth.SCOPE)
    private val form = GooglePhotosForm("id.apps", "GOCSPX-secret", useAlbum = true, copyLink = false)
    private val sandbox = Files.createTempDirectory("screencapper-google").toFile()

    @AfterTest
    fun cleanup() {
        sandbox.deleteRecursively()
    }

    private fun signedIn(): IntegrationSettings = GooglePhotosSettings.saved(null, form, SignedIn("id.apps", tokens)).with("albumId" to "album-1")

    @Test
    fun `sign in stores the tokens and options`() {
        val config = GooglePhotosSettings(GooglePhotosSettings.saved(null, form, SignedIn("id.apps", tokens)))
        assertEquals("google-photos", config.settings.kind)
        assertEquals("id.apps", config.clientId)
        assertEquals("GOCSPX-secret", config.clientSecret)
        assertEquals("1//refresh", config.refreshToken)
        assertTrue(config.isSignedIn)
        assertTrue(config.useAlbum)
        assertFalse(config.copyLink)
        assertTrue(config.hasFreshAccess(now = 1_000L))
        assertFalse(config.hasFreshAccess(now = 4_990_000L))
    }

    @Test
    fun `sign in with another client id than the form is ignored`() {
        val config = GooglePhotosSettings(GooglePhotosSettings.saved(null, form, SignedIn("other.apps", tokens)))
        assertFalse(config.isSignedIn)
    }

    @Test
    fun `saving keeps tokens and album refreshed since the dialog opened`() {
        val config = GooglePhotosSettings(GooglePhotosSettings.saved(signedIn(), form.copy(copyLink = true), null))
        assertEquals("1//refresh", config.refreshToken)
        assertEquals("album-1", config.albumId)
        assertTrue(config.copyLink)
    }

    @Test
    fun `signing in again keeps the album`() {
        val again = tokens.copy(refreshToken = "1//second")
        val config = GooglePhotosSettings(GooglePhotosSettings.saved(signedIn(), form, SignedIn("id.apps", again)))
        assertEquals("1//second", config.refreshToken)
        assertEquals("album-1", config.albumId)
    }

    @Test
    fun `changing the client id signs out and forgets the album`() {
        val config = GooglePhotosSettings(GooglePhotosSettings.saved(signedIn(), form.copy(clientId = " new.apps "), null))
        assertEquals("new.apps", config.clientId)
        assertFalse(config.isSignedIn)
        assertEquals("", config.accessToken)
        assertEquals("", config.albumId)
    }

    @Test
    fun `turning the album off forgets it`() {
        val config = GooglePhotosSettings(GooglePhotosSettings.saved(signedIn(), form.copy(useAlbum = false), null))
        assertFalse(config.useAlbum)
        assertEquals("", config.albumId)
    }

    @Test
    fun `signing out drops every token but keeps the client`() {
        val config = GooglePhotosSettings(GooglePhotosSettings.signedOut(signedIn()))
        assertFalse(config.isSignedIn)
        assertEquals("", config.accessToken)
        assertEquals("id.apps", config.clientId)
        assertEquals("album-1", config.albumId)
    }

    @Test
    fun `refreshed tokens replace the access token`() {
        val refreshed = GooglePhotosSettings(GooglePhotosSettings.withTokens(signedIn(), GoogleTokens("ya29.new", 9L, "1//refresh", "")))
        assertEquals("ya29.new", refreshed.accessToken)
        assertEquals("9", refreshed.settings["expiresAt"])
    }

    @Test
    fun `credentials are sealed in the saved config and the rest stays readable`() {
        val profile = UploaderProfile(id = "gp", name = "Google Photos", integration = signedIn())
        val cipher = SecretCipher(File(sandbox, ".key"))
        val saved = UploaderProfile.encodeList(listOf(profile), cipher)
        for (secret in listOf("GOCSPX-secret", "1//refresh", "ya29.access")) assertFalse(secret in saved)
        for (plain in listOf("id.apps", "album-1", "5000000")) assertTrue(plain in saved)
        assertEquals(listOf(profile), UploaderProfile.decodeList(saved, cipher))
    }
}
