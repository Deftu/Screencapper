package dev.deftu.screencapper.upload

import kotlin.test.Test
import kotlin.test.assertEquals

class UploadFailureTest {
    @Test
    fun imgurErrorsSayWhatWentWrong() {
        val body = """{"status":400,"success":false,"data":{"error":"We don't support that file type!","request":"/3/image","method":"POST"}}"""
        assertEquals("The host answered with HTTP 400: We don't support that file type!", UploadHttp.failure(400, body))
    }

    @Test
    fun nestedErrorMessagesAreFound() {
        assertEquals("The host answered with HTTP 400: Bad image", UploadHttp.failure(400, """{"data":{"error":{"code":1003,"message":"Bad image"}}}"""))
        assertEquals("The host answered with HTTP 403: Forbidden here", UploadHttp.failure(403, """{"error":{"message":"Forbidden here"}}"""))
        assertEquals("The host answered with HTTP 413: Too big", UploadHttp.failure(413, """{"message":"Too big"}"""))
        assertEquals("The host answered with HTTP 401: No key", UploadHttp.failure(401, """{"error":"No key"}"""))
    }

    @Test
    fun shortPlainTextIsKeptOnOneLine() {
        assertEquals("The host answered with HTTP 500: Upload failed. Try again", UploadHttp.failure(500, "Upload failed.\r\n  Try again\n"))
    }

    @Test
    fun htmlAndEmptyBodiesAreLeftOut() {
        assertEquals("The host answered with HTTP 502", UploadHttp.failure(502, "<!DOCTYPE html><html><body>Bad gateway</body></html>"))
        assertEquals("The host answered with HTTP 400", UploadHttp.failure(400, "   "))
        assertEquals("The host answered with HTTP 400", UploadHttp.failure(400, """{"status":400,"success":false}"""))
    }

    @Test
    fun longReasonsAreCut() {
        val reason = UploadHttp.failure(400, "x".repeat(1000)).removePrefix("The host answered with HTTP 400: ")
        assertEquals(UploadHttp.MAX_REASON, reason.length)
        assertEquals('…', reason.last())
    }
}
