package dev.deftu.screencapper.upload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionTest {
    @Test
    fun `secret urls keep only their host`() {
        val url = "https://discord.com/api/webhooks/1/abcdefTOKEN?wait=true"
        assertEquals("Illegal character in https://discord.com/***", Redaction.redact("Illegal character in $url", listOf(url)))
    }

    @Test
    fun `secret values are masked`() {
        assertEquals("bad key ***", Redaction.redact("bad key my-api-key-123", listOf("my-api-key-123")))
    }

    @Test
    fun `webhook tokens are masked even when not listed`() {
        val out = Redaction.redact("failed at https://discord.com/api/webhooks/123/zzzTOKENzzz/messages/9", emptyList())
        assertFalse("zzzTOKENzzz" in out)
    }

    @Test
    fun `short values are not treated as secrets`() {
        assertEquals("POST failed", Redaction.redact("POST failed", listOf("POST", "")))
    }

    @Test
    fun `url userinfo is masked even when not listed`() {
        val out = Redaction.redact("Connection refused: https://me:hunter2pass@host.example/up", emptyList())
        assertFalse("hunter2pass" in out)
        assertTrue("host.example/up" in out)
    }

    @Test
    fun `secret query parameters are masked even when not listed`() {
        val out = Redaction.redact("GET https://host.example/up?name=shot&api_key=qsecret99&token=tsecret99 failed", emptyList())
        assertEquals("GET https://host.example/up?name=shot&api_key=***&token=*** failed", out)
    }

    @Test
    fun `secret headers in text are masked even when not listed`() {
        assertEquals("sent Authorization: Bearer *** and Accept: image/png", Redaction.redact("sent Authorization: Bearer abc123xyz and Accept: image/png", emptyList()))
        assertFalse("abc123" in Redaction.redact("x-api-key=abc123 rejected", emptyList()))
        assertFalse("abc123" in Redaction.redact("""{"api_key":"abc123","ok":false}""", emptyList()))
        assertTrue("ok" in Redaction.redact("""{"api_key":"abc123","ok":false}""", emptyList()))
    }

    @Test
    fun `secret references and sealed values are left as they are`() {
        val text = "url https://h.example/{secret:p:header.authorization} enc:v1:AAAA"
        assertEquals(text, Redaction.redact(text, emptyList()))
    }

    @Test
    fun `every secret of a profile is masked`() {
        val profile = UploaderProfile(
            url = "https://host.example/up?key=urlsecret1",
            headers = listOf("Authorization" to "Bearer headersecret1"),
            formFields = listOf("userhash" to "fieldsecret1"),
        )
        val out = Redaction.redact("headersecret1 fieldsecret1 urlsecret1", Redaction.secretsOf(profile))
        for (secret in listOf("headersecret1", "fieldsecret1", "urlsecret1")) assertFalse(secret in out, secret)
    }
}
