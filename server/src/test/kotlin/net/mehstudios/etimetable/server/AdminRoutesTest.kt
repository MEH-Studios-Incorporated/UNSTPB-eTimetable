// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.server.admin.AdminRole
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminRoutesTest {
    private fun withDashboard(block: suspend ApplicationTestBuilder.(Services, HttpClient) -> Unit) = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        application { module(services) }
        val browser = createClient {
            install(HttpCookies)
            install(ContentNegotiation) { json(AppJson) }
            followRedirects = false
        }
        block(services, browser)
    }

    private suspend fun HttpClient.login(email: String = "admin@faima.test", password: String = "parola-admin") =
        submitForm(
            "/admin/login",
            parameters {
                append("email", email)
                append("password", password)
            },
        )

    private suspend fun HttpClient.createSemester(id: String = "2026-1") = submitForm(
        "/admin/semesters",
        parameters {
            append("id", id)
            append("name_ro", "Semestrul 1, 2026-2027")
            append("first_day", "2026-09-28")
            append("last_day", "2027-01-15")
            append(
                "breaks",
                "2026-12-21 2027-01-03 Vacanța de iarnă | Winter break\n2026-12-01 Ziua Națională | National Day",
            )
        },
    )

    @Test
    fun pagesNeedALogin() = withDashboard { _, browser ->
        val response = browser.get("/admin/semesters")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/admin/login", response.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.Unauthorized, browser.login(password = "gresita").status)
    }

    @Test
    fun fromEmptyToPublishedTimetable() = withDashboard { _, browser ->
        assertEquals(HttpStatusCode.Found, browser.login().status)
        assertEquals(HttpStatusCode.Found, browser.createSemester().status)

        val sheet = TimetableSheet.export(sampleDataset())
        val import = browser.submitFormWithBinaryData(
            "/admin/semesters/2026-1/import",
            formData {
                append(
                    "sheet",
                    sheet.toByteArray(),
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"orar.csv\"")
                    },
                )
            },
        )
        assertEquals(HttpStatusCode.OK, import.status, import.bodyAsText())
        assertTrue("Am importat 9 activități" in import.bodyAsText())

        // not in the app until it is made current
        assertEquals(HttpStatusCode.NotFound, browser.get("/api/v1/dataset").status)
        browser.submitForm("/admin/semesters/2026-1/current", parameters { })
        val dataset = browser.get("/api/v1/dataset").body<Dataset>()
        assertEquals(9, dataset.activities.size)
        assertEquals(2, dataset.semester.breaks.size)
        assertEquals("Winter break", dataset.semester.breaks.first().name.en)

        val week = browser.get("/admin/semesters/2026-1/groups/1541E").bodyAsText()
        assertTrue("Managementul calității" in week)
    }

    @Test
    fun importErrorsSaveNothing() = withDashboard { _, browser ->
        browser.login()
        browser.createSemester()
        val bad =
            "programme;year;series;audience;subject_code;subject_ro;subject_en;" +
                "type;day;start;end;weeks;professor;room;erasmus\n" +
                "BEM;2;A;;MKT;Marketing;;curs;vinerea;10:00;12:00;;;;\n"
        val response = browser.submitFormWithBinaryData(
            "/admin/semesters/2026-1/import",
            formData {
                append(
                    "sheet",
                    bad.toByteArray(),
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"orar.csv\"")
                    },
                )
            },
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("Rândul 2" in response.bodyAsText())
    }

    @Test
    fun announcementsReachTheApi() = withDashboard { _, browser ->
        browser.login()
        browser.submitForm(
            "/admin/announcements",
            parameters {
                append("title", "Sală nouă")
                append("body", "Laboratorul se mută.")
                append("author", "Secretariat")
                append("pinned", "on")
            },
        )
        val published = browser.get("/api/v1/announcements").body<List<Announcement>>().single()
        assertEquals("Sală nouă", published.title)
        assertTrue(published.pinned)

        browser.submitForm("/admin/announcements/${published.id}/delete", parameters { })
        assertEquals(emptyList(), browser.get("/api/v1/announcements").body<List<Announcement>>())
    }

    @Test
    fun editorsCannotManageAccounts() = withDashboard { services, browser ->
        services.admins.create("editor@faima.test", "parola-editor", AdminRole.EDITOR)
        browser.login("editor@faima.test", "parola-editor")
        assertEquals(HttpStatusCode.OK, browser.get("/admin/announcements").status)
        assertEquals(HttpStatusCode.Forbidden, browser.get("/admin/admins").status)
    }

    @Test
    fun postsFromOtherSitesAreRefused() = withDashboard { _, browser ->
        browser.login()
        val response = browser.submitForm("/admin/announcements", parameters { append("title", "x") }) {
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun theLastAdminStays() = withDashboard { services, _ ->
        val only = services.admins.list().single()
        val error = runCatching { services.admins.delete(only.id) }.exceptionOrNull()
        assertTrue(error is net.mehstudios.etimetable.server.admin.AdminFormError)
    }
}
