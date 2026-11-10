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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.PushFields
import net.mehstudios.etimetable.api.PushPlatform
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Event
import net.mehstudios.etimetable.server.admin.AdminRole
import net.mehstudios.etimetable.server.admin.AuditCategory
import net.mehstudios.etimetable.server.admin.AuditFilter
import net.mehstudios.etimetable.server.admin.DraftKind
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import net.mehstudios.etimetable.server.events.EventFields
import net.mehstudios.etimetable.server.events.EventImages
import net.mehstudios.etimetable.server.push.PushResult
import net.mehstudios.etimetable.server.push.PushSender
import java.util.Collections
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class ComposeDraftsTest {
    private class Gateway : PushSender {
        val sent: MutableList<Map<String, String>> = Collections.synchronizedList(mutableListOf())

        override suspend fun send(token: String, data: Map<String, String>): PushResult {
            sent += data
            return PushResult.SENT
        }
    }

    private fun withDashboard(
        services: Services = Services(TestPostgres.freshDatabase()),
        block: suspend ApplicationTestBuilder.(Services, HttpClient) -> Unit,
    ) = testApplication {
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        application { module(services) }
        val browser = signedIn("admin@faima.test", "parola-admin")
        block(services, browser)
    }

    private suspend fun ApplicationTestBuilder.signedIn(email: String, password: String): HttpClient {
        val browser = createClient {
            install(HttpCookies)
            sameSite()
            install(ContentNegotiation) { json(AppJson) }
            followRedirects = false
        }
        browser.submitForm(
            "/admin/login",
            parameters {
                append("email", email)
                append("password", password)
            },
        )
        return browser
    }

    private suspend fun HttpClient.announcement(fields: Map<String, String>): HttpResponse =
        submitForm("/admin/announcements", parameters { fields.forEach { (k, v) -> append(k, v) } })

    private suspend fun HttpClient.event(fields: Map<String, String>, image: ByteArray? = null): HttpResponse =
        submitFormWithBinaryData(
            "/admin/events",
            formData {
                fields.forEach { (k, v) -> append(k, v) }
                if (image != null) {
                    append(
                        "image",
                        image,
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"afis.jpg\"")
                            append(HttpHeaders.ContentType, "image/jpeg")
                        },
                    )
                }
            },
        )

    /** The draft's id from the redirect after "Salvează ciorna". */
    private fun savedDraft(response: HttpResponse, list: String): String {
        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location].orEmpty()
        val match = Regex("^$list/new\\?draft=([0-9a-f-]{36})&msg=").find(location)
        assertNotNull(match, location)
        return match.groupValues[1]
    }

    private val eventFields = mapOf(
        "title" to "Ziua Carierei FAIMA",
        "start_date" to "2030-11-21",
        "start_time" to "10:00",
        "end_time" to "12:00",
        "place" to "Aula BN",
        "body" to "Companii **partenere** și interviuri.",
        "link" to "https://faima.upb.ro/cariera",
    )

    @Test
    fun aDraftNeedsATitleOrAText() = withDashboard { services, browser ->
        val empty = browser.announcement(
            mapOf(
                "title" to " ",
                "body" to "",
                "author" to "Secretariat",
                "save" to "draft",
            ),
        )
        assertEquals(HttpStatusCode.OK, empty.status)
        val page = empty.bodyAsText()
        assertTrue("Scrie cel puțin un titlu sau un text înainte să salvezi ciorna." in page, page)
        assertFalse("sunt obligatorii" in page, "the publish rules do not apply to a draft")
        assertTrue("value=\"Secretariat\"" in page, "what was typed stays in the form")

        val noEvent = browser.event(mapOf("place" to "Aula BN", "save" to "draft"), picture(640, 360)).bodyAsText()
        assertTrue("Scrie cel puțin un titlu sau un text" in noEvent)
        assertTrue(Regex("name=\"image_hash\" value=\"[0-9a-f]{64}\"").containsMatchIn(noEvent), "the picture is kept")

        assertEquals(0, services.composeDrafts.count(DraftKind.ANNOUNCEMENT))
        assertEquals(0, services.composeDrafts.count(DraftKind.EVENT))
        assertEquals(0, services.audit.count(AuditFilter()))
    }

    @Test
    fun anAnnouncementDraftKeepsEveryField() = withDashboard { services, browser ->
        val id = savedDraft(
            browser.announcement(
                mapOf(
                    "title" to "Sala se schimbă",
                    "body" to "Laboratorul se ține în **BN212b**.",
                    "author" to "Secretariat",
                    "pinned" to "on",
                    "save" to "draft",
                ),
            ),
            "/admin/announcements",
        )
        val page = browser.get("/admin/announcements/new?draft=$id&msg=Ciorna%20a%20fost%20salvat%C4%83.").bodyAsText()
        assertTrue("Ciorna a fost salvată." in page, page)
        assertTrue("Ciornă, salvată" in page)
        assertTrue("de admin@faima.test" in page)
        assertTrue("name=\"draft\" value=\"$id\"" in page)
        assertTrue("value=\"Sala se schimbă\"" in page)
        assertTrue("Laboratorul se ține în **BN212b**." in page)
        assertTrue("<strong>BN212b</strong>" in page, "the phone shows the draft too")
        assertTrue("value=\"Secretariat\"" in page)
        assertTrue(Regex("name=\"pinned\"[^>]*checked").containsMatchIn(page))
        assertTrue("Salvează ciorna" in page)
        assertTrue("/admin/announcements/drafts/$id/delete" in page)

        // saving again changes the same draft and is not logged again
        val again = browser.announcement(
            mapOf("title" to "Sala nouă", "body" to "", "author" to "", "draft" to id, "save" to "draft"),
        )
        assertEquals(id, savedDraft(again, "/admin/announcements"))
        val draft = services.composeDrafts.get(UUID.fromString(id), DraftKind.ANNOUNCEMENT)!!
        assertEquals(mapOf("title" to "Sala nouă", "body" to "", "author" to ""), draft.form)
        assertEquals(1, services.composeDrafts.count(DraftKind.ANNOUNCEMENT))
        val log = browser.get("/admin/audit?category=announcements").bodyAsText()
        assertEquals(1, Regex("a salvat ciorna anunțului Sala se schimbă").findAll(log).count())

        // an edit of a published announcement has no draft button
        val published = services.announcements.publish("Program", "Text", "Secretariat")
        assertFalse("Salvează ciorna" in browser.get("/admin/announcements/${published.id}").bodyAsText())
    }

    @Test
    fun anEventDraftKeepsEveryFieldAndThePicture() = withDashboard { services, browser ->
        val id = savedDraft(browser.event(eventFields + ("save" to "draft"), picture(1200, 675)), "/admin/events")
        val draft = services.composeDrafts.get(UUID.fromString(id), DraftKind.EVENT)!!
        val hash = assertNotNull(draft.imageHash)

        val page = browser.get("/admin/events/new?draft=$id").bodyAsText()
        eventFields.filterKeys { it != "body" }.forEach { (name, value) ->
            assertTrue(Regex("name=\"$name\"[^>]*value=\"${Regex.escape(value)}\"").containsMatchIn(page), name)
        }
        assertTrue("Companii **partenere** și interviuri." in page)
        assertTrue("name=\"image_hash\" value=\"$hash\"" in page)
        assertTrue("src=\"/api/v1/images/$hash.jpg\"" in page)
        assertFalse(Regex("name=\"notify\"[^>]*checked").containsMatchIn(page), "notify was not ticked")
        assertTrue("Joi, 21 noiembrie, 10:00 - 12:00 · Aula BN" in page)
        assertTrue("Ciornă, salvată" in page)

        // a picture that is refused still saves the rest, with the old picture
        val refused = browser.event(
            eventFields + mapOf("title" to "Ziua Carierei", "draft" to id, "image_hash" to hash, "save" to "draft"),
            "nu".toByteArray(),
        )
        assertEquals(id, savedDraft(refused, "/admin/events"))
        assertTrue("err=" in refused.headers[HttpHeaders.Location].orEmpty())
        val kept = services.composeDrafts.get(UUID.fromString(id), DraftKind.EVENT)!!
        assertEquals("Ziua Carierei", kept.title)
        assertEquals(hash, kept.imageHash)
    }

    @Test
    fun publishingFromADraftDeletesItAndNotifiesOnlyWhenTicked() {
        val gateway = Gateway()
        val services = Services(TestPostgres.freshDatabase(), mapOf(PushPlatform.ANDROID to gateway))
        runBlocking {
            val session = services.accounts.signup(
                SignupRequest("ana@stud.upb.ro", "parola-buna", "Ana", "Pop", "0712345678"),
            ).token
            services.pushTokens.register(
                services.accounts.studentFor(session)!!,
                session,
                "phone",
                PushPlatform.ANDROID,
            )
        }
        withDashboard(services) { _, browser ->
            val quiet = savedDraft(
                browser.event(eventFields + mapOf("title" to "Fără notificare", "save" to "draft"), picture(640, 360)),
                "/admin/events",
            )
            val loud = savedDraft(
                browser.event(
                    eventFields + mapOf("title" to "Cu notificare", "notify" to "on", "save" to "draft"),
                    picture(800, 450),
                ),
                "/admin/events",
            )
            assertEquals(2, services.composeDrafts.count(DraftKind.EVENT))

            for (id in listOf(quiet, loud)) {
                val draft = services.composeDrafts.get(UUID.fromString(id), DraftKind.EVENT)!!
                val published = browser.event(draft.form + mapOf("draft" to id, "image_hash" to draft.imageHash!!))
                assertEquals(HttpStatusCode.Found, published.status, published.bodyAsText())
                assertEquals(
                    "/admin/events?msg=Evenimentul%20a%20fost%20publicat.",
                    published.headers[HttpHeaders.Location],
                )
            }
            assertEquals(0, services.composeDrafts.count(DraftKind.EVENT))
            assertEquals(setOf("Fără notificare", "Cu notificare"), services.events.all().map { it.title }.toSet())
            repeat(50) { if (gateway.sent.isEmpty()) delay(100) }
            delay(200)
            assertEquals("Cu notificare", gateway.sent.single()[PushFields.TITLE])

            val announcement = savedDraft(
                browser.announcement(mapOf("title" to "Program", "body" to "Text", "save" to "draft")),
                "/admin/announcements",
            )
            val missingAuthor = browser.announcement(
                mapOf(
                    "title" to "Program",
                    "body" to "Text",
                    "draft" to announcement,
                ),
            )
            assertTrue("Titlul, textul și semnătura sunt obligatorii." in missingAuthor.bodyAsText())
            assertEquals(1, services.composeDrafts.count(DraftKind.ANNOUNCEMENT), "a refused publish keeps the draft")
            browser.announcement(
                mapOf("title" to "Program", "body" to "Text", "author" to "Secretariat", "draft" to announcement),
            )
            assertEquals(0, services.composeDrafts.count(DraftKind.ANNOUNCEMENT))
            assertEquals("Program", services.announcements.list(0, 10).single().title)

            val log = browser.get("/admin/audit").bodyAsText()
            assertTrue("a publicat evenimentul Fără notificare dintr-o ciornă" in log, log)
            assertTrue("a publicat anunțul Program dintr-o ciornă" in log)

            // the published draft's address now says it is gone
            val gone = browser.get("/admin/events/new?draft=$quiet")
            assertEquals(HttpStatusCode.Found, gone.status)
            assertTrue(gone.headers[HttpHeaders.Location].orEmpty().startsWith("/admin/events?view=drafts&err="))
        }
    }

    @Test
    fun draftsDoNotReachTheApps() = withDashboard { services, browser ->
        savedDraft(
            browser.announcement(
                mapOf(
                    "title" to "Ciornă",
                    "body" to "Text",
                    "author" to "Secretariat",
                    "save" to "draft",
                ),
            ),
            "/admin/announcements",
        )
        savedDraft(browser.event(eventFields + ("save" to "draft"), picture(640, 360)), "/admin/events")
        assertEquals(emptyList(), browser.get("/api/v1/announcements").body<List<Announcement>>())
        assertEquals(emptyList(), browser.get("/api/v1/events").body<List<Event>>())
        assertEquals(0, services.announcements.count())
        assertEquals(emptyList(), services.events.all())
    }

    @Test
    fun aDraftsPictureSurvivesTheCleanup() = withDashboard { services, browser ->
        val id = savedDraft(browser.event(eventFields + ("save" to "draft"), picture(640, 360)), "/admin/events")
        val hash = services.composeDrafts.get(UUID.fromString(id), DraftKind.EVENT)!!.imageHash!!
        val abandoned = services.events.stage(EventImages.process(picture(300, 300)))
        services.db.transaction { c -> c.update("UPDATE event_image SET created_at = now() - interval '2 days'") }

        // publishing any event clears pictures nobody uses
        val fresh = services.events.stage(EventImages.process(picture(400, 300)))
        val start = Clock.System.now() + 3.days
        val event = services.events.publish(EventFields("Altul", "", null, null, start, null), fresh)
        assertNull(services.events.imageSize(abandoned))
        assertNotNull(services.events.imageSize(hash))

        // nor does an event that used the same picture and is deleted take it from the draft
        services.events.update(event.id, EventFields("Altul", "", null, null, start, null), hash)
        assertNull(services.events.imageSize(fresh), "the picture the event dropped goes")
        services.events.delete(event.id)
        assertNotNull(services.events.imageSize(hash))
        assertTrue("src=\"/api/v1/images/$hash.jpg\"" in browser.get("/admin/events/new?draft=$id").bodyAsText())
    }

    @Test
    fun theCiorneTabListsEveryonesDraftsAndDeletes() = withDashboard { services, browser ->
        services.admins.create("editor@faima.test", "parola-de-test", AdminRole.EDITOR)
        val editor = signedIn("editor@faima.test", "parola-de-test")
        val mine = savedDraft(
            browser.announcement(mapOf("title" to "Sesiunea de restanțe", "body" to "Programul", "save" to "draft")),
            "/admin/announcements",
        )
        savedDraft(editor.announcement(mapOf("body" to "Doar textul", "save" to "draft")), "/admin/announcements")
        services.announcements.publish("Publicat", "Text", "Secretariat")

        val published = browser.get("/admin/announcements").bodyAsText()
        assertTrue(Regex("Publicate<span class=\"n\">1</span>").containsMatchIn(published), published)
        assertTrue(Regex("Ciorne<span class=\"n\">2</span>").containsMatchIn(published))
        assertFalse("Sesiunea de restanțe" in published)

        // an editor sees and can continue an administrator's draft
        val tab = editor.get("/admin/announcements?view=drafts").bodyAsText()
        assertTrue("Sesiunea de restanțe" in tab, tab)
        assertTrue("(fără titlu)" in tab)
        assertTrue("admin@faima.test" in tab)
        assertTrue("editor@faima.test" in tab)
        assertTrue("href=\"/admin/announcements/new?draft=$mine\"" in tab)
        assertFalse("Publicat<" in tab)
        assertTrue("Ștergi ciorna?" in tab)
        assertEquals(HttpStatusCode.OK, editor.get("/admin/announcements/new?draft=$mine").status)

        val deleted = editor.submitForm("/admin/announcements/drafts/$mine/delete", parameters { })
        assertEquals(HttpStatusCode.Found, deleted.status)
        assertTrue(deleted.headers[HttpHeaders.Location].orEmpty().startsWith("/admin/announcements?view=drafts&msg="))
        assertEquals(1, services.composeDrafts.count(DraftKind.ANNOUNCEMENT))
        val log = services.audit.page(AuditFilter(category = AuditCategory.ANNOUNCEMENTS), 0, 10)
        assertTrue(
            log.any {
                it.adminEmail == "editor@faima.test" &&
                    it.summary == "a salvat ciorna unui anunț fără titlu"
            },
        )
        assertTrue(
            log.any {
                it.adminEmail == "editor@faima.test" &&
                    it.summary == "a șters ciorna anunțului Sesiunea de restanțe"
            },
        )

        // a draft of the other kind is not found here
        val event = savedDraft(browser.event(eventFields + ("save" to "draft")), "/admin/events")
        val events = browser.get("/admin/events?view=drafts").bodyAsText()
        assertTrue(Regex("Ciorne<span class=\"n\">1</span>").containsMatchIn(events))
        assertTrue("Ziua Carierei FAIMA" in events)
        assertTrue("Joi, 21 noiembrie, 10:00 - 12:00" in events)
        assertEquals(HttpStatusCode.Found, browser.get("/admin/announcements/new?draft=$event").status)
        browser.submitForm("/admin/announcements/drafts/$event/delete", parameters { })
        assertEquals(1, services.composeDrafts.count(DraftKind.EVENT))
        browser.submitForm("/admin/events/drafts/$event/delete", parameters { })
        assertTrue("Nicio ciornă" in browser.get("/admin/events?view=drafts").bodyAsText())
    }
}
