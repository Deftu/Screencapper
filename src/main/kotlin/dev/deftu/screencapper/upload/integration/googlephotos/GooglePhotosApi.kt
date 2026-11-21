package dev.deftu.screencapper.upload.integration.googlephotos

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.upload.UploadException

data class CreatedItem(val id: String, val productUrl: String)

object GooglePhotosApi {
    private const val BASE = "https://photoslibrary.googleapis.com/v1"

    fun uploadBytes(accessToken: String, bytes: ByteArray, contentType: String): ApiRequest = ApiRequest(
        "$BASE/uploads",
        listOf(
            "Authorization" to "Bearer $accessToken",
            "Content-Type" to "application/octet-stream",
            "X-Goog-Upload-Content-Type" to contentType,
            "X-Goog-Upload-Protocol" to "raw",
        ),
        bytes,
    )

    fun batchCreate(accessToken: String, uploadToken: String, fileName: String, albumId: String): ApiRequest {
        val item = JsonObject().apply {
            add("simpleMediaItem", JsonObject().apply {
                addProperty("fileName", fileName)
                addProperty("uploadToken", uploadToken)
            })
        }
        val body = JsonObject().apply {
            if (albumId.isNotEmpty()) addProperty("albumId", albumId)
            add("newMediaItems", JsonArray().apply { add(item) })
        }
        return json("$BASE/mediaItems:batchCreate", accessToken, body)
    }

    fun createAlbum(accessToken: String, title: String): ApiRequest =
        json("$BASE/albums", accessToken, JsonObject().apply { add("album", JsonObject().apply { addProperty("title", title) }) })

    fun parseCreated(body: String): CreatedItem {
        val result = parse(body)?.getAsJsonArray("newMediaItemResults")?.firstOrNull() as? JsonObject
            ?: throw UploadException("Google Photos sent an unreadable reply.")
        val status = result.get("status") as? JsonObject
        val code = status?.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (code != 0) throw UploadException("Google Photos didn't save it: ${status?.string("message")?.ifEmpty { null } ?: "error $code"}")
        val item = result.get("mediaItem") as? JsonObject ?: throw UploadException("Google Photos didn't save it.")
        return CreatedItem(item.string("id"), item.string("productUrl"))
    }

    fun parseAlbumId(body: String): String =
        parse(body)?.string("id")?.takeIf { it.isNotEmpty() } ?: throw UploadException("Google Photos didn't make the album.")

    fun describe(status: Int, body: String): String {
        if (status == 429) return "Google Photos is busy. Try again in a minute."
        val message = (parse(body)?.get("error") as? JsonObject)?.string("message").orEmpty()
        return "Google Photos answered with HTTP $status" + if (message.isEmpty()) "" else ": ${message.trimEnd('.')}."
    }

    private fun json(url: String, accessToken: String, body: JsonObject): ApiRequest = ApiRequest(
        url,
        listOf("Authorization" to "Bearer $accessToken", "Content-Type" to "application/json"),
        body.toString().toByteArray(Charsets.UTF_8),
    )

    private fun parse(body: String): JsonObject? = runCatching { JsonParser.parseString(body) as? JsonObject }.getOrNull()

    private fun JsonObject.string(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}
