package dev.deftu.screencapper.upload

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.config.LinkMode
import java.net.URI

object UploaderPresets {
    private val DISCORD_HOSTS = setOf("discord.com", "discordapp.com").flatMap { listOf(it, "canary.$it", "ptb.$it") }.toSet()
    private val WEBHOOK_PATH = Regex("""^/api(/v\d+)?/webhooks/\d+/[\w-]+/?$""")

    fun discordProblem(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return "That isn't a valid link."
        if (uri.scheme?.lowercase() != "https" || uri.host?.lowercase() !in DISCORD_HOSTS) return "Paste a Discord webhook link, like https://discord.com/api/webhooks/..."
        if (!WEBHOOK_PATH.matches(uri.rawPath.orEmpty())) return "That link isn't a webhook. Copy it from the channel's Integrations > Webhooks settings."
        return null
    }

    fun discord(webhookUrl: String, options: DiscordOptions? = null): UploaderProfile {
        val uri = URI(webhookUrl.trim())
        val base = "${uri.scheme}://${uri.rawAuthority}${uri.rawPath.trimEnd('/')}"
        val chosen = options ?: DiscordOptions(threadId = discordThreadId(webhookUrl))
        val kept = queryParts(uri).filterNot { it.startsWith("wait=") || it.startsWith("thread_id=") }
        val query = kept + listOfNotNull(chosen.threadId.trim().takeIf { it.isNotEmpty() }?.let { "thread_id=$it" })
        val threadQuery = if (query.isEmpty()) "" else "?" + query.joinToString("&")
        return UploaderProfile(
            name = "Discord webhook",
            url = "$base?" + (query + "wait=true").joinToString("&"),
            fileField = "files[0]",
            responseUrl = DISCORD_RESPONSE,
            formFields = listOf(DiscordMessage.FIELD to DiscordMessage.template(chosen)),
            sizeLimitMb = "20",
            deletion = DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "$base/messages/{json:id}$threadQuery"),
            discord = chosen,
        )
    }

    private const val DISCORD_RESPONSE = "attachments[0].url||embeds[0].image.url"

    fun responsePath(profile: UploaderProfile): String =
        if (profile.discord != null && profile.responseUrl.trim() == "attachments[0].url") DISCORD_RESPONSE else profile.responseUrl

    fun discordThreadId(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return ""
        return queryParts(uri).firstOrNull { it.startsWith("thread_id=") }?.removePrefix("thread_id=").orEmpty()
    }

    fun refreshed(profile: UploaderProfile): UploaderProfile {
        val options = profile.discord ?: return profile
        val kept = options.copy(threadId = options.threadId.ifBlank { discordThreadId(profile.url) })
        return runCatching { withDiscord(profile, kept) }.getOrDefault(profile)
    }

    fun withDiscord(profile: UploaderProfile, options: DiscordOptions): UploaderProfile {
        val fresh = discord(profile.url, options)
        return profile.copy(
            url = fresh.url,
            formFields = profile.formFields.filterNot { it.first == DiscordMessage.FIELD } + fresh.formFields,
            deletion = fresh.deletion,
            responseUrl = responsePath(profile.copy(discord = options)),
            discord = options,
        )
    }

    private fun queryParts(uri: URI): List<String> = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }

    fun immichProblem(server: String): String? {
        val uri = runCatching { URI(server.trim()) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) {
            return "Enter your server's address, like https://photos.example.com"
        }
        return null
    }

    fun immich(server: String, apiKey: String, ownerLink: Boolean): UploaderProfile {
        val base = server.trim().trimEnd('/').removeSuffix("/api").trimEnd('/')
        val key = apiKey.trim()
        return UploaderProfile(
            name = "Immich",
            url = "$base/api/assets",
            fileField = "assetData",
            headers = listOf("x-api-key" to key, "Accept" to "application/json"),
            formFields = listOf(
                "deviceAssetId" to "screencapper-{uuid}",
                "deviceId" to "screencapper",
                "fileCreatedAt" to "{createdAt}",
                "fileModifiedAt" to "{modifiedAt}",
                "filename" to "{fileName}",
            ),
            responseUrl = "$base/photos/{json:id}",
            linkMode = if (ownerLink) LinkMode.LINK else LinkMode.NONE,
            deletion = DeletionRequest(
                DeletionKind.REQUEST,
                DeletionMethod.DELETE,
                "$base/api/assets",
                listOf("x-api-key" to key, "Content-Type" to "application/json"),
                """{"ids":["{json:id}"]}""",
            ),
        )
    }
}
