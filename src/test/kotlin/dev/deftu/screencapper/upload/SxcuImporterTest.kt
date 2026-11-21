package dev.deftu.screencapper.upload

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.config.LinkMode
import dev.deftu.screencapper.config.RequestBody
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SxcuImporterTest {
    private val dir = Files.createTempDirectory("screencapper-sxcu").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun sxcu(json: String): UploaderProfile = SxcuImporter.read(File(dir, "host.sxcu").apply { writeText(json) })

    @Test
    fun `deletion url becomes an open in browser deletion`() {
        val profile = sxcu(
            """
            {
              "Version": "17.0.0",
              "Name": "Example",
              "DestinationType": "ImageUploader",
              "RequestMethod": "POST",
              "RequestURL": "https://host.example/upload",
              "Body": "MultipartFormData",
              "FileFormName": "file",
              "URL": "{json:url}",
              "DeletionURL": "{json:deletion_url}"
            }
            """.trimIndent(),
        )
        assertEquals(DeletionRequest(DeletionKind.BROWSER, DeletionMethod.GET, "{json:deletion_url}"), profile.deletion)
        assertEquals(LinkMode.LINK, profile.linkMode)
        assertEquals("url", profile.responseUrl)
        assertEquals(RequestBody.MULTIPART, profile.body)
    }

    @Test
    fun `legacy dollar syntax in the deletion url is converted`() {
        val profile = sxcu("""{"RequestURL":"https://host.example/upload","DeletionURL":"https://host.example/delete/${'$'}json:hash${'$'}"}""")
        assertEquals("https://host.example/delete/{json:hash}", profile.deletion.url)
    }

    @Test
    fun `no deletion url means no deletion`() {
        val profile = sxcu("""{"RequestURL":"https://host.example/upload","URL":"{json:url}"}""")
        assertEquals(DeletionRequest(), profile.deletion)
    }
}
