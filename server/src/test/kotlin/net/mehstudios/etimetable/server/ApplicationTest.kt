// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import net.mehstudios.etimetable.api.Health
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun healthAnswersOk() = testApplication {
        application { module() }

        val response = client.get("/api/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", Json.decodeFromString<Health>(response.bodyAsText()).status)
    }

    @Test
    fun portComesFromTheEnvironment() {
        assertEquals(9000, ServerConfig.fromEnvironment(mapOf("ETT_PORT" to "9000")).port)
        assertEquals(8080, ServerConfig.fromEnvironment(emptyMap()).port)
    }
}
