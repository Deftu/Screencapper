package dev.deftu.screencapper.upload.integration.googlephotos

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.upload.UploadException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class ApiRequest(val url: String, val headers: List<Pair<String, String>>, val body: ByteArray) {
    val text: String get() = String(body, Charsets.UTF_8)

    fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, true) }?.second
}

data class GoogleTokens(val accessToken: String, val expiresAt: Long, val refreshToken: String, val scope: String) {
    val canUpload: Boolean get() = GoogleOAuth.SCOPE in scope.split(' ')
}

object GoogleOAuth {
    const val CLIENT_REJECTED = "Google didn't accept the client ID or secret."
    const val SCOPE = "https://www.googleapis.com/auth/photoslibrary.appendonly"
    private const val AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    private val random = SecureRandom()
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    fun randomToken(): String = base64.encodeToString(ByteArray(32).also(random::nextBytes))

    fun challenge(verifier: String): String = base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun authorizationUrl(clientId: String, redirectUri: String, challenge: String, state: String): String = AUTHORIZE_URL + "?" + form(
        listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to SCOPE,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "access_type" to "offline",
            "prompt" to "consent",
        ),
    )

    fun exchange(clientId: String, clientSecret: String, code: String, verifier: String, redirectUri: String): ApiRequest = tokenRequest(
        listOf("client_id" to clientId, "client_secret" to clientSecret, "code" to code, "code_verifier" to verifier, "grant_type" to "authorization_code", "redirect_uri" to redirectUri),
    )

    fun refresh(clientId: String, clientSecret: String, refreshToken: String): ApiRequest = tokenRequest(
        listOf("client_id" to clientId, "client_secret" to clientSecret, "grant_type" to "refresh_token", "refresh_token" to refreshToken),
    )

    fun parseTokens(body: String, now: Long, previousRefresh: String = ""): GoogleTokens {
        val json = parse(body) ?: throw UploadException("Google sent an unreadable sign-in reply.")
        val access = json.string("access_token").ifEmpty { throw UploadException("Google sent no access token.") }
        val expiresIn = json.get("expires_in")?.takeIf { it.isJsonPrimitive }?.asLong ?: 3600L
        return GoogleTokens(access, now + expiresIn * 1000L, json.string("refresh_token").ifEmpty { previousRefresh }, json.string("scope"))
    }

    fun error(body: String): String? = parse(body)?.string("error")?.takeIf { it.isNotEmpty() }

    fun codeFrom(reply: Map<String, String>, state: String): String {
        reply["error"]?.let { throw UploadException(if (it == "access_denied") "Sign-in was cancelled." else "Google sign-in failed: $it") }
        if (reply["state"] != state) throw UploadException("The sign-in reply didn't match. Try again.")
        return reply["code"]?.takeIf { it.isNotEmpty() } ?: throw UploadException("Google sent no sign-in code. Try again.")
    }

    private fun tokenRequest(fields: List<Pair<String, String>>): ApiRequest = ApiRequest(
        TOKEN_URL,
        listOf("Content-Type" to "application/x-www-form-urlencoded"),
        form(fields.filter { it.second.isNotBlank() }).toByteArray(Charsets.UTF_8),
    )

    private fun form(fields: List<Pair<String, String>>): String = fields.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun parse(body: String): JsonObject? = runCatching { JsonParser.parseString(body) as? JsonObject }.getOrNull()

    private fun JsonObject.string(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}
