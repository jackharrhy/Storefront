package com.jackharrhy.storefront

import io.javalin.http.NotFoundResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture

class WebServerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `serves JSON and completes invalid and missing map requests`() {
        val storage = Storage(directory.resolve("storefront.db").toString())
        val app = createWebApp(storage) { id, _ ->
            if (id == 1) CompletableFuture.completedFuture("""{"world":"world","centerX":0,"centerZ":0}""")
            else CompletableFuture.failedFuture(NotFoundResponse("Map not found"))
        }.start("127.0.0.1", 0)
        try {
            HttpClient.newHttpClient().use { client ->
                fun get(path: String): HttpResponse<String> = client.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:${app.port()}$path"))
                        .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString()
                )
                val all = get("/storefronts/")
                assertEquals(200, all.statusCode())
                assertEquals("[]", all.body())
                assertTrue(all.headers().firstValue("content-type").orElse("").startsWith("application/json"))
                assertEquals(404, get("/storefronts/999").statusCode())
                assertEquals(400, get("/storefronts/invalid").statusCode())
                assertEquals(400, get("/storefronts/1/item/-1/map").statusCode())
                assertEquals(400, get("/storefronts/1/item/invalid/map").statusCode())
                assertEquals(200, get("/storefronts/1/item/0/map").statusCode())
                assertEquals(404, get("/storefronts/999/item/0/map").statusCode())
            }
        } finally {
            app.stop()
        }
    }
}
