// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiClientTest {
    @Test
    fun readsHealthAndIgnoresUnknownFields() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/health", request.url.encodedPath)
            respond(
                content = """{"status":"ok","version":"1.2.3","apiVersion":1,"addedLater":true}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = ApiClient(engine, "http://server.test/")

        assertEquals(Health(status = "ok", version = "1.2.3"), client.health())
    }
}
