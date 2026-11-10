// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.PushPlatform
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.server.admin.AdminRole
import net.mehstudios.etimetable.server.admin.markdownPreview
import net.mehstudios.etimetable.server.push.ClockCheck
import net.mehstudios.etimetable.server.push.PushResult
import net.mehstudios.etimetable.server.push.PushSender
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class AdminRoutesTest {
    private fun withDashboard(
        services: Services = Services(TestPostgres.freshDatabase()),
        block: suspend ApplicationTestBuilder.(Services, HttpClient) -> Unit,
    ) = testApplication {
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        application { module(services) }
        val browser = createClient {
            install(HttpCookies)
            sameSite()
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
        val import = browser.importThroughWizard("2026-1", sheet)
        assertEquals(HttpStatusCode.Found, import.status)
        val message = java.net.URLDecoder.decode(import.headers[HttpHeaders.Location].orEmpty(), "UTF-8")
        assertTrue("9 adăugate" in message, message)

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
    fun importErrorsSaveNothing() = withDashboard { services, browser ->
        browser.login()
        browser.createSemester()
        val bad =
            "programme;year;series;audience;subject_code;subject_ro;subject_en;" +
                "type;day;start;end;weeks;professor;room;erasmus\n" +
                "BEM;2;A;;MKT;Marketing;;curs;vinerea;10:00;12:00;;;;\n"
        val draft = browser.uploadSheet("2026-1", bad).headers[HttpHeaders.Location]!!.removeSuffix("/columns")
        browser.submitForm("$draft/columns", parameters { append("keep", "1") })
        val preview = browser.get("$draft/preview").bodyAsText()
        assertTrue("Rândul 2" in preview)
        assertFalse("confirm?version=" in preview)
        assertEquals(0, services.datasets.dataset("2026-1")!!.activities.size)
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
    fun announcementListStripsMarkdownAndPages() = withDashboard { services, browser ->
        browser.login()
        services.announcements.publish("Primul", "**Atenție:** sala [EC105](https://upb.ro) se schimbă.", "Secretariat")
        repeat(9) { services.announcements.publish("Anunț $it", "Text $it", "Secretariat") }
        val first = browser.get("/admin/announcements").bodyAsText()
        assertTrue("1-10 din 10" in first, first)
        assertFalse("page=2" in first)
        services.announcements.publish("Ultimul", "Text", "Secretariat")
        val newest = browser.get("/admin/announcements").bodyAsText()
        assertTrue("1-10 din 11" in newest)
        assertTrue("Ultimul" in newest)
        assertFalse("Primul" in newest)
        val second = browser.get("/admin/announcements?page=2").bodyAsText()
        assertTrue("11-11 din 11" in second)
        assertTrue("Atenție: sala EC105 se schimbă." in second, second)
        assertFalse("**Atenție:**" in second)
    }

    @Test
    fun announcementPreviewCutsByCodePoints() {
        val text = "a".repeat(239) + "\uD83D\uDE00" + "rest"
        assertEquals("a".repeat(239) + "\uD83D\uDE00...", markdownPreview(text, 240))
        assertEquals("unu doi", markdownPreview("- unu\n- doi", 240))
    }

    @Test
    fun publishingWithAWrongClockSaysSoAndSendsNothing() {
        val sent = Collections.synchronizedList(mutableListOf<String>())
        val services = servicesWithClockAhead(sent)
        withDashboard(services) { _, browser ->
            val session = services.accounts.signup(
                SignupRequest("ana@stud.upb.ro", "parola-buna", "Ana", "Pop", "0712345678"),
            ).token
            val student = services.accounts.studentFor(session)!!
            services.pushTokens.register(student, session, "phone-1", PushPlatform.ANDROID)
            browser.login()

            val response = browser.submitForm(
                "/admin/announcements",
                parameters {
                    append("title", "Sală nouă")
                    append("body", "Laboratorul se mută.")
                    append("author", "Secretariat")
                },
            )

            val page = browser.get(response.headers[HttpHeaders.Location]!!).bodyAsText()
            assertTrue("ceasul serverului este cu 2 h înainte" in page, page)
            val published = browser.get("/api/v1/announcements").body<List<Announcement>>()
            assertEquals(listOf("Sală nouă"), published.map { it.title })
            assertEquals(emptyList(), sent)
        }
    }

    /** Push is on, but this server's clock is two hours ahead of Google's; [sent] gets tokens. */
    private fun servicesWithClockAhead(sent: MutableList<String>): Services {
        val gateway = PushSender { token, _ ->
            sent += token
            PushResult.SENT
        }
        val googleNow = Clock.System.now() - 2.hours
        val senders = mapOf(PushPlatform.ANDROID to gateway)
        return Services(TestPostgres.freshDatabase(), senders, ClockCheck({ googleNow }))
    }

    @Test
    fun editorsCannotManageAccounts() = withDashboard { services, browser ->
        services.admins.create("editor@faima.test", "parola-de-test", AdminRole.EDITOR)
        browser.login("editor@faima.test", "parola-de-test")
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

    @Test
    fun previewPublishesNothingAndEscapesHtml() = withDashboard { _, browser ->
        browser.login()
        val page = browser.submitForm(
            "/admin/announcements",
            parameters {
                append("title", "Sală nouă")
                append("body", "**BN212b** de luni <script>alert(1)</script>\n\n- grupa 1521A")
                append("author", "Secretariat")
                append("preview", "1")
            },
        ).bodyAsText()
        assertTrue("<strong>BN212b</strong>" in page, page)
        assertTrue("<li>grupa 1521A</li>" in page)
        assertTrue("&lt;script&gt;" in page)
        assertTrue("<script>alert" !in page)
        assertEquals(emptyList(), browser.get("/api/v1/announcements").body<List<Announcement>>())
    }

    @Test
    fun editingKeepsThePublicationTime() = withDashboard { _, browser ->
        browser.login()
        browser.submitForm(
            "/admin/announcements",
            parameters {
                append("title", "Sala se schimbă")
                append("body", "Laboratorul se ține în BN212b.")
                append("author", "Secretariat")
            },
        )
        val before = browser.get("/api/v1/announcements").body<List<Announcement>>().single()
        val saved = browser.submitForm(
            "/admin/announcements/${before.id}",
            parameters {
                append("title", "Sala se schimbă de luni")
                append("body", "Laboratorul se ține în BN212a.")
                append("author", "Secretariat")
                append("pinned", "on")
            },
        )
        assertEquals(HttpStatusCode.Found, saved.status)
        val after = browser.get("/api/v1/announcements").body<List<Announcement>>().single()
        assertEquals("Sala se schimbă de luni", after.title)
        assertTrue(after.pinned)
        assertEquals(before.publishedAt, after.publishedAt)
    }

    @Test
    fun overviewSaysWhenThereIsNoSemester() = withDashboard { _, browser ->
        browser.login()
        val page = browser.get("/admin")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue("Niciun semestru în aplicație" in page.bodyAsText())
    }
}
