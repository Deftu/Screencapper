package dev.deftu.screencapper.upload

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod

data class DeletionTarget(val method: DeletionMethod, val url: String, val body: String)

data class DeletionRequest(
    val kind: DeletionKind = DeletionKind.NONE,
    val method: DeletionMethod = DeletionMethod.DELETE,
    val url: String = "",
    val headers: List<Pair<String, String>> = emptyList(),
    val body: String = "",
    val integration: String = "",
) {
    val target: DeletionTarget get() = DeletionTarget(method, url.trim(), body.trim())

    fun render(values: TemplateValues, response: String): DeletionRequest? {
        if (kind == DeletionKind.NONE || url.isBlank()) return null
        val rendered = DeletionRequest(
            kind,
            method,
            UploadTemplate.renderUrl(url, values, response).trim(),
            headers.map { (key, value) -> key to UploadTemplate.render(value, values, response) },
            UploadTemplate.render(body, values, response),
            integration,
        )
        if (integration.isEmpty() && !isWebLink(rendered.url)) throw UploadException("The deletion address isn't a web link")
        return rendered
    }

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("kind", kind.name)
        addProperty("method", method.name)
        addProperty("url", url)
        add("headers", pairsToJson(headers))
        addProperty("body", body)
        if (integration.isNotEmpty()) addProperty("integration", integration)
    }

    companion object {
        fun fromJson(json: JsonObject): DeletionRequest = DeletionRequest(
            kind = DeletionKind.entries.firstOrNull { it.name == json.get("kind")?.asString } ?: DeletionKind.NONE,
            method = DeletionMethod.entries.firstOrNull { it.name == json.get("method")?.asString } ?: DeletionMethod.DELETE,
            url = json.get("url")?.asString.orEmpty(),
            headers = jsonToPairs(json.get("headers") as? JsonArray),
            body = json.get("body")?.asString.orEmpty(),
            integration = json.get("integration")?.asString.orEmpty(),
        )
    }
}

internal fun jsonToPairs(array: JsonArray?): List<Pair<String, String>> =
    array?.mapNotNull { entry -> (entry as? JsonArray)?.takeIf { it.size() == 2 }?.let { it[0].asString to it[1].asString } }.orEmpty()

internal fun pairsToJson(pairs: List<Pair<String, String>>): JsonArray = JsonArray().apply {
    pairs.forEach { (k, v) -> add(JsonArray().apply { add(k); add(v) }) }
}
