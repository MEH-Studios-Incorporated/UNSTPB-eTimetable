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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
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
import net.mehstudios.etimetable.model.Event
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.events.EventFields
import net.mehstudios.etimetable.server.events.EventImages
import net.mehstudios.etimetable.server.push.PushResult
import net.mehstudios.etimetable.server.push.PushSender
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class EventRoutesTest {
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
        val browser = createClient {
            install(HttpCookies)
            install(ContentNegotiation) { json(AppJson) }
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

    private suspend fun HttpClient.saveEvent(
        url: String = "/admin/events",
        title: String = "Ziua Carierei FAIMA",
        image: ByteArray? = picture(1200, 675),
        extra: Map<String, String> = emptyMap(),
    ): HttpResponse = submitFormWithBinaryData(
        url,
        formData {
            append("title", title)
            append("start_date", "2030-11-21")
            append("start_time", "10:00")
            append("end_time", "12:00")
            append("place", "Aula BN")
            append("body", "Companii **partenere** și interviuri.")
            append("link", "https://faima.upb.ro/cariera")
            extra.forEach { (k, v) -> append(k, v) }
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

    @Test
    fun aPublishedEventReachesTheAppsWithItsPicture() = withDashboard { _, browser ->
        val saved = browser.saveEvent(extra = mapOf("notify" to "on"))
        assertEquals(HttpStatusCode.Found, saved.status, saved.bodyAsText())

        val first = browser.get("/api/v1/events")
        val event = first.body<List<Event>>().single()
        assertEquals("Ziua Carierei FAIMA", event.title)
        assertEquals("Aula BN", event.place)
        assertEquals(16f / 9, event.imageRatio, 0.01f)
        assertTrue(event.image.matches(Regex("/api/v1/images/[0-9a-f]{64}\\.jpg")))

        val etag = first.headers[HttpHeaders.ETag]!!
        val again = browser.get("/api/v1/events") { header(HttpHeaders.IfNoneMatch, etag) }
        assertEquals(HttpStatusCode.NotModified, again.status)

        val image = browser.get(event.image)
        assertEquals(HttpStatusCode.OK, image.status)
        assertEquals("image/jpeg", image.headers[HttpHeaders.ContentType])
        assertTrue("immutable" in image.headers[HttpHeaders.CacheControl].orEmpty())
        assertEquals(0xFF.toByte(), image.readRawBytes()[0])
        assertEquals(HttpStatusCode.NotFound, browser.get("/api/v1/images/nu-exista.jpg").status)
    }

    @Test
    fun theFormSaysWhatIsWrongAndKeepsThePicture() = withDashboard { services, browser ->
        val page = browser.saveEvent(title = "").bodyAsText()
        assertTrue("Titlul este obligatoriu." in page, page)
        // the picture was kept, so the form sends it back without a new upload
        val hash = Regex("name=\"image_hash\" value=\"([0-9a-f]{64})\"").find(page)!!.groupValues[1]
        val saved = browser.saveEvent(image = null, extra = mapOf("image_hash" to hash))
        assertEquals(HttpStatusCode.Found, saved.status)
        assertEquals(hash, services.events.all().single().image.substringAfterLast('/').removeSuffix(".jpg"))

        val noPicture = browser.saveEvent(image = null).bodyAsText()
        assertTrue("Alege o imagine pentru eveniment." in noPicture)
        val notAPicture = browser.saveEvent(image = "nu".toByteArray()).bodyAsText()
        assertTrue("Imaginea trebuie să fie JPEG sau PNG." in notAPicture)
        val backwards = browser.saveEvent(extra = mapOf("end_time" to "09:00")).bodyAsText()
        assertTrue("Sfârșitul trebuie să fie după început." in backwards)
    }

    @Test
    fun previewPublishesNothing() = withDashboard { services, browser ->
        val page = browser.saveEvent(extra = mapOf("preview" to "1")).bodyAsText()
        assertTrue("Așa apare în aplicație" in page)
        assertTrue("Joi, 21 noiembrie, 10:00 - 12:00 · Aula BN" in page, page)
        assertEquals(emptyList(), services.events.all())
    }

    @Test
    fun editingKeepsThePictureUnlessANewOneIsSent() = withDashboard { services, browser ->
        browser.saveEvent()
        val event = services.events.all().single()
        browser.saveEvent("/admin/events/${event.id}", title = "Ziua Carierei", image = null)
        val renamed = services.events.byId(event.id)!!
        assertEquals("Ziua Carierei", renamed.title)
        assertEquals(event.image, renamed.image)

        browser.saveEvent("/admin/events/${event.id}", image = picture(800, 800, "png"))
        val replaced = services.events.byId(event.id)!!
        assertNotEquals(event.image, replaced.image)
        assertEquals(1f, replaced.imageRatio, 0.01f)
        assertEquals(1, imageCount(services), "the old picture goes with the change")

        browser.submitForm("/admin/events/${event.id}/delete", parameters { })
        assertEquals(emptyList(), services.events.all())
        assertEquals(0, imageCount(services))
    }

    @Test
    fun theAppsGetUpcomingAndRecentEventsOnly() = runBlocking {
        val services = Services(TestPostgres.freshDatabase())
        val image = services.events.stage(EventImages.process(picture(640, 360)))
        val now = Clock.System.now()
        fun fields(title: String, start: kotlin.time.Instant) = EventFields(
            title,
            "",
            null,
            null,
            start,
            start + 2.hours,
        )
        services.events.publish(fields("Peste o săptămână", now + 7.days), image)
        services.events.publish(fields("Acum o lună", now - 30.days), image)
        services.events.publish(fields("Acum trei luni", now - 90.days), image)

        assertEquals(listOf("Acum o lună", "Peste o săptămână"), services.events.forApps().map { it.title })
        assertEquals(3, services.events.all().size)
    }

    @Test
    fun aNotificationGoesOutOnlyWhenTicked() {
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
            browser.saveEvent(title = "Fără notificare")
            browser.saveEvent(title = "Cu notificare", extra = mapOf("notify" to "on"))
            repeat(50) { if (gateway.sent.isEmpty()) delay(100) }
            val data = gateway.sent.single()
            assertEquals(PushFields.KIND_EVENT, data[PushFields.KIND])
            assertEquals("Cu notificare", data[PushFields.TITLE])
            assertEquals("Joi, 21 noiembrie, 10:00 - 12:00 · Aula BN", data[PushFields.TEXT])
        }
    }

    private suspend fun imageCount(services: Services) =
        services.db.transaction { c -> c.query("SELECT count(*) FROM event_image") { it.getInt(1) }.single() }
}
