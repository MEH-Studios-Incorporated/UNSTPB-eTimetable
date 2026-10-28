// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Talks to the eTimetable server at [baseUrl], for example `http://10.0.2.2:8080`. */
class ApiClient(engine: HttpClientEngine, private val baseUrl: String) {
    private val http = HttpClient(engine) {
        install(ContentNegotiation) {
            // fields added by newer servers must not break older apps
            json(Json { ignoreUnknownKeys = true })
        }
        expectSuccess = true
    }

    suspend fun health(): Health = http.get("${baseUrl.trimEnd('/')}/api/v1/health").body()

    fun close() = http.close()
}
