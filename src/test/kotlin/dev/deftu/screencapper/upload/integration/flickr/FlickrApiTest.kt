package dev.deftu.screencapper.upload.integration.flickr

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.upload.SecretFields
import dev.deftu.screencapper.upload.SecretRefs
import dev.deftu.screencapper.upload.TemplateValues
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import dev.deftu.screencapper.upload.integration.LoopbackReceiver
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FlickrApiTest {
    private val values = TemplateValues("2026-11-20_12.00.00.png", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, "uuid-1")

    private val signedIn = IntegrationSettings(
        FlickrSettings.KIND,
        mapOf(
            FlickrSettings.API_KEY to "653e7a6ecc1d528c516cc8f92cf98611",
            FlickrSettings.API_SECRET to "consumer-secret-1",
            FlickrSettings.OAUTH_TOKEN to "72157626318069415-087bfc7b5816092c",
            FlickrSettings.TOKEN_SECRET to "a202d1f853ec69de",
            FlickrSettings.USER_NSID to "21207597@N07",
            FlickrSettings.USERNAME to "jamalfanaian",
        ),
    )

    @Test
    fun `request token url carries the callback and a signature`() {
        val url = FlickrApi.requestTokenUrl("ck", "cs", "http://127.0.0.1:5000/callback", "n1", "100")
        assertTrue(url.startsWith("https://www.flickr.com/services/oauth/request_token?"))
        val query = LoopbackReceiver.parseQuery(URI(url).rawQuery)
        assertEquals("http://127.0.0.1:5000/callback", query["oauth_callback"])
        val unsigned = query.filterKeys { it != "oauth_signature" }.toList()
        assertEquals(OAuth1.sign(OAuth1.baseString("GET", FlickrApi.REQUEST_TOKEN_URL, unsigned), "cs", ""), query["oauth_signature"])
    }

    @Test
    fun `authorize url asks for delete permission`() {
        assertEquals("https://www.flickr.com/services/oauth/authorize?oauth_token=72157626737672178-022bbd2f4c2f3432&perms=delete", FlickrApi.authorizeUrl("72157626737672178-022bbd2f4c2f3432"))
    }

    @Test
    fun `access token url signs with the request token secret and verifier`() {
        val requestToken = RequestToken("72157626737672178-022bbd2f4c2f3432", "fccb68c4e6103197")
        val url = FlickrApi.accessTokenUrl("ck", "cs", requestToken, "5d1b96a26b494074", "n2", "200")
        val query = LoopbackReceiver.parseQuery(URI(url).rawQuery)
        assertEquals("5d1b96a26b494074", query["oauth_verifier"])
        assertEquals(requestToken.token, query["oauth_token"])
        val unsigned = query.filterKeys { it != "oauth_signature" }.toList()
        assertEquals(OAuth1.sign(OAuth1.baseString("GET", FlickrApi.ACCESS_TOKEN_URL, unsigned), "cs", "fccb68c4e6103197"), query["oauth_signature"])
    }

    @Test
    fun `request and access token replies from the docs parse`() {
        val requestToken = FlickrApi.parseRequestToken("oauth_callback_confirmed=true&oauth_token=72157626737672178-022bbd2f4c2f3432&oauth_token_secret=fccb68c4e6103197")
        assertEquals(RequestToken("72157626737672178-022bbd2f4c2f3432", "fccb68c4e6103197"), requestToken)
        val account = FlickrApi.parseAccessToken(
            "fullname=Jamal%20Fanaian&oauth_token=72157626318069415-087bfc7b5816092c&oauth_token_secret=a202d1f853ec69de&user_nsid=21207597%40N07&username=jamalfanaian",
        )
        assertEquals(FlickrAccount("72157626318069415-087bfc7b5816092c", "a202d1f853ec69de", "21207597@N07", "jamalfanaian"), account)
        assertFailsWith<UploadException> { FlickrApi.parseRequestToken("oauth_problem=signature_invalid") }
    }

    @Test
    fun `oauth problems name the problem without echoing debug data`() {
        val message = FlickrApi.oauthProblem(401, "oauth_problem=signature_invalid&debug_sbs=GET&https%3A%2F%2Fwww.flickr.com")
        assertTrue("signature_invalid" in message)
        assertFalse("debug_sbs" in message)
        assertTrue("500" in FlickrApi.oauthProblem(500, "<html>"))
    }

    @Test
    fun `upload fields follow visibility title and tags`() {
        val public = FlickrApi.uploadFields(signedIn.with(FlickrSettings.TITLE to "{name}", FlickrSettings.TAGS to "minecraft screencapper"), values)
        assertEquals(
            listOf("title" to "2026-11-20_12.00.00", "tags" to "minecraft screencapper", "is_public" to "1", "is_friend" to "0", "is_family" to "0"),
            public,
        )
        val private = FlickrApi.uploadFields(signedIn.with(FlickrSettings.VISIBILITY to FlickrSettings.PRIVATE, FlickrSettings.FAMILY to "true"), values)
        assertEquals(listOf("is_public" to "0", "is_friend" to "0", "is_family" to "1"), private)
    }

    @Test
    fun `upload authorization signs the fields but not the photo`() {
        val credentials = FlickrSettings.credentials(signedIn)!!
        val fields = listOf("title" to "shot", "is_public" to "1")
        val header = FlickrApi.authorization("POST", FlickrApi.UPLOAD_URL, credentials, fields, "n3", "300")
        val oauth = Regex("""(\w+)="([^"]*)"""").findAll(header.removePrefix("OAuth ")).associate { it.groupValues[1] to URLDecoder.decode(it.groupValues[2], Charsets.UTF_8) }
        assertEquals(credentials.token, oauth["oauth_token"])
        assertFalse("title" in oauth)
        val unsigned = oauth.filterKeys { it != "oauth_signature" }.toList()
        val expected = OAuth1.sign(OAuth1.baseString("POST", FlickrApi.UPLOAD_URL, unsigned + fields), credentials.consumerSecret, credentials.tokenSecret)
        assertEquals(expected, oauth["oauth_signature"])
    }

    @Test
    fun `multipart body holds the fields and the photo part`() {
        val body = String(FlickrApi.multipart("B", listOf("title" to "shot"), "shot.png", "image/png", byteArrayOf(1, 2)), Charsets.ISO_8859_1)
        assertTrue("--B\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nshot\r\n" in body)
        assertTrue("--B\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"shot.png\"\r\nContent-Type: image/png\r\n\r\n\u0001\u0002\r\n--B--\r\n" in body)
    }

    @Test
    fun `upload reply gives the photo id or a readable error`() {
        assertEquals("1234", FlickrApi.photoId(200, "<rsp stat=\"ok\">\n<photoid>1234</photoid>\n</rsp>"))
        val signIn = assertFailsWith<UploadException> { FlickrApi.photoId(200, "<rsp stat=\"fail\"><err code=\"98\" msg=\"Invalid auth token\" /></rsp>") }
        assertTrue("sign in" in signIn.message!!.lowercase())
        val other = assertFailsWith<UploadException> { FlickrApi.photoId(200, "<rsp stat=\"fail\"><err code=\"6\" msg=\"Upload limit reached\" /></rsp>") }
        assertTrue("Upload limit reached" in other.message!!)
        val http = assertFailsWith<UploadException> { FlickrApi.photoId(502, "<html>Bad gateway</html>") }
        assertTrue("502" in http.message!!)
    }

    @Test
    fun `photo link uses the user nsid`() {
        assertEquals("https://www.flickr.com/photos/21207597@N07/1234", FlickrApi.photoUrl("21207597@N07", "1234"))
    }

    @Test
    fun `deletion keeps credentials under secret names and the photo id in the url`() {
        val request = FlickrApi.deletion(signedIn, "1234")
        assertEquals(DeletionKind.REQUEST, request.kind)
        assertEquals(DeletionMethod.POST, request.method)
        assertEquals("1234", request.url)
        assertEquals(FlickrSettings.KIND, request.integration)
        assertTrue(request.headers.all { SecretFields.isSecretName(it.first) })
        val credentials = FlickrSettings.credentials(signedIn)!!
        assertEquals(credentials, FlickrSettings.credentials(request.headers.toMap()))

        val profile = UploaderProfile(id = "flickr-1", name = "Flickr", integration = signedIn)
        val stored = SecretRefs.toReferences(request, profile)
        assertFalse(credentials.tokenSecret in stored.toJson().toString())
        assertEquals(request, assertIs<SecretRefs.Resolution.Ready>(SecretRefs.resolve(stored, listOf(profile))).request)
    }

    @Test
    fun `delete call names the method and photo`() {
        assertEquals(listOf("method" to "flickr.photos.delete", "api_key" to "ck", "photo_id" to "1234"), FlickrApi.deleteFields("ck", "1234"))
    }

    @Test
    fun `delete treats photo not found as already deleted`() {
        FlickrApi.checkDeleted(200, "<rsp stat=\"ok\"></rsp>")
        FlickrApi.checkDeleted(200, "<rsp stat=\"fail\"><err code=\"1\" msg=\"Photo not found\" /></rsp>")
        assertFailsWith<UploadException> { FlickrApi.checkDeleted(200, "<rsp stat=\"fail\"><err code=\"99\" msg=\"Insufficient permissions\" /></rsp>") }
    }
}
