package dev.deftu.screencapper.upload.integration.nextcloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.isWebLink

data class Reply(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

data class LoginStart(val loginUrl: String, val pollEndpoint: String, val pollToken: String)

data class LoginGrant(val server: String, val loginName: String, val appPassword: String) {
    override fun toString(): String = "LoginGrant(server=$server, loginName=$loginName)"
}

data class NextcloudUser(val id: String, val displayName: String)

data class NextcloudShare(val id: String, val url: String)

sealed interface ConnectionCheck {
    data class Ok(val user: NextcloudUser) : ConnectionCheck
    data class Failed(val message: String) : ConnectionCheck
}

object NextcloudReplies {
    const val UNREACHABLE = "Couldn't reach the server. Check the address and your connection."
    const val NOT_NEXTCLOUD = "That doesn't look like a Nextcloud server. Check the address."
    const val WRONG_LOGIN = "Wrong username or app password."

    private const val MAINTENANCE = "The server is in maintenance mode or busy. Try again later."
    private const val MAX_MESSAGE = 200
    private val DAV_MESSAGE = Regex("<s:message>(.*?)</s:message>", RegexOption.DOT_MATCHES_ALL)

    fun loginStart(reply: Reply): LoginStart {
        if (reply.status == 404) throw UploadException("This server can't sign in through the browser. Use an app password instead.")
        if (!reply.ok) throw UploadException(describe(reply))
        val json = parse(reply.body) ?: throw UploadException(NOT_NEXTCLOUD)
        val poll = json.get("poll") as? JsonObject
        val start = LoginStart(json.text("login"), poll?.text("endpoint").orEmpty(), poll?.text("token").orEmpty())
        if (!isWebLink(start.loginUrl) || !isWebLink(start.pollEndpoint) || start.pollToken.isEmpty()) throw UploadException(NOT_NEXTCLOUD)
        return start
    }

    fun loginPoll(reply: Reply): LoginGrant? {
        if (reply.status == 404) return null
        if (!reply.ok) throw UploadException("Sign-in failed. ${describe(reply)}")
        val json = parse(reply.body)
        val grant = LoginGrant(json?.text("server").orEmpty(), json?.text("loginName").orEmpty(), json?.text("appPassword").orEmpty())
        if (grant.loginName.isEmpty() || grant.appPassword.isEmpty()) throw UploadException("Sign-in didn't return a login. Try again.")
        return grant
    }

    fun currentUser(reply: Reply): NextcloudUser {
        if (!reply.ok) throw UploadException(describe(reply))
        val data = ocsData(reply.body) ?: throw UploadException(NOT_NEXTCLOUD)
        val id = data.text("id").ifEmpty { throw UploadException(NOT_NEXTCLOUD) }
        return NextcloudUser(id, data.text("display-name").ifEmpty { data.text("displayname") }.ifEmpty { id })
    }

    fun share(reply: Reply): NextcloudShare {
        if (!reply.ok) throw UploadException(describe(reply))
        val data = ocsData(reply.body)
        val share = NextcloudShare(data?.text("id").orEmpty(), data?.text("url").orEmpty())
        if (share.id.isEmpty() || !isWebLink(share.url)) throw UploadException("Nextcloud didn't return a link.")
        return share
    }

    fun describe(reply: Reply): String = when (reply.status) {
        401 -> WRONG_LOGIN
        429 -> "Too many failed sign-ins. Wait a minute and try again."
        503 -> MAINTENANCE
        507 -> "Your Nextcloud storage is full."
        else -> message(reply.body)?.let { "Nextcloud answered with HTTP ${reply.status}: $it" } ?: "Nextcloud answered with HTTP ${reply.status}"
    }

    fun check(reply: Reply): ConnectionCheck = when {
        reply.status == 401 || reply.status == 429 || reply.status == 503 -> ConnectionCheck.Failed(describe(reply))
        reply.ok -> runCatching { ConnectionCheck.Ok(currentUser(reply)) }.getOrElse { ConnectionCheck.Failed(NOT_NEXTCLOUD) }
        ocsMeta(reply.body) != null -> ConnectionCheck.Failed(describe(reply))
        else -> ConnectionCheck.Failed(NOT_NEXTCLOUD)
    }

    private fun message(body: String): String? {
        val text = ocsMeta(body)?.text("message")?.takeIf { it.isNotBlank() }
            ?: DAV_MESSAGE.find(body)?.groupValues?.get(1)?.let(::unescapeXml)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        return if (text.length > MAX_MESSAGE) text.take(MAX_MESSAGE) + "..." else text
    }

    private fun unescapeXml(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    private fun parse(body: String): JsonObject? = runCatching { JsonParser.parseString(body) as? JsonObject }.getOrNull()

    private fun ocs(body: String): JsonObject? = parse(body)?.get("ocs") as? JsonObject

    private fun ocsMeta(body: String): JsonObject? = ocs(body)?.get("meta") as? JsonObject

    private fun ocsData(body: String): JsonObject? = ocs(body)?.get("data") as? JsonObject

    private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.asString.orEmpty()
}
