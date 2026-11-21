package dev.deftu.screencapper.screenshot

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.upload.DeletionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedUploadsTest {
    private fun immich(asset: String, key: String = "key") = DeletionRequest(
        DeletionKind.REQUEST,
        DeletionMethod.DELETE,
        "https://photos.example/api/assets",
        listOf("x-api-key" to key),
        """{"ids":["$asset"]}""",
    )

    private fun record(at: Long, deletion: DeletionRequest?) = UploadRecord("immich", "Immich", "", at, deletion)

    @Test
    fun `an immich duplicate in another screenshot shares the remote asset`() {
        val first = record(1, immich("asset-1"))
        val second = record(2, immich("asset-1"))
        val history = mapOf("a.png" to listOf(first), "b.png" to listOf(second))
        assertEquals(listOf(UploadRef("b.png", second)), SharedUploads.others(history, "a.png", first))
        assertEquals(listOf(UploadRef("a.png", first)), SharedUploads.others(history, "b.png", second))
    }

    @Test
    fun `a different asset is not shared`() {
        val first = record(1, immich("asset-1"))
        val second = record(2, immich("asset-2"))
        val history = mapOf("a.png" to listOf(first), "b.png" to listOf(second))
        assertTrue(SharedUploads.others(history, "a.png", first).isEmpty())
    }

    @Test
    fun `headers do not decide the target`() {
        val first = record(1, immich("asset-1", key = "old"))
        val second = record(2, immich("asset-1", key = "new"))
        val history = mapOf("a.png" to listOf(first, second))
        assertEquals(listOf(UploadRef("a.png", second)), SharedUploads.others(history, "a.png", first))
    }

    @Test
    fun `method and url are part of the target`() {
        val first = record(1, immich("asset-1"))
        val post = record(2, immich("asset-1").copy(method = DeletionMethod.POST))
        val elsewhere = record(3, immich("asset-1").copy(url = "https://other.example/api/assets"))
        val history = mapOf("a.png" to listOf(first, post, elsewhere))
        assertTrue(SharedUploads.others(history, "a.png", first).isEmpty())
    }

    @Test
    fun `records without a deletion request never share`() {
        val first = record(1, null)
        val second = record(2, null)
        val history = mapOf("a.png" to listOf(first), "b.png" to listOf(second))
        assertTrue(SharedUploads.others(history, "a.png", first).isEmpty())
    }

    @Test
    fun `only the record itself is excluded`() {
        val first = record(1, immich("asset-1"))
        val history = mapOf("a.png" to listOf(first, first))
        assertEquals(listOf(UploadRef("a.png", first)), SharedUploads.others(history, "a.png", first))
    }

    @Test
    fun `describes who still uses the upload`() {
        val one = record(1, immich("asset-1"))
        assertNull(SharedUploads.describe(emptyList(), "a.png"))
        assertEquals("1 other screenshot", SharedUploads.describe(listOf(UploadRef("b.png", one)), "a.png"))
        assertEquals("2 other screenshots", SharedUploads.describe(listOf(UploadRef("b.png", one), UploadRef("c.png", one), UploadRef("c.png", one)), "a.png"))
        assertEquals("another upload of this screenshot", SharedUploads.describe(listOf(UploadRef("a.png", one)), "a.png"))
        assertEquals("2 other uploads of this screenshot", SharedUploads.describe(listOf(UploadRef("a.png", one), UploadRef("a.png", one)), "a.png"))
        assertEquals("1 other screenshot", SharedUploads.describe(listOf(UploadRef("a.png", one), UploadRef("b.png", one)), "a.png"))
    }
}
