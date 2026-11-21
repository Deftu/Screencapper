package dev.deftu.screencapper.upload.integration

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LoopbackReceiver private constructor(private val server: HttpServer, private val path: String) : AutoCloseable {
    private val result = CompletableFuture<Map<String, String>>()

    val redirectUri: String get() = "http://127.0.0.1:${server.address.port}$path"

    init {
        server.createContext(path) { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery.orEmpty())
            val page = PAGE.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, page.size.toLong())
            exchange.responseBody.use { it.write(page) }
            result.complete(query)
        }
        server.executor = Executors.newSingleThreadExecutor { Thread(it, "Screencapper sign-in").apply { isDaemon = true } }
        server.start()
    }

    fun await(timeoutMinutes: Long = 5): Map<String, String> = try {
        result.get(timeoutMinutes, TimeUnit.MINUTES)
    } finally {
        close()
    }

    fun cancel() {
        result.cancel(true)
        close()
    }

    override fun close() {
        server.stop(0)
        (server.executor as? ExecutorService)?.shutdownNow()
    }

    companion object {
        private const val PAGE = "<!doctype html><meta charset=\"utf-8\"><title>Screencapper</title>" +
            "<body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\"><p>You're signed in. You can close this tab and go back to Minecraft.</p></body>"

        fun open(path: String = "/callback"): LoopbackReceiver =
            LoopbackReceiver(HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0), path)

        fun parseQuery(raw: String): Map<String, String> = raw.split('&')
            .filter { it.isNotEmpty() }
            .associate { part ->
                val split = part.indexOf('=')
                val key = if (split < 0) part else part.substring(0, split)
                val value = if (split < 0) "" else part.substring(split + 1)
                URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
            }
    }
}
