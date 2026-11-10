// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import net.mehstudios.etimetable.api.MAX_PUSH_TOKEN
import net.mehstudios.etimetable.api.PushPlatform
import net.mehstudios.etimetable.api.PushTokenRequest
import net.mehstudios.etimetable.api.SessionResponse
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.server.push.MAX_PUSH_TOKENS_PER_STUDENT
import net.mehstudios.etimetable.server.push.PushDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PushRoutesTest {
    private val phone = "fcm:token-of-one-phone_123"

    private suspend fun HttpClient.token(email: String) = post("/api/v1/auth/signup") {
        contentType(ContentType.Application.Json)
        setBody(SignupRequest(email, "parola-buna", "Ana", "Pop", "0712345678"))
    }.body<SessionResponse>().token

    private suspend fun HttpClient.register(token: String, push: String, platform: PushPlatform) =
        put("/api/v1/me/push-tokens/${push.encodeURLPathPart()}") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PushTokenRequest(platform))
        }

    private suspend fun HttpClient.unregister(token: String, push: String) =
        delete("/api/v1/me/push-tokens/${push.encodeURLPathPart()}") { bearerAuth(token) }

    @Test
    fun registersAndRemovesAPhone() = withServer { services, client ->
        val ana = client.token("ana@stud.upb.ro")

        val registered = client.register(ana, phone, PushPlatform.ANDROID)
        assertEquals(HttpStatusCode.NoContent, registered.status)
        assertEquals(listOf(PushDevice(phone, PushPlatform.ANDROID)), services.pushTokens.all())

        assertEquals(HttpStatusCode.NoContent, client.unregister(ana, phone).status)
        assertEquals(emptyList(), services.pushTokens.all())
    }

    @Test
    fun aPhoneMovesToTheAccountThatRegisteredItLast() = withServer { services, client ->
        val ana = client.token("ana@stud.upb.ro")
        val ion = client.token("ion@stud.upb.ro")
        client.register(ana, phone, PushPlatform.ANDROID)
        client.register(ion, phone, PushPlatform.ANDROID)

        // Ana signing out on her old phone must not take it from Ion
        client.unregister(ana, phone)

        assertEquals(1, services.pushTokens.all().size)
        val ionId = services.accounts.studentFor(ion)!!
        assertEquals(listOf(PushDevice(phone, PushPlatform.ANDROID)), services.pushTokens.of(ionId))
    }

    @Test
    fun deletingTheAccountRemovesItsPhones() = withServer { services, client ->
        val ana = client.token("ana@stud.upb.ro")
        client.register(ana, phone, PushPlatform.IOS)

        client.delete("/api/v1/me") { bearerAuth(ana) }

        assertEquals(emptyList(), services.pushTokens.all())
    }

    @Test
    fun needsASessionAndASensibleToken() = withServer { _, client ->
        val ana = client.token("ana@stud.upb.ro")

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.register("not-a-session", phone, PushPlatform.ANDROID).status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.register(ana, "x".repeat(MAX_PUSH_TOKEN + 1), PushPlatform.ANDROID).status,
        )
    }

    @Test
    fun signingOutRemovesThePhoneEvenWhenTheAppCouldNot() = withServer { services, client ->
        val ana = client.token("ana@stud.upb.ro")
        client.register(ana, phone, PushPlatform.ANDROID)

        // the app's own DELETE never arrived, as when it signs out offline
        client.post("/api/v1/auth/logout") { bearerAuth(ana) }

        assertEquals(emptyList(), services.pushTokens.all())
    }

    @Test
    fun aNewPasswordEndsTheSessionsAndTheirPhones() = withServer { services, client ->
        val ana = client.token("ana@stud.upb.ro")
        client.register(ana, phone, PushPlatform.ANDROID)

        services.accounts.setPassword(services.accounts.studentFor(ana)!!, "alta-parola-buna")

        assertEquals(emptyList(), services.pushTokens.all())
    }

    @Test
    fun oneStudentKeepsOnlyTheNewestPhones() = withServer { services, client ->
        val ana = client.token("ana@stud.upb.ro")
        val anaId = services.accounts.studentFor(ana)!!
        val tokens = (1..MAX_PUSH_TOKENS_PER_STUDENT + 2).map { "phone-$it" }
        // straight to the store, so the rate limit does not stop the test
        tokens.forEach { services.pushTokens.register(anaId, ana, it, PushPlatform.ANDROID) }

        val kept = services.pushTokens.of(anaId).map { it.token }.toSet()
        assertEquals(tokens.takeLast(MAX_PUSH_TOKENS_PER_STUDENT).toSet(), kept)
    }

    @Test
    fun registeringIsRateLimitedPerStudent() = withServer { _, client ->
        val ana = client.token("ana@stud.upb.ro")
        val replies = (1..11).map { client.register(ana, "phone-$it", PushPlatform.ANDROID) }

        assertEquals(List(10) { HttpStatusCode.NoContent }, replies.take(10).map { it.status })
        assertEquals(HttpStatusCode.TooManyRequests, replies.last().status)
        assertNotNull(replies.last().headers[HttpHeaders.RetryAfter]?.toIntOrNull())
        // another student has an allowance of their own
        val ion = client.token("ion@stud.upb.ro")
        assertEquals(HttpStatusCode.NoContent, client.register(ion, phone, PushPlatform.ANDROID).status)
    }

    @Test
    fun requestLogLeavesTheTokenOut() {
        assertEquals(
            "/api/v1/me/push-tokens/<token>",
            loggedPath("/api/v1/me/push-tokens/${phone.encodeURLPathPart()}"),
        )
        assertEquals("/api/v1/me/profile", loggedPath("/api/v1/me/profile"))
    }
}
