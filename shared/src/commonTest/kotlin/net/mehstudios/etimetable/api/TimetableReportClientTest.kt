// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TimetableReportClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val report = TimetableReportRequest("autumn-2026", "class-42", "The room should be BN 030.")

    @Test
    fun sendsAuthenticatedClassAndDescriptionAndReadsReceipt() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/timetable-reports", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("Bearer student-session", request.headers[HttpHeaders.Authorization])
            val body = assertIs<TextContent>(request.body)
            assertEquals(ContentType.Application.Json, body.contentType.withoutParameters())
            assertEquals(report, AppJson.decodeFromString<TimetableReportRequest>(body.text))
            respond("""{"id":"report-17","futureField":true}""", HttpStatusCode.Created, jsonHeaders)
        }
        val client = ApiClient(engine, "https://server.test/")
        try {
            assertEquals(TimetableReportReceipt("report-17"), client.reportTimetableProblem("student-session", report))
        } finally {
            client.close()
        }
    }

    @Test
    fun propagatesEmptyAuthenticationChallenge() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val client = ApiClient(engine, "https://server.test")
        try {
            val error = assertFailsWith<ClientRequestException> {
                client.reportTimetableProblem("expired-session", report)
            }
            assertEquals(HttpStatusCode.Unauthorized, error.response.status)
            assertEquals("", error.response.bodyAsText())
        } finally {
            client.close()
        }
    }

    @Test
    fun preservesDuplicateErrorForTheForm() = runTest {
        val engine = MockEngine {
            respond("""{"code":"report_already_open"}""", HttpStatusCode.Conflict, jsonHeaders)
        }
        val client = ApiClient(engine, "https://server.test")
        try {
            val error = assertFailsWith<ClientRequestException> {
                client.reportTimetableProblem("student-session", report)
            }
            assertEquals(HttpStatusCode.Conflict, error.response.status)
            assertEquals(ErrorCodes.REPORT_ALREADY_OPEN, decodeApiError(error.response.bodyAsText())?.code)
        } finally {
            client.close()
        }
    }

    @Test
    fun preservesRateLimitAndRetryAfterWithoutRetryingSubmission() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                """{"code":"too_many_requests"}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("120"),
                ),
            )
        }
        val client = ApiClient(engine, "https://server.test")
        try {
            val error = assertFailsWith<ClientRequestException> {
                client.reportTimetableProblem("student-session", report)
            }
            assertEquals(1, requests)
            assertEquals("120", error.response.headers[HttpHeaders.RetryAfter])
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS, decodeApiError(error.response.bodyAsText())?.code)
        } finally {
            client.close()
        }
    }

    @Test
    fun propagatesCancelledSubmission() = runTest {
        val engine = MockEngine { throw CancellationException("The form was closed") }
        val client = ApiClient(engine, "https://server.test")
        try {
            assertFailsWith<CancellationException> { client.reportTimetableProblem("student-session", report) }
        } finally {
            client.close()
        }
    }
}
