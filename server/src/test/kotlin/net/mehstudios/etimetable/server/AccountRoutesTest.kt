// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.LocalDate
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.LoginRequest
import net.mehstudios.etimetable.api.SessionResponse
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.ExtraSlot
import net.mehstudios.etimetable.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountRoutesTest {
    private suspend fun HttpClient.signup(email: String = "ana@stud.upb.ro", password: String = "parola-buna") =
        post("/api/v1/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(email, password, "Ana"))
        }

    private suspend fun HttpClient.token() = signup().body<SessionResponse>().token

    private suspend fun HttpClient.putProfile(token: String, profile: Profile) = put("/api/v1/me") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(profile)
    }

    @Test
    fun signupLoginAndProfile() = withServer { _, client ->
        assertEquals(HttpStatusCode.Created, client.signup().status)

        val login = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("ANA@stud.upb.ro ", "parola-buna"))
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val token = login.body<SessionResponse>().token

        val profile = client.get("/api/v1/me") { bearerAuth(token) }.body<Profile>()
        assertEquals("Ana", profile.displayName)
    }

    @Test
    fun rejectsBadSignups() = withServer { _, client ->
        client.signup()
        assertEquals(ErrorCodes.EMAIL_TAKEN, client.signup(email = "Ana@Stud.upb.ro").body<ApiError>().code)
        assertEquals(ErrorCodes.INVALID_EMAIL, client.signup(email = "not-an-email").body<ApiError>().code)
        assertEquals(
            ErrorCodes.WEAK_PASSWORD,
            client.signup(email = "b@stud.upb.ro", password = "short").body<ApiError>().code,
        )
    }

    @Test
    fun wrongPasswordAndMissingToken() = withServer { _, client ->
        client.signup()
        val wrong = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("ana@stud.upb.ro", "parola-gresita"))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth("made-up") }.status)
    }

    @Test
    fun picksThatFitAreAccepted() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        val token = client.token()

        // the two halves' labs follow each other on Wednesday (12:00-14:00, 14:00-16:00)
        val backToBack = Profile(enrollment = Enrollment.Erasmus(setOf("calit-l-1541A", "calit-l-1541B")))
        assertEquals(HttpStatusCode.OK, client.putProfile(token, backToBack).status)

        // 1541E's project runs in odd weeks and 1542E's in even weeks at the same time
        val otherParity = Profile(
            enrollment = Enrollment.National("1541E", "1541E/A"),
            extraSlots = listOf(ExtraSlot("mkt-p-1542")),
        )
        assertEquals(HttpStatusCode.OK, client.putProfile(token, otherParity).status)

        // a single visit to the other half's lab, right after the student's own
        val visit = Profile(
            enrollment = Enrollment.National("1541E", "1541E/A"),
            extraSlots = listOf(ExtraSlot("calit-l-1541B", LocalDate(2026, 10, 28))),
        )
        assertEquals(HttpStatusCode.OK, client.putProfile(token, visit).status)
        assertEquals(visit, client.get("/api/v1/me") { bearerAuth(token) }.body<Profile>())
    }

    @Test
    fun clashesAndUnknownIdsAreRefused() = withServer { services, client ->
        val token = client.token()

        // a second lab at exactly the same time as 1541E/A's
        val sample = sampleDataset()
        val sameTime = sample.activities.first { it.id == "calit-l-1541A" }.copy(id = "calit-extra")
        services.datasets.replace(sample.copy(activities = sample.activities + sameTime), makeCurrent = true)

        val clash = client.putProfile(
            token,
            Profile(enrollment = Enrollment.Erasmus(setOf("calit-l-1541A", "calit-extra"))),
        )
        assertEquals(HttpStatusCode.Conflict, clash.status)
        val error = clash.body<ApiError>()
        assertEquals(ErrorCodes.CLASH, error.code)
        assertEquals(setOf("calit-l-1541A", "calit-extra"), error.details.toSet())

        val unknown = client.putProfile(token, Profile(enrollment = Enrollment.Erasmus(setOf("nope"))))
        assertEquals(ErrorCodes.UNKNOWN_ACTIVITY, unknown.body<ApiError>().code)
        val wrongHalf = client.putProfile(token, Profile(enrollment = Enrollment.National("1541E", "1542E/A")))
        assertEquals(ErrorCodes.UNKNOWN_GROUP, wrongHalf.body<ApiError>().code)
    }

    @Test
    fun logoutAndDelete() = withServer { _, client ->
        val token = client.token()
        assertEquals(HttpStatusCode.NoContent, client.post("/api/v1/auth/logout") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(token) }.status)

        val second = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("ana@stud.upb.ro", "parola-buna"))
        }.body<SessionResponse>().token
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/me") { bearerAuth(second) }.status)
        val again = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("ana@stud.upb.ro", "parola-buna"))
        }
        assertEquals(HttpStatusCode.Unauthorized, again.status)
    }
}
