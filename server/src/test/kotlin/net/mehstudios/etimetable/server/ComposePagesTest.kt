// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.server.admin.AuditCategory
import net.mehstudios.etimetable.server.admin.AuditFilter
import net.mehstudios.etimetable.server.events.EventFields
import net.mehstudios.etimetable.server.events.EventImages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ComposePagesTest {
    private fun withDashboard(block: suspend ApplicationTestBuilder.(Services, HttpClient) -> Unit) = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        application { module(services) }
        val browser = createClient {
            install(HttpCookies)
            sameSite()
            followRedirects = false
        }
        browser.submitForm(
            "/admin/login",
            parameters {
                append("email", "admin@faima.test")
                append("password", "parola-admin")
            },
        )
        block(services, browser)
    }

    @Test
    fun anEmptyAnnouncementListSaysWhatToDo() = withDashboard { _, browser ->
        val page = browser.get("/admin/announcements").bodyAsText()
        assertTrue("Niciun anunț publicat" in page, page)
        assertTrue("href=\"/admin/announcements/new\"" in page)
    }

    @Test
    fun theNewAnnouncementPageHasTheFormAndThePhone() = withDashboard { _, browser ->
        val response = browser.get("/admin/announcements/new")
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.bodyAsText()
        assertTrue("action=\"/admin/announcements\"" in page, page)
        assertTrue("data-compose" in page)
        assertTrue("Pe telefon" in page)
        assertTrue("Titlul anunțului" in page)
        assertTrue("id=\"pv-page\"" in page)
        assertTrue("data-preview-markdown=\"body\"" in page)
        // nothing was sent yet, so nothing is wrong yet
        assertFalse("sunt obligatorii" in page)
    }

    @Test
    fun theEditPageShowsTheAnnouncementAsTheAppWould() = withDashboard { services, browser ->
        val a = services.announcements.publish("Sala se schimbă", "Laboratorul se ține în **BN212b**.", "Secretariat")
        val page = browser.get("/admin/announcements/${a.id}").bodyAsText()
        assertTrue("Editează anunțul" in page)
        assertTrue("action=\"/admin/announcements/${a.id}\"" in page)
        assertTrue("value=\"Sala se schimbă\"" in page)
        assertTrue("<strong>BN212b</strong>" in page)
        assertTrue("Laboratorul se ține în BN212b." in page, "the list item shows the text without marks")
        assertTrue("/admin/announcements/${a.id}/delete" in page)
        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/announcements/nu-exista").status)
    }

    @Test
    fun aPreviewOfAnEditChangesNothing() = withDashboard { services, browser ->
        val a = services.announcements.publish("Sala se schimbă", "Text", "Secretariat")
        val page = browser.submitForm(
            "/admin/announcements/${a.id}",
            parameters {
                append("title", "Titlu nou")
                append("body", "Text nou")
                append("author", "Secretariat")
                append("preview", "1")
            },
        )
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue("Titlu nou" in page.bodyAsText())
        assertEquals("Sala se schimbă", services.announcements.byId(a.id)!!.title)
        assertEquals(0, services.audit.count(AuditFilter(category = AuditCategory.ANNOUNCEMENTS)))
    }

    @Test
    fun pinningKeepsThePageAndIsLogged() = withDashboard { services, browser ->
        val a = services.announcements.publish("Program", "Text", "Secretariat")
        val pinned = browser.submitForm(
            "/admin/announcements/${a.id}/pin",
            parameters {
                append("pinned", "true")
                append("back", "/admin/announcements?page=2")
            },
        )
        assertEquals("/admin/announcements?page=2", pinned.headers[HttpHeaders.Location])
        assertTrue(services.announcements.byId(a.id)!!.pinned)
        val elsewhere = browser.submitForm(
            "/admin/announcements/${a.id}/pin",
            parameters {
                append("pinned", "false")
                append("back", "https://evil.example/admin/announcements")
            },
        )
        assertEquals("/admin/announcements", elsewhere.headers[HttpHeaders.Location])
        val log = browser.get("/admin/audit?category=announcements").bodyAsText()
        assertTrue("a fixat anunțul Program" in log)
        assertTrue("nu mai fixează anunțul Program" in log)
    }

    @Test
    fun theMarkdownPreviewEscapesHtml() = withDashboard { _, browser ->
        val response = browser.submitForm(
            "/admin/preview/markdown",
            parameters { append("text", "**BN212b** <script>alert(1)</script>\n\n- grupa 1521A") },
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        val html = response.bodyAsText()
        assertEquals(
            "<p><strong>BN212b</strong> &lt;script&gt;alert(1)&lt;/script&gt;</p><ul><li>grupa 1521A</li></ul>",
            html,
        )
    }

    @Test
    fun theMarkdownPreviewNeedsASessionAndThisSite() = withDashboard { _, browser ->
        val stranger = createClient {
            sameSite()
            followRedirects = false
        }
        val signedOut = stranger.submitForm("/admin/preview/markdown", parameters { append("text", "**x**") })
        assertEquals(HttpStatusCode.Found, signedOut.status)
        assertEquals("/admin/login", signedOut.headers[HttpHeaders.Location])

        val crossSite = browser.submitForm("/admin/preview/markdown", parameters { append("text", "**x**") }) {
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, crossSite.status)
        val fetchedFromElsewhere = browser.submitForm(
            "/admin/preview/markdown",
            parameters { append("text", "**x**") },
        ) { header("Sec-Fetch-Site", "cross-site") }
        assertEquals(HttpStatusCode.Forbidden, fetchedFromElsewhere.status)
    }

    @Test
    fun eventsArePagedIntoComingAndPast() = withDashboard { services, browser ->
        val image = services.events.stage(EventImages.process(picture(640, 360)))
        val now = Clock.System.now()
        fun fields(title: String, start: kotlin.time.Instant) =
            EventFields(title, "", "Aula BN", null, start, start + 2.hours)
        runBlocking {
            (1..12).forEach { services.events.publish(fields("Viitor $it", now + it.days), image) }
            services.events.publish(fields("Trecut 1", now - 10.days), image)
            services.events.publish(fields("Trecut 2", now - 3.days), image)
        }

        val coming = browser.get("/admin/events").bodyAsText()
        assertTrue("1-10 din 12" in coming, coming)
        assertTrue(Regex("Următoare<span class=\"n\">12</span>").containsMatchIn(coming))
        assertTrue(Regex("Trecute<span class=\"n\">2</span>").containsMatchIn(coming))
        assertTrue(coming.indexOf("Viitor 1<") < coming.indexOf("Viitor 2<"), "the soonest first")
        assertFalse("Trecut 1" in coming)
        assertTrue("href=\"/admin/events/new\"" in coming)

        val second = browser.get("/admin/events?page=2").bodyAsText()
        assertTrue("11-12 din 12" in second)
        assertTrue("Viitor 12" in second)

        val past = browser.get("/admin/events?when=past").bodyAsText()
        assertTrue(past.indexOf("Trecut 2") in 0 until past.indexOf("Trecut 1"), "the latest first")
        assertFalse("Viitor 1<" in past)
    }

    @Test
    fun theEventComposePagesAskForWhatTheyNeed() = withDashboard { services, browser ->
        val fresh = browser.get("/admin/events/new").bodyAsText()
        assertTrue("enctype=\"multipart/form-data\"" in fresh)
        assertTrue(Regex("name=\"notify\"[^>]*checked").containsMatchIn(fresh), "a new event notifies by default")
        assertTrue("Titlul evenimentului" in fresh)
        assertTrue("data-preview-image" in fresh)
        assertFalse("Titlul este obligatoriu." in fresh)

        val image = services.events.stage(EventImages.process(picture(640, 360)))
        val start = Clock.System.now() + 5.days
        val event = services.events.publish(
            EventFields("Ziua Carierei", "Companii **partenere**.", "Aula BN", "https://faima.upb.ro", start, null),
            image,
        )
        val edit = browser.get("/admin/events/${event.id}").bodyAsText()
        assertTrue("Editează evenimentul" in edit)
        assertTrue("name=\"image_hash\" value=\"$image\"" in edit)
        assertFalse("name=\"notify\"" in edit, "an edit never notifies")
        assertTrue("<strong>partenere</strong>" in edit)
        assertTrue("Deschide linkul" in edit)
        assertTrue("src=\"${event.image}\"" in edit)
        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/events/nu-exista").status)
    }

    @Test
    fun aNoScriptEventPreviewPublishesNothing() = withDashboard { services, browser ->
        val page = browser.submitFormWithBinaryData(
            "/admin/events",
            formData {
                append("title", "Ziua Carierei")
                append("start_date", "2030-11-21")
                append("start_time", "10:00")
                append("notify", "on")
                append("preview", "1")
            },
        ).bodyAsText()
        assertTrue("Joi, 21 noiembrie, 10:00" in page, page)
        assertTrue("Înainte de publicare: Alege o imagine pentru eveniment." in page)
        assertEquals(emptyList(), services.events.all())
        assertEquals(0, services.audit.count(AuditFilter(category = AuditCategory.EVENTS)))
    }
}
