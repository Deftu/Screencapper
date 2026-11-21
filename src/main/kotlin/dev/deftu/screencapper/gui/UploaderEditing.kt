package dev.deftu.screencapper.gui

import dev.deftu.screencapper.upload.DiscordOptions
import dev.deftu.screencapper.upload.UploaderPresets
import dev.deftu.screencapper.upload.UploaderProfile
import java.net.URI
import java.util.UUID

internal object TemplateInsert {
    fun insert(text: String, start: Int, end: Int, token: String, maxLength: Int = Int.MAX_VALUE): Pair<String, Int>? {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        val result = text.substring(0, from) + token + text.substring(to)
        if (result.length > maxLength) return null
        return result to from + token.length
    }
}

internal object UploaderDisplay {
    fun host(profile: UploaderProfile): String {
        if (profile.isBuiltIn || profile.integration != null) return profile.host
        if (profile.url.isBlank()) return "No address set"
        return runCatching { URI(profile.url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Invalid address"
    }

    fun kind(profile: UploaderProfile): String = when {
        profile.isBuiltIn -> "Built in"
        profile.integration != null -> IntegrationDialogs.of(profile)?.label ?: "Not available"
        isDiscord(profile) -> "Discord"
        ImmichKey.isImmich(profile) -> "Immich"
        else -> "Custom"
    }

    fun isDiscord(profile: UploaderProfile): Boolean = profile.discord != null || UploaderPresets.discordProblem(profile.url) == null
}

internal object ImmichKey {
    private const val HEADER = "x-api-key"

    fun isImmich(profile: UploaderProfile): Boolean =
        profile.headers.any { it.first.equals(HEADER, ignoreCase = true) } &&
            runCatching { URI(profile.url.trim()).path }.getOrNull()?.trimEnd('/')?.endsWith("/api/assets") == true

    fun read(profile: UploaderProfile): String = profile.headers.firstOrNull { it.first.equals(HEADER, ignoreCase = true) }?.second.orEmpty()

    fun write(profile: UploaderProfile, key: String): UploaderProfile {
        val trimmed = key.trim()
        fun replace(pairs: List<Pair<String, String>>) = pairs.map { if (it.first.equals(HEADER, ignoreCase = true)) it.first to trimmed else it }
        return profile.copy(headers = replace(profile.headers), deletion = profile.deletion.copy(headers = replace(profile.deletion.headers)))
    }
}

internal object UploaderEdits {
    fun duplicate(profile: UploaderProfile): UploaderProfile = profile.copy(id = UUID.randomUUID().toString(), name = "${profile.name} copy")

    fun discordOptions(profile: UploaderProfile): DiscordOptions =
        (profile.discord ?: DiscordOptions()).copy(threadId = UploaderPresets.discordThreadId(profile.url))

    fun saved(draft: UploaderProfile, initial: DiscordOptions?, options: DiscordOptions?): UploaderProfile =
        if (options == null || options == initial) draft else UploaderPresets.withDiscord(draft, options)
}
