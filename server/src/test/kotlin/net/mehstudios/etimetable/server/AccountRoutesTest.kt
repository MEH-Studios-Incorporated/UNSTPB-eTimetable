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
            setBody(SignupRequest(email, password, "Ana", "Pop", "+40 712 345 678"))
        }

    private suspend fun HttpClient.token() = signup().body<SessionResponse>().token

    private suspend fun HttpClient.putProfile(token: String, profile: Profile) = put("/api/v1/me") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(profile.copy(firstName = "Ana", lastName = "Pop", phone = "0712345678"))
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
        assertEquals("Ana Pop", profile.fullName)
        assertEquals("+40 712 345 678", profile.phone)
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
    fun namesAndPhoneAreRequired() = withServer { _, client ->
        suspend fun signup(first: String, last: String, phone: String) = client.post("/api/v1/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest("ana@stud.upb.ro", "parola-buna", first, last, phone))
        }.body<ApiError>().code
        assertEquals(ErrorCodes.MISSING_NAME, signup(" ", "Pop", "0712345678"))
        assertEquals(ErrorCodes.INVALID_PHONE, signup("Ana", "Pop", "12345"))
        assertEquals(ErrorCodes.INVALID_PHONE, signup("Ana", "Pop", "call me"))
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
        assertEquals(visit.enrollment, client.get("/api/v1/me") { bearerAuth(token) }.body<Profile>().enrollment)
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
    fun aDroppedGroupLabFreesItsTime() = withServer { services, client ->
        val token = client.token()
        // another group's lab at exactly the time of 1541E/A's
        val sample = sampleDataset()
        val sameTime = sample.activities.first { it.id == "calit-l-1541A" }.copy(id = "calit-extra")
        services.datasets.replace(sample.copy(activities = sample.activities + sameTime), makeCurrent = true)
        val group = Enrollment.National("1541E", "1541E/A")

        val both = client.putProfile(token, Profile(enrollment = group, extraSlots = listOf(ExtraSlot("calit-extra"))))
        assertEquals(HttpStatusCode.Conflict, both.status)
        val swapped = Profile(
            enrollment = group,
            extraSlots = listOf(ExtraSlot("calit-extra")),
            droppedSlots = setOf("calit-l-1541A"),
        )
        assertEquals(HttpStatusCode.OK, client.putProfile(token, swapped).status)
        assertEquals(
            setOf("calit-l-1541A"),
            client.get("/api/v1/me") {
                bearerAuth(token)
            }.body<Profile>().droppedSlots,
        )

        val unknown = client.putProfile(token, Profile(enrollment = group, droppedSlots = setOf("nope")))
        assertEquals(ErrorCodes.UNKNOWN_ACTIVITY, unknown.body<ApiError>().code)
    }

    @Test
    fun classesAnImportRemovedDoNotBlockTheNextSave() = withServer { services, client ->
        val token = client.token()
        val sample = sampleDataset()
        val extra = sample.activities.first { it.id == "calit-l-1541A" }.copy(id = "calit-extra")
        services.datasets.replace(sample.copy(activities = sample.activities + extra), makeCurrent = true)
        val profile = Profile(
            enrollment = Enrollment.National("1541E", "1541E/A"),
            extraSlots = listOf(ExtraSlot("calit-extra")),
            droppedSlots = setOf("calit-l-1541A"),
        )
        assertEquals(HttpStatusCode.OK, client.putProfile(token, profile).status)

        // the next import has neither class; the phone still has both in its copy of the profile
        services.datasets.replace(
            sample.copy(
                activities = sample.activities.filterNot {
                    it.id == "calit-l-1541A"
                },
            ),
            makeCurrent = true,
        )
        val saved = client.putProfile(token, profile)
        assertEquals(HttpStatusCode.OK, saved.status)
        val stored = saved.body<Profile>()
        assertEquals(emptyList(), stored.extraSlots)
        assertEquals(emptySet(), stored.droppedSlots)

        // an id the student never had is still refused
        val unknown = client.putProfile(token, stored.copy(droppedSlots = setOf("nope")))
        assertEquals(ErrorCodes.UNKNOWN_ACTIVITY, unknown.body<ApiError>().code)
    }

    @Test
    fun aGroupAnImportRemovedDoesNotBlockOtherChanges() = withServer { services, client ->
        val token = client.token()
        val sample = sampleDataset()
        services.datasets.replace(sample, makeCurrent = true)
        val profile = Profile(enrollment = Enrollment.National("1541E", "1541E/A"))
        assertEquals(HttpStatusCode.OK, client.putProfile(token, profile).status)

        services.datasets.replace(
            sample.copy(
                groups = sample.groups.filterNot { it.id == "1541E" },
                halfGroups = sample.halfGroups.filterNot { it.groupId == "1541E" },
            ),
            makeCurrent = true,
        )
        // the same group as before: the student can still change their phone
        val phone = client.put("/api/v1/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(profile.copy(firstName = "Ana", lastName = "Pop", phone = "0798765432"))
        }
        assertEquals(HttpStatusCode.OK, phone.status)
        // a group that is not in the timetable, and was not theirs, is refused
        val other = client.putProfile(token, Profile(enrollment = Enrollment.National("9999X")))
        assertEquals(ErrorCodes.UNKNOWN_GROUP, other.body<ApiError>().code)
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
