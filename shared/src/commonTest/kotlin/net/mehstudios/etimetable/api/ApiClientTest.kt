// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.mehstudios.etimetable.SampleData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ApiClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun readsHealthAndIgnoresUnknownFields() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/health", request.url.encodedPath)
            respond("""{"status":"ok","version":"1.2.3","apiVersion":1,"addedLater":true}""", headers = jsonHeaders)
        }
        assertEquals(Health(status = "ok", version = "1.2.3"), ApiClient(engine, "http://server.test/").health())
    }

    @Test
    fun datasetSendsTheETagAndUnderstands304() = runTest {
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.IfNoneMatch] == "\"v1\"") {
                respond("", HttpStatusCode.NotModified)
            } else {
                respond(
                    AppJson.encodeToString(SampleData.dataset),
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.ETag to listOf("\"v1\""),
                    ),
                )
            }
        }
        val client = ApiClient(engine, "http://server.test")

        val fresh = assertIs<DatasetFetch.Fresh>(client.dataset(etag = null))
        assertEquals("\"v1\"", fresh.etag)
        assertEquals(SampleData.dataset, fresh.dataset)
        assertEquals(DatasetFetch.NotModified, client.dataset(etag = "\"v1\""))
    }

    @Test
    fun serverErrorsAreThrown() = runTest {
        val engine = MockEngine { respond("down", HttpStatusCode.ServiceUnavailable) }
        assertFailsWith<io.ktor.client.plugins.ServerResponseException> {
            ApiClient(engine, "http://server.test").dataset(etag = null)
        }
    }
}
