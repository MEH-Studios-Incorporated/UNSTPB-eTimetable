// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.LoginRequest
import net.mehstudios.etimetable.api.SessionResponse
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.api.TimetableReportReceipt
import net.mehstudios.etimetable.api.TimetableReportRequest
import net.mehstudios.etimetable.model.LocalizedText
import net.mehstudios.etimetable.server.admin.AdminRole
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import net.mehstudios.etimetable.server.reports.ReportStatus
import net.mehstudios.etimetable.server.reports.TimetableReports
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimetableReportRoutesTest {
    private val endpoint = "/api/v1/timetable-reports"
    private val lab = "calit-l-1541A"

    private suspend fun HttpClient.signup(email: String = "ana@stud.upb.ro"): String = post("/api/v1/auth/signup") {
        contentType(ContentType.Application.Json)
        setBody(SignupRequest(email, "parola-buna", "Ana", "Pop", "0712345678"))
    }.body<SessionResponse>().token

    private suspend fun HttpClient.report(
        token: String?,
        activity: String = lab,
        description: String = "Sala laboratorului este greșită.",
        semester: String = "2026-1",
    ) = post(endpoint) {
        token?.let { bearerAuth(it) }
        contentType(ContentType.Application.Json)
        setBody(TimetableReportRequest(semester, activity, description))
    }

    private fun ApplicationTestBuilder.browser(): HttpClient = createClient {
        install(HttpCookies)
        install(ContentNegotiation) { json(AppJson) }
        followRedirects = false
    }

    private suspend fun HttpClient.adminLogin(email: String = "admin@faima.test") = submitForm(
        "/admin/login",
        parameters {
            append("email", email)
            append("password", "parola-admin")
        },
    )

    private suspend fun HttpClient.csrf(id: UUID): String {
        val html = get("/admin/reports/$id").bodyAsText()
        return assertNotNull(Regex("name=\"csrf\" value=\"([^\"]+)\"").find(html)?.groupValues?.get(1), html)
    }

    private suspend fun HttpClient.review(id: UUID, status: String, csrf: String) = submitForm(
        "/admin/reports/$id/review",
        parameters {
            append("status", status)
            append("csrf", csrf)
        },
    )

    @Test
    fun reportsRequireAStudentSessionAndAreNotPubliclyReadable() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        assertEquals(HttpStatusCode.Unauthorized, client.report(null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.report("invented-token").status)
        assertEquals(HttpStatusCode.MethodNotAllowed, client.get(endpoint).status)
        val browser = browser()
        assertEquals(HttpStatusCode.Found, browser.get("/admin/reports").status)
        assertEquals(HttpStatusCode.Found, browser.get("/admin/reports/${UUID.randomUUID()}").status)
    }

    @Test
    fun createsTrimmedReportWithServerClassSnapshot() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        val response = client.report(client.signup(), description = "  Sala corectă este EC 104.  ")
        assertEquals(HttpStatusCode.Created, response.status)
        val id = UUID.fromString(response.body<TimetableReportReceipt>().id)
        val report = assertNotNull(services.reports.find(id))
        assertEquals("Sala corectă este EC 104.", report.description)
        assertEquals(lab, report.snapshot.activity.id)
        assertEquals("Managementul calității", report.snapshot.subjectName)
        assertTrue("1541E/A" in report.snapshot.audience)
        assertEquals(ReportStatus.OPEN, report.status)
        assertNull(report.reviewedAt)
    }

    @Test
    fun rejectsInvalidDescriptionsAndStaleClassesWithoutCreatingReports() = withServer { services, client ->
        val token = client.signup()
        assertEquals(ErrorCodes.UNKNOWN_ACTIVITY, client.report(token).body<ApiError>().code)
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        val invalid = listOf("", "  too short  ", "x".repeat(2001), "a\u0000description", "\uD83D\uDE00".repeat(5))
        invalid.forEach { description ->
            val response = client.report(token, description = description)
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(ErrorCodes.INVALID_REPORT, response.body<ApiError>().code)
        }
        assertEquals(ErrorCodes.UNKNOWN_ACTIVITY, client.report(token, semester = "old-semester").body<ApiError>().code)
        assertEquals(ErrorCodes.UNKNOWN_ACTIVITY, client.report(token, activity = "missing").body<ApiError>().code)
        assertEquals(ErrorCodes.INVALID_REPORT, client.report(token, activity = "x".repeat(257)).body<ApiError>().code)
        assertEquals(0, services.reports.openCount())
        val malformed = client.post(endpoint) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{\"description\":123}")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals(HttpStatusCode.Created, client.report(token, description = "ș".repeat(10)).status)
        assertEquals(
            HttpStatusCode.Created,
            client.report(
                token,
                activity = sampleDataset().activities.first { it.id != lab }.id,
                description = "x".repeat(2000),
            ).status,
        )
        assertEquals(
            HttpStatusCode.Created,
            client.report(
                token,
                activity = sampleDataset().activities.last { it.id != lab }.id,
                description = "\uD83D\uDE00".repeat(10),
            ).status,
        )
    }

    @Test
    fun duplicateOpenReportsAreRejectedAcrossSessions() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        val token = client.signup()
        assertEquals(HttpStatusCode.Created, client.report(token).status)
        val second = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("ana@stud.upb.ro", "parola-buna"))
        }.body<SessionResponse>().token
        val duplicate = client.report(second, description = "Același laborator are altă sală.")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals(ErrorCodes.REPORT_ALREADY_OPEN, duplicate.body<ApiError>().code)
        assertEquals(1, services.reports.openCount())
        assertEquals(HttpStatusCode.Created, client.report(client.signup("other@stud.upb.ro")).status)
    }

    @Test
    fun quotaSurvivesRepositoryRecreationAndClosedReportsUntilTheHourExpires() = withServer { services, client ->
        val sample = sampleDataset()
        services.datasets.replace(sample, makeCurrent = true)
        val token = client.signup()
        val ids = sample.activities.take(5).map { activity ->
            val response = client.report(token, activity.id)
            assertEquals(HttpStatusCode.Created, response.status)
            UUID.fromString(response.body<TimetableReportReceipt>().id)
        }
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        val admin = services.admins.list().single()
        assertTrue(TimetableReports(services.db).review(ids.first(), ReportStatus.DISMISSED, admin.id))
        val limited = client.report(token, sample.activities[5].id)
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals(ErrorCodes.TOO_MANY_REQUESTS, limited.body<ApiError>().code)
        assertTrue(limited.headers[HttpHeaders.RetryAfter]!!.toInt() in 1..3600)
        val student = assertNotNull(services.accounts.studentFor(token))
        val restarted = TimetableReports(services.db)
        val failure = runCatching {
            restarted.submit(
                student,
                TimetableReportRequest("2026-1", sample.activities[5].id, "Sala este incorectă."),
                services.datasets.current(),
            )
        }.exceptionOrNull()
        assertTrue(failure is net.mehstudios.etimetable.server.reports.ReportLimitExceeded)
        services.db.transaction { c ->
            c.update("UPDATE timetable_report SET created_at = clock_timestamp() - interval '2 hours'")
        }
        assertEquals(HttpStatusCode.Created, client.report(token, sample.activities[5].id).status)
        assertEquals(HttpStatusCode.Created, client.report(token, sample.activities.first().id).status)
    }

    @Test
    fun concurrentSubmissionsCannotBypassTheQuota() = withServer { services, client ->
        val sample = sampleDataset()
        services.datasets.replace(sample, makeCurrent = true)
        val token = client.signup()
        val statuses = coroutineScope {
            sample.activities.take(8).map { activity -> async { client.report(token, activity.id).status } }.awaitAll()
        }
        assertEquals(5, statuses.count { it == HttpStatusCode.Created })
        assertEquals(3, statuses.count { it == HttpStatusCode.TooManyRequests })
        assertEquals(5, services.reports.openCount())
    }

    @Test
    fun concurrentDuplicatesCreateOnlyOneReport() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        val token = client.signup()
        val statuses = coroutineScope { (1..8).map { async { client.report(token).status } }.awaitAll() }
        assertEquals(1, statuses.count { it == HttpStatusCode.Created })
        assertEquals(7, statuses.count { it == HttpStatusCode.Conflict })
    }

    @Test
    fun editorCanReadEditAndResolveReportsWithCsrfProtection() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        services.admins.create("editor@faima.test", "parola-admin", AdminRole.EDITOR)
        val id = UUID.fromString(client.report(client.signup()).body<TimetableReportReceipt>().id)
        val browser = browser()
        browser.adminLogin("editor@faima.test")
        val overview = browser.get("/admin").bodyAsText()
        assertTrue("1 sesizare de orar" in overview)
        val detail = browser.get("/admin/reports/$id").bodyAsText()
        assertTrue("Ana Pop" in detail)
        assertTrue("ana@stud.upb.ro" in detail)
        assertTrue("Editează activitatea" in detail)
        assertTrue("back=%2Fadmin%2Freports%2F$id" in detail, detail)
        val csrf = browser.csrf(id)
        assertEquals(HttpStatusCode.Forbidden, browser.review(id, "fixed", "invalid").status)
        val crossSite = browser.submitForm(
            "/admin/reports/$id/review",
            parameters {
                append("status", "fixed")
                append("csrf", csrf)
            },
        ) { header(HttpHeaders.Origin, "https://other.test") }
        assertEquals(HttpStatusCode.Forbidden, crossSite.status)
        assertEquals(HttpStatusCode.UnprocessableEntity, browser.review(id, "open", csrf).status)
        val saved = browser.submitForm(
            "/admin/semesters/2026-1/activities/$lab",
            parameters {
                append("subject", "calit")
                append("type", "LAB")
                append("day", "3")
                append("start", "12:00")
                append("end", "14:00")
                append("weeks", "toate")
                append("professor", "ilie")
                append("room", "EC104")
                append("audience", "1541E/A")
                append("back", "/admin/reports/$id")
            },
        )
        assertEquals(HttpStatusCode.Found, saved.status)
        assertTrue(saved.headers[HttpHeaders.Location].orEmpty().startsWith("/admin/reports/$id?"))
        assertTrue("Activitatea a fost salvată." in browser.get(saved.headers[HttpHeaders.Location]!!).bodyAsText())
        val dataset = assertNotNull(services.datasets.current()).dataset
        assertEquals("EC104", dataset.roomsById[dataset.activitiesById[lab]?.roomId]?.code)
        assertEquals(HttpStatusCode.Found, browser.review(id, "fixed", csrf).status)
        val report = assertNotNull(services.reports.find(id))
        assertEquals(ReportStatus.FIXED, report.status)
        assertNotNull(report.reviewedAt)
        assertEquals("editor@faima.test", report.reviewedBy)
        assertTrue("Rezolvată" in browser.get("/admin/reports/$id").bodyAsText())
        assertTrue("editor@faima.test" in browser.get("/admin/reports/$id").bodyAsText())
        assertEquals(HttpStatusCode.Conflict, browser.review(id, "dismissed", csrf).status)
        assertEquals(ReportStatus.FIXED, services.reports.find(id)?.status)
        assertFalse("Managementul calității" in browser.get("/admin/reports").bodyAsText())
        assertTrue("Managementul calității" in browser.get("/admin/reports?status=closed").bodyAsText())
    }

    @Test
    fun removedClassesKeepTheirOriginalContextAndEscapeUserText() = withServer { services, client ->
        val sample = sampleDataset()
        services.datasets.replace(sample, makeCurrent = true)
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        val description = "<script>alert('wrong room')</script>\nUse EC 104 instead."
        val receipt = client.report(client.signup(), description = description).body<TimetableReportReceipt>()
        val id = UUID.fromString(receipt.id)
        services.datasets.replace(
            sample.copy(
                activities = emptyList(),
                subjects = sample.subjects.map { it.copy(name = LocalizedText("Renamed", "Renamed")) },
            ),
            makeCurrent = true,
        )
        val browser = browser()
        browser.adminLogin()
        val detail = browser.get("/admin/reports/$id").bodyAsText()
        assertTrue("Managementul calității" in detail)
        assertTrue("&lt;script&gt;" in detail)
        assertFalse("<script>" in detail)
        assertFalse("Editează activitatea" in detail)
        assertTrue("nu mai există" in detail)
        assertEquals(HttpStatusCode.Found, browser.review(id, "dismissed", browser.csrf(id)).status)
        assertEquals(ReportStatus.DISMISSED, services.reports.find(id)?.status)
    }

    @Test
    fun deletingAnAccountDeletesAllItsReports() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        val token = client.signup()
        val id = UUID.fromString(client.report(token).body<TimetableReportReceipt>().id)
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/me") { bearerAuth(token) }.status)
        assertNull(services.reports.find(id))
        assertEquals(0, services.reports.openCount())
    }

    @Test
    fun dashboardPaginatesWithoutOmittingReportRows() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        val token = client.signup()
        val id = UUID.fromString(client.report(token).body<TimetableReportReceipt>().id)
        services.db.transaction { c ->
            repeat(50) { index ->
                c.update(
                    """
                    INSERT INTO timetable_report (id, student_id, semester_id, activity_id, snapshot, description)
                    SELECT ?, student_id, semester_id, ?, snapshot, description FROM timetable_report WHERE id = ?
                    """.trimIndent(),
                    UUID.randomUUID(),
                    "page-$index",
                    id,
                )
            }
        }
        val browser = browser()
        browser.adminLogin()
        val first = browser.get("/admin/reports").bodyAsText()
        val second = browser.get("/admin/reports?page=1").bodyAsText()
        val reportLink = Regex("href=\"/admin/reports/([0-9a-f-]{36})\"")
        val firstIds = reportLink.findAll(first).map { it.groupValues[1] }.toSet()
        val secondIds = reportLink.findAll(second).map { it.groupValues[1] }.toSet()
        assertEquals(50, firstIds.size)
        assertEquals(1, secondIds.size)
        assertEquals(51, (firstIds + secondIds).size)
        assertTrue("Pagina următoare" in first)
        assertFalse("Pagina următoare" in second)
    }
}
