package dev.deftu.screencapper.upload.integration.googlephotos

import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.integration.LoopbackReceiver
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GoogleOAuthTest {
    private val tokenResponse = """
        {
          "access_token": "1/fFAGRNJru1FTz70BzhT3Zg",
          "expires_in": 3920,
          "token_type": "Bearer",
          "scope": "https://www.googleapis.com/auth/photoslibrary.appendonly",
          "refresh_token": "1//xEoDL4iW3cxlI7yDbSRFYNG01kVKM2C-259HOF2aQbI"
        }
    """.trimIndent()

    @Test
    fun `pkce challenge matches the RFC 7636 example`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", GoogleOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun `random verifiers are long enough and url safe`() {
        val first = GoogleOAuth.randomToken()
        assertEquals(43, first.length)
        assertTrue(first.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(first, GoogleOAuth.randomToken())
    }

    @Test
    fun `authorization url asks for offline upload access with pkce and state`() {
        val url = GoogleOAuth.authorizationUrl("123.apps.googleusercontent.com", "http://127.0.0.1:5123/callback", "chal", "st&ate")
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        val query = LoopbackReceiver.parseQuery(URI(url).rawQuery)
        assertEquals(
            mapOf(
                "client_id" to "123.apps.googleusercontent.com",
                "redirect_uri" to "http://127.0.0.1:5123/callback",
                "response_type" to "code",
                "scope" to "https://www.googleapis.com/auth/photoslibrary.appendonly",
                "code_challenge" to "chal",
                "code_challenge_method" to "S256",
                "state" to "st&ate",
                "access_type" to "offline",
                "prompt" to "consent",
            ),
            query,
        )
        assertFalse('+' in url)
    }

    @Test
    fun `code exchange posts the verifier and redirect uri`() {
        val request = GoogleOAuth.exchange("id", "sec ret", "4/code", "verifier", "http://127.0.0.1:1/callback")
        assertEquals("https://oauth2.googleapis.com/token", request.url)
        assertEquals("application/x-www-form-urlencoded", request.header("Content-Type"))
        assertEquals(
            mapOf(
                "client_id" to "id",
                "client_secret" to "sec ret",
                "code" to "4/code",
                "code_verifier" to "verifier",
                "grant_type" to "authorization_code",
                "redirect_uri" to "http://127.0.0.1:1/callback",
            ),
            LoopbackReceiver.parseQuery(request.text),
        )
    }

    @Test
    fun `blank client secret is left out`() {
        val form = LoopbackReceiver.parseQuery(GoogleOAuth.refresh("id", " ", "1//r").text)
        assertEquals(mapOf("client_id" to "id", "grant_type" to "refresh_token", "refresh_token" to "1//r"), form)
    }

    @Test
    fun `token response is parsed with an absolute expiry`() {
        val tokens = GoogleOAuth.parseTokens(tokenResponse, now = 1_000L)
        assertEquals("1/fFAGRNJru1FTz70BzhT3Zg", tokens.accessToken)
        assertEquals("1//xEoDL4iW3cxlI7yDbSRFYNG01kVKM2C-259HOF2aQbI", tokens.refreshToken)
        assertEquals(1_000L + 3920_000L, tokens.expiresAt)
        assertTrue(tokens.canUpload)
    }

    @Test
    fun `refresh response keeps the old refresh token`() {
        val body = """{"access_token":"new","expires_in":3599,"scope":"https://www.googleapis.com/auth/photoslibrary.appendonly","token_type":"Bearer"}"""
        assertEquals("old", GoogleOAuth.parseTokens(body, 0L, previousRefresh = "old").refreshToken)
    }

    @Test
    fun `missing upload scope is noticed`() {
        val body = """{"access_token":"a","expires_in":10,"scope":"openid","refresh_token":"r"}"""
        assertFalse(GoogleOAuth.parseTokens(body, 0L).canUpload)
    }

    @Test
    fun `token error code is read`() {
        assertEquals("invalid_grant", GoogleOAuth.error("""{"error":"invalid_grant","error_description":"Token has been expired or revoked."}"""))
        assertEquals(null, GoogleOAuth.error("<html>"))
    }

    @Test
    fun `sign in reply gives the code only when the state matches`() {
        assertEquals("4/abc", GoogleOAuth.codeFrom(mapOf("code" to "4/abc", "state" to "s1", "scope" to "x"), "s1"))
        assertFailsWith<UploadException> { GoogleOAuth.codeFrom(mapOf("code" to "4/abc", "state" to "other"), "s1") }
        assertFailsWith<UploadException> { GoogleOAuth.codeFrom(mapOf("state" to "s1"), "s1") }
        val denied = assertFailsWith<UploadException> { GoogleOAuth.codeFrom(mapOf("error" to "access_denied", "state" to "s1"), "s1") }
        assertEquals("Sign-in was cancelled.", denied.message)
    }
}
