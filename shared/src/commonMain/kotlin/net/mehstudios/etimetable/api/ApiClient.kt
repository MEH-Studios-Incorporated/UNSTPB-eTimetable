// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Profile

sealed interface DatasetFetch {
    /** The copy with the ETag the app sent is still current. */
    data object NotModified : DatasetFetch

    data class Fresh(val dataset: Dataset, val etag: String?) : DatasetFetch
}

/** Talks to the eTimetable server at [baseUrl], for example `http://127.0.0.1:8080`. */
class ApiClient(engine: HttpClientEngine, private val baseUrl: String) {
    private val http = HttpClient(engine) {
        install(ContentNegotiation) { json(AppJson) }
        expectSuccess = true
    }

    private fun url(path: String) = "${baseUrl.trimEnd('/')}$path"

    suspend fun health(): Health = http.get(url("/api/v1/health")).body()

    /** The current semester's dataset, or [DatasetFetch.NotModified] when [etag] still matches. */
    suspend fun dataset(etag: String?): DatasetFetch {
        val response = http.get(url("/api/v1/dataset")) {
            // 304 is an answer here, not an error
            expectSuccess = false
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        return when {
            response.status == HttpStatusCode.NotModified -> DatasetFetch.NotModified
            response.status.value in 200..299 -> DatasetFetch.Fresh(response.body(), response.headers[HttpHeaders.ETag])
            else -> throw response.toException()
        }
    }

    /** Newest first, one page; pass the last id of a page as [before] for the next one. */
    suspend fun announcements(before: String? = null): List<Announcement> =
        http.get(url("/api/v1/announcements")) { before?.let { parameter("before", it) } }.body()

    suspend fun signup(request: SignupRequest): SessionResponse = http.post(url("/api/v1/auth/signup")) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun login(request: LoginRequest): SessionResponse = http.post(url("/api/v1/auth/login")) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    suspend fun logout(token: String) {
        http.post(url("/api/v1/auth/logout")) { bearerAuth(token) }
    }

    suspend fun profile(token: String): Profile = http.get(url("/api/v1/me")) { bearerAuth(token) }.body()

    /** Saves [profile]; a clash or an unknown id comes back as a 409 or 422 with an [ApiError]. */
    suspend fun saveProfile(token: String, profile: Profile): Profile = http.put(url("/api/v1/me")) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(profile)
    }.body()

    suspend fun deleteAccount(token: String) {
        http.delete(url("/api/v1/me")) { bearerAuth(token) }
    }

    fun close() = http.close()
}

private suspend fun HttpResponse.toException(): Exception {
    val text = bodyAsText()
    return if (status.value in 400..499) ClientRequestException(this, text) else ServerResponseException(this, text)
}
