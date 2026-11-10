// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.push

import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * A status and a body, all the push code needs from an HTTP answer. [retryAfter] is the
 * Retry-After header, sent with 429 and 503.
 */
data class HttpReply(val status: Int, val body: String, val retryAfter: String? = null)

/** One HTTP POST; the push gateways are called through it so tests can answer instead. */
fun interface HttpPost {
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpReply
}

/** The JDK's own client, so push needs no extra library. */
class JdkHttpPost(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : HttpPost {
    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpReply {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        val retryAfter = response.headers().firstValue("Retry-After").orElse(null)
        return HttpReply(response.statusCode(), response.body(), retryAfter)
    }
}
