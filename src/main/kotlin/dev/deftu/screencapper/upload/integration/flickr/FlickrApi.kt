package dev.deftu.screencapper.upload.integration.flickr

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.TemplateValues
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploadTemplate
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import dev.deftu.screencapper.upload.integration.LoopbackReceiver
import java.io.ByteArrayOutputStream

internal data class RequestToken(val token: String, val secret: String)

internal object FlickrApi {
    const val REQUEST_TOKEN_URL = "https://www.flickr.com/services/oauth/request_token"
    const val AUTHORIZE_URL = "https://www.flickr.com/services/oauth/authorize"
    const val ACCESS_TOKEN_URL = "https://www.flickr.com/services/oauth/access_token"
    const val REST_URL = "https://www.flickr.com/services/rest"
    const val UPLOAD_URL = "https://up.flickr.com/services/upload/"
    const val PERMS = "delete"

    private const val PHOTO_NOT_FOUND = 1
    private val SIGN_IN_CODES = setOf(96, 97, 98, 99, 100)
    private val PROBLEM = Regex("[A-Za-z_]+")

    fun requestTokenUrl(consumerKey: String, consumerSecret: String, callback: String, nonce: String, timestamp: String): String {
        val oauth = OAuth1.signed("GET", REQUEST_TOKEN_URL, OAuthCredentials(consumerKey, consumerSecret), emptyList(), listOf("oauth_callback" to callback), nonce, timestamp)
        return "$REQUEST_TOKEN_URL?${OAuth1.query(oauth)}"
    }

    fun authorizeUrl(token: String): String = "$AUTHORIZE_URL?${OAuth1.query(listOf("oauth_token" to token, "perms" to PERMS))}"

    fun accessTokenUrl(consumerKey: String, consumerSecret: String, requestToken: RequestToken, verifier: String, nonce: String, timestamp: String): String {
        val credentials = OAuthCredentials(consumerKey, consumerSecret, requestToken.token, requestToken.secret)
        val oauth = OAuth1.signed("GET", ACCESS_TOKEN_URL, credentials, emptyList(), listOf("oauth_verifier" to verifier), nonce, timestamp)
        return "$ACCESS_TOKEN_URL?${OAuth1.query(oauth)}"
    }

    fun parseRequestToken(body: String): RequestToken {
        val reply = LoopbackReceiver.parseQuery(body.trim())
        val token = reply["oauth_token"].orEmpty()
        val secret = reply["oauth_token_secret"].orEmpty()
        if (token.isEmpty() || secret.isEmpty()) throw UploadException(oauthProblem(200, body))
        return RequestToken(token, secret)
    }

    fun parseAccessToken(body: String): FlickrAccount {
        val reply = LoopbackReceiver.parseQuery(body.trim())
        val account = FlickrAccount(reply["oauth_token"].orEmpty(), reply["oauth_token_secret"].orEmpty(), reply["user_nsid"].orEmpty(), reply["username"].orEmpty())
        if (account.token.isEmpty() || account.tokenSecret.isEmpty() || account.nsid.isEmpty()) throw UploadException(oauthProblem(200, body))
        return account
    }

    fun oauthProblem(status: Int, body: String): String {
        val problem = runCatching { LoopbackReceiver.parseQuery(body.trim())["oauth_problem"] }.getOrNull()?.takeIf { it.matches(PROBLEM) }
        return when {
            problem == "signature_invalid" || problem == "consumer_key_unknown" -> "Flickr didn't accept the key and secret ($problem). Check that both are copied right."
            problem != null -> "Flickr turned down the sign-in ($problem)."
            status in 200..299 -> "Flickr sent a reply Screencapper couldn't read."
            else -> "Flickr answered with HTTP $status."
        }
    }

    fun uploadFields(settings: IntegrationSettings, values: TemplateValues): List<Pair<String, String>> = buildList {
        val title = UploadTemplate.expand(settings[FlickrSettings.TITLE], values).trim()
        val tags = UploadTemplate.expand(settings[FlickrSettings.TAGS], values).trim()
        if (title.isNotEmpty()) add("title" to title)
        if (tags.isNotEmpty()) add("tags" to tags)
        val public = FlickrSettings.isPublic(settings)
        add("is_public" to bit(public))
        add("is_friend" to bit(!public && FlickrSettings.flag(settings, FlickrSettings.FRIENDS)))
        add("is_family" to bit(!public && FlickrSettings.flag(settings, FlickrSettings.FAMILY)))
    }

    fun authorization(
        method: String,
        url: String,
        credentials: OAuthCredentials,
        params: List<Pair<String, String>>,
        nonce: String = OAuth1.nonce(),
        timestamp: String = OAuth1.timestamp(),
    ): String = OAuth1.header(OAuth1.signed(method, url, credentials, params, emptyList(), nonce, timestamp))

    fun multipart(boundary: String, fields: List<Pair<String, String>>, fileName: String, contentType: String, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
        for ((name, value) in fields) write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
        write("--$boundary\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"${headerSafe(fileName)}\"\r\nContent-Type: $contentType\r\n\r\n")
        out.write(bytes)
        write("\r\n--$boundary--\r\n")
        return out.toByteArray()
    }

    fun photoId(status: Int, body: String): String = when (val reply = read(status, body)) {
        is FlickrReply.Ok -> reply.text("photoid").takeIf { id -> id.isNotEmpty() && id.all { it.isDigit() } } ?: throw UploadException("Flickr didn't send back a photo id.")
        is FlickrReply.Fail -> throw UploadException(problem(reply, "take the upload"))
    }

    fun photoUrl(nsid: String, photoId: String): String = "https://www.flickr.com/photos/$nsid/$photoId"

    fun deletion(settings: IntegrationSettings, photoId: String): DeletionRequest = DeletionRequest(
        kind = DeletionKind.REQUEST,
        method = DeletionMethod.POST,
        url = photoId,
        headers = listOf(
            FlickrSettings.API_KEY to settings[FlickrSettings.API_KEY],
            FlickrSettings.API_SECRET to settings[FlickrSettings.API_SECRET],
            FlickrSettings.OAUTH_TOKEN to settings[FlickrSettings.OAUTH_TOKEN],
            FlickrSettings.TOKEN_SECRET to settings[FlickrSettings.TOKEN_SECRET],
        ),
        integration = FlickrSettings.KIND,
    )

    fun deleteFields(apiKey: String, photoId: String): List<Pair<String, String>> =
        listOf("method" to "flickr.photos.delete", "api_key" to apiKey, "photo_id" to photoId)

    fun checkDeleted(status: Int, body: String) {
        val reply = read(status, body)
        if (reply is FlickrReply.Fail && reply.code != PHOTO_NOT_FOUND) throw UploadException(problem(reply, "delete the photo"))
    }

    private fun read(status: Int, body: String): FlickrReply =
        FlickrXml.parse(body) ?: throw UploadException(if (status in 200..299) "Flickr sent a reply Screencapper couldn't read." else "Flickr answered with HTTP $status.")

    private fun problem(reply: FlickrReply.Fail, action: String): String = when (reply.code) {
        in SIGN_IN_CODES -> "Flickr turned down the sign-in: ${reply.message} (${reply.code}). Sign in again in the uploader settings."
        else -> "Flickr couldn't $action: ${reply.message} (${reply.code})."
    }

    private fun bit(value: Boolean): String = if (value) "1" else "0"

    private fun headerSafe(value: String): String = value.replace("\"", "%22").replace("\r", "").replace("\n", "")
}
