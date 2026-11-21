package dev.deftu.screencapper.upload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretFieldsTest {
    @Test
    fun `secret header and field names are recognised`() {
        for (name in listOf("Authorization", "x-api-key", "API-Key", "token", "X-Auth-Token", "client_secret", "apikey", "userhash", "password", " Token ")) {
            assertTrue(SecretFields.isSecretName(name), name)
        }
    }

    @Test
    fun `ordinary names are not secrets`() {
        for (name in listOf("Accept", "Content-Type", "User-Agent", "file", "deviceAssetId", "payload_json", "reqtype", "filename", "")) {
            assertFalse(SecretFields.isSecretName(name), name)
        }
    }

    private fun secretsIn(url: String) = SecretFields.urlSecrets(url).map { it.name to url.substring(it.range) }

    @Test
    fun `discord webhook token is a secret`() {
        assertEquals(
            listOf("webhook.123456" to "abc-DEF_9"),
            secretsIn("https://discord.com/api/webhooks/123456/abc-DEF_9?wait=true"),
        )
        assertEquals(
            listOf("webhook.1" to "tok"),
            secretsIn("https://discord.com/api/webhooks/1/tok/messages/{json:id}?thread_id=5"),
        )
    }

    @Test
    fun `url userinfo is a secret`() {
        assertEquals(listOf("userinfo" to "me:hunter2"), secretsIn("https://me:hunter2@host.example/upload"))
    }

    @Test
    fun `secret query parameters are secrets and others are not`() {
        assertEquals(
            listOf("query.api_key" to "k1", "query.token" to "t%202"),
            secretsIn("https://host.example/up?api_key=k1&name={name}&token=t%202#frag"),
        )
    }

    @Test
    fun `plain urls have no secrets`() {
        assertTrue(secretsIn("https://photos.example/api/assets").isEmpty())
        assertTrue(secretsIn("").isEmpty())
        assertTrue(secretsIn("https://host.example/up?key=").isEmpty())
    }

    @Test
    fun `imgur delete hash is a secret`() {
        assertEquals(listOf("imgur.deletehash" to "abc123"), secretsIn("https://api.imgur.com/3/image/abc123"))
        assertTrue(secretsIn("https://i.imgur.com/abc123.png").isEmpty())
    }

    @Test
    fun `secret fields in json and form bodies are secrets`() {
        val json = """{"api_key":"k-1","id":"{json:id}","token" : "t\"2"}"""
        assertEquals(listOf("body.api_key" to "k-1", "body.token" to "t\\\"2"), SecretFields.bodySecrets(json).map { it.name to json.substring(it.range) })
        val form = "id={json:id}&password=p-3&note=hi"
        assertEquals(listOf("body.password" to "p-3"), SecretFields.bodySecrets(form).map { it.name to form.substring(it.range) })
        assertEquals("""{"api_key":"X","id":"{json:id}"}""", SecretFields.mapBody("""{"api_key":"k-1","id":"{json:id}"}""") { _, _ -> "X" })
    }
}
