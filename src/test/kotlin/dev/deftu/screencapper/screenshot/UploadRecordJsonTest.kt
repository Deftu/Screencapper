package dev.deftu.screencapper.screenshot

import com.google.gson.JsonParser
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.upload.DeletionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UploadRecordJsonTest {
    private fun parse(json: String) = UploadRecord.fromJson(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `records written before deletions existed still load`() {
        val record = parse("""{"uploader":"imgur","uploaderName":"Imgur","url":"https://i.imgur.com/a.png","at":1700000000000}""")
        assertEquals(UploadRecord("imgur", "Imgur", "https://i.imgur.com/a.png", 1700000000000), record)
        assertNull(record.deletion)
        assertTrue(record.hasLink)
    }

    @Test
    fun `round trip keeps the deletion request`() {
        val record = UploadRecord(
            "custom", "Discord", "https://cdn.example/a.png", 5,
            DeletionRequest(DeletionKind.REQUEST, DeletionMethod.DELETE, "https://discord.com/api/webhooks/1/t/messages/2"),
        )
        assertEquals(record, parse(record.toJson().toString()))
    }

    @Test
    fun `old field names are kept so older versions can read new files`() {
        val json = UploadRecord("a", "A", "https://x.example", 7).toJson()
        assertEquals(setOf("uploader", "uploaderName", "url", "at"), json.keySet())
    }

    @Test
    fun `records without a link round trip with an empty url`() {
        val record = UploadRecord("immich", "Immich", "", 9)
        assertFalse(record.hasLink)
        assertEquals(record, parse(record.toJson().toString()))
    }

    @Test
    fun `an unreadable deletion is dropped instead of the record`() {
        val record = parse("""{"uploader":"a","uploaderName":"A","url":"https://x.example","at":1,"deletion":"oops"}""")
        assertNull(record.deletion)
    }
}
