package dev.deftu.screencapper.upload.integration.googlephotos

import com.google.gson.JsonParser
import dev.deftu.screencapper.upload.UploadException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GooglePhotosApiTest {
    private val created = """
        {
          "newMediaItemResults": [
            {
              "uploadToken": "upload-token",
              "status": {"message": "Success"},
              "mediaItem": {
                "id": "media-item-id",
                "description": "",
                "productUrl": "https://photos.google.com/lr/photo/media-item-id",
                "mimeType": "image/png",
                "mediaMetadata": {"width": "1920", "height": "1080", "creationTime": "2026-11-20T10:00:00Z", "photo": {}},
                "filename": "shot.png"
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `byte upload sends the raw file with the documented headers`() {
        val bytes = byteArrayOf(1, 2, 3)
        val request = GooglePhotosApi.uploadBytes("ya29.token", bytes, "image/png")
        assertEquals("https://photoslibrary.googleapis.com/v1/uploads", request.url)
        assertEquals("Bearer ya29.token", request.header("Authorization"))
        assertEquals("application/octet-stream", request.header("Content-Type"))
        assertEquals("image/png", request.header("X-Goog-Upload-Content-Type"))
        assertEquals("raw", request.header("X-Goog-Upload-Protocol"))
        assertContentEquals(bytes, request.body)
    }

    @Test
    fun `batch create names the file and skips the album when there is none`() {
        val request = GooglePhotosApi.batchCreate("t", "upload-token", "shot \"1\".png", "")
        assertEquals("https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate", request.url)
        assertEquals("application/json", request.header("Content-Type"))
        val json = JsonParser.parseString(request.text).asJsonObject
        assertFalse(json.has("albumId"))
        val item = json.getAsJsonArray("newMediaItems")[0].asJsonObject.getAsJsonObject("simpleMediaItem")
        assertEquals("upload-token", item.get("uploadToken").asString)
        assertEquals("shot \"1\".png", item.get("fileName").asString)
    }

    @Test
    fun `batch create adds to the album when one is set`() {
        val json = JsonParser.parseString(GooglePhotosApi.batchCreate("t", "u", "a.png", "album-1").text).asJsonObject
        assertEquals("album-1", json.get("albumId").asString)
    }

    @Test
    fun `created item gives its id and product url`() {
        assertEquals(CreatedItem("media-item-id", "https://photos.google.com/lr/photo/media-item-id"), GooglePhotosApi.parseCreated(created))
    }

    @Test
    fun `failed item status is reported`() {
        val failed = """{"newMediaItemResults":[{"uploadToken":"upload-token","status":{"code":13,"message":"Internal error"}}]}"""
        val error = assertFailsWith<UploadException> { GooglePhotosApi.parseCreated(failed) }
        assertEquals("Google Photos didn't save it: Internal error", error.message)
        assertFailsWith<UploadException> { GooglePhotosApi.parseCreated("""{"newMediaItemResults":[]}""") }
    }

    @Test
    fun `album is created with a title and its id is read back`() {
        val request = GooglePhotosApi.createAlbum("t", "Screencapper")
        assertEquals("https://photoslibrary.googleapis.com/v1/albums", request.url)
        assertEquals("Screencapper", JsonParser.parseString(request.text).asJsonObject.getAsJsonObject("album").get("title").asString)
        assertEquals("album-1", GooglePhotosApi.parseAlbumId("""{"id":"album-1","title":"Screencapper","productUrl":"https://photos.google.com/lr/album/album-1","isWriteable":true}"""))
        assertFailsWith<UploadException> { GooglePhotosApi.parseAlbumId("{}") }
    }

    @Test
    fun `error responses give a short reason`() {
        val body = """{"error":{"code":403,"message":"Request had insufficient authentication scopes.","status":"PERMISSION_DENIED"}}"""
        assertEquals("Google Photos answered with HTTP 403: Request had insufficient authentication scopes.", GooglePhotosApi.describe(403, body))
        assertEquals("Google Photos answered with HTTP 500", GooglePhotosApi.describe(500, "oops"))
        assertTrue(GooglePhotosApi.describe(429, "").contains("Try again"))
    }
}
