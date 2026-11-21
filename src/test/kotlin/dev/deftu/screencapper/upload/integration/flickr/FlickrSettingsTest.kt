package dev.deftu.screencapper.upload.integration.flickr

import dev.deftu.screencapper.upload.SecretCipher
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlickrSettingsTest {
    private val sandbox = Files.createTempDirectory("screencapper-flickr").toFile()
    private val cipher = SecretCipher(File(sandbox, ".key"))
    private val account = FlickrAccount("72157626318069415-087bfc7b5816092c", "a202d1f853ec69de", "21207597@N07", "jamalfanaian")

    @AfterTest
    fun cleanup() {
        sandbox.deleteRecursively()
    }

    @Test
    fun `new settings default to public photos titled by name`() {
        val settings = FlickrSettings.initial()
        assertEquals(FlickrSettings.KIND, settings.kind)
        assertTrue(FlickrSettings.isPublic(settings))
        assertEquals("{name}", settings[FlickrSettings.TITLE])
        assertFalse(FlickrSettings.signedIn(settings))
    }

    @Test
    fun `signing in stores the account and changing the key signs out`() {
        val keyed = FlickrSettings.withKeys(FlickrSettings.initial(), "key-1", "secret-1")
        val settings = FlickrSettings.withAccount(keyed, account)
        assertTrue(FlickrSettings.signedIn(settings))
        assertEquals(OAuthCredentials("key-1", "secret-1", account.token, account.tokenSecret), FlickrSettings.credentials(settings))
        assertEquals("jamalfanaian", settings[FlickrSettings.USERNAME])
        assertEquals(settings, FlickrSettings.withKeys(settings, "key-1", "secret-1"))
        val changed = FlickrSettings.withKeys(settings, "key-2", "secret-1")
        assertFalse(FlickrSettings.signedIn(changed))
        assertNull(FlickrSettings.credentials(changed))
        assertEquals("", changed[FlickrSettings.USERNAME])
    }

    @Test
    fun `credentials are encrypted at rest while the account name stays readable`() {
        val settings = FlickrSettings.withAccount(FlickrSettings.withKeys(FlickrSettings.initial(), "key-123456", "secret-123456"), account)
        val profile = UploaderProfile(id = "flickr-1", name = "Flickr", integration = settings)
        val saved = UploaderProfile.encodeList(listOf(profile), cipher)
        listOf("key-123456", "secret-123456", account.token, account.tokenSecret).forEach { assertFalse(it in saved) }
        assertTrue("jamalfanaian" in saved)
        assertTrue("21207597@N07" in saved)
        assertEquals(listOf(profile), UploaderProfile.decodeList(saved, cipher))
    }

    @Test
    fun `empty settings have no credentials`() {
        assertNull(FlickrSettings.credentials(IntegrationSettings(FlickrSettings.KIND)))
    }
}
