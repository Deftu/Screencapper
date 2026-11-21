package dev.deftu.screencapper.upload.integration.flickr

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class OAuthCredentials(val consumerKey: String, val consumerSecret: String, val token: String = "", val tokenSecret: String = "")

internal object OAuth1 {
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val HEX = "0123456789ABCDEF"
    private val random = SecureRandom()

    fun encode(value: String): String {
        val out = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            if (code < 0x80 && code.toChar() in UNRESERVED) out.append(code.toChar()) else out.append('%').append(HEX[code shr 4]).append(HEX[code and 0xF])
        }
        return out.toString()
    }

    fun baseString(method: String, url: String, params: List<Pair<String, String>>): String {
        val normalized = params.map { (name, value) -> encode(name) to encode(value) }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { (name, value) -> "$name=$value" }
        return "${method.uppercase()}&${encode(url)}&${encode(normalized)}"
    }

    fun sign(baseString: String, consumerSecret: String, tokenSecret: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec("${encode(consumerSecret)}&${encode(tokenSecret)}".toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return Base64.getEncoder().encodeToString(mac.doFinal(baseString.toByteArray(Charsets.UTF_8)))
    }

    fun oauthParams(credentials: OAuthCredentials, nonce: String, timestamp: String): List<Pair<String, String>> = buildList {
        add("oauth_consumer_key" to credentials.consumerKey)
        add("oauth_nonce" to nonce)
        add("oauth_signature_method" to "HMAC-SHA1")
        add("oauth_timestamp" to timestamp)
        add("oauth_version" to "1.0")
        if (credentials.token.isNotEmpty()) add("oauth_token" to credentials.token)
    }

    fun signed(
        method: String,
        url: String,
        credentials: OAuthCredentials,
        params: List<Pair<String, String>>,
        extra: List<Pair<String, String>> = emptyList(),
        nonce: String = nonce(),
        timestamp: String = timestamp(),
    ): List<Pair<String, String>> {
        val oauth = oauthParams(credentials, nonce, timestamp) + extra
        return oauth + ("oauth_signature" to sign(baseString(method, url, oauth + params), credentials.consumerSecret, credentials.tokenSecret))
    }

    fun header(oauth: List<Pair<String, String>>): String = "OAuth " + oauth.joinToString(", ") { (name, value) -> "${encode(name)}=\"${encode(value)}\"" }

    fun query(params: List<Pair<String, String>>): String = params.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }

    fun nonce(): String = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    fun timestamp(): String = (System.currentTimeMillis() / 1000).toString()
}
