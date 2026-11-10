// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.LoginRequest
import net.mehstudios.etimetable.api.SessionResponse
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.server.admin.AdminRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminEditTest {
    private val semester = "/admin/semesters/2026-1"

    private fun withSemester(block: suspend ApplicationTestBuilder.(Services, HttpClient) -> Unit) = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        services.datasets.replace(sampleDataset(), makeCurrent = true)
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

    private suspend fun HttpClient.dataset() = get("/api/v1/dataset").body<Dataset>()

    private suspend fun HttpClient.saveActivity(id: String?, change: ParametersBuilder.() -> Unit = {}) = submitForm(
        if (id == null) "$semester/activities" else "$semester/activities/${id.encodeURLParameter()}",
        parameters {
            append("subject", "calit")
            append("type", "LAB")
            append("day", "3")
            append("start", "12:00")
            append("end", "14:00")
            append("weeks", "toate")
            append("professor", "ilie")
            append("room", "BN212b")
            append("audience", "1541E/A")
            change()
        },
    )

    @Test
    fun aTeacherForAllLabsOfASubject() = withSemester { _, browser ->
        // the sample's labs all have an assistant, so "only where empty" changes nothing
        val onlyEmpty = parameters {
            append("type", "LAB")
            append("professor", "marinescu")
            append("only_empty", "on")
        }
        browser.submitForm("$semester/subjects/calit/assign", onlyEmpty)
        assertTrue(
            browser.dataset().activities.filter { it.subjectId == "calit" && it.type.name == "LAB" }.all {
                it.professorId ==
                    "ilie"
            },
        )

        browser.submitForm(
            "$semester/subjects/calit/assign",
            parameters {
                append("type", "LAB")
                append("professor", "marinescu")
            },
        )
        val labs = browser.dataset().activities.filter { it.subjectId == "calit" && it.type.name == "LAB" }
        assertEquals(4, labs.size)
        assertTrue(labs.all { it.professorId == "marinescu" })
    }

    @Test
    fun editingAnActivityReachesTheApps() = withSemester { _, browser ->
        val before = browser.get("/api/v1/dataset").headers["ETag"]
        val saved = browser.saveActivity("calit-l-1541A") {
            set("room", "ec 104")
            set("weeks", "impare")
            set("erasmus", "on")
        }
        assertEquals(HttpStatusCode.Found, saved.status)
        val dataset = browser.dataset()
        val lab = dataset.activitiesById.getValue("calit-l-1541A")
        assertEquals("EC104", dataset.roomsById[lab.roomId]?.code)
        assertEquals(WeekRule.Odd, lab.slot.weeks)
        assertTrue(lab.openToErasmus)
        assertTrue(before != browser.get("/api/v1/dataset").headers["ETag"])
    }

    @Test
    fun wrongActivitiesAreRefusedAndChangeNothing() = withSemester { _, browser ->
        val before = browser.dataset()
        val backwards = browser.saveActivity("calit-l-1541A") { set("end", "11:00") }
        assertEquals(HttpStatusCode.UnprocessableEntity, backwards.status)
        assertTrue("după cea de început" in backwards.bodyAsText())
        val unknown = browser.saveActivity("calit-l-1541A") { set("audience", "9999X") }
        assertEquals(HttpStatusCode.UnprocessableEntity, unknown.status)
        assertTrue("Nu există grupa sau seria 9999X" in unknown.bodyAsText())
        assertEquals(before, browser.dataset())
    }

    @Test
    fun addingAndDeletingAnActivity() = withSemester { _, browser ->
        val count = browser.dataset().activities.size
        browser.saveActivity(null) {
            set("day", "5")
            set("audience", "1542E")
        }
        val added = browser.dataset().activities.single {
            it.slot.isoDay == 5 &&
                "1542E" in it.audience.groupIds &&
                it.subjectId == "calit"
        }
        assertEquals(count + 1, browser.dataset().activities.size)
        browser.submitForm("$semester/activities/${added.id.encodeURLParameter()}/delete", parameters { })
        assertEquals(count, browser.dataset().activities.size)
    }

    @Test
    fun officeHoursFromLines() = withSemester { _, browser ->
        val saved = browser.submitForm(
            "$semester/professors/marinescu",
            parameters {
                append("title", "Conf. dr.")
                append("name", "Ioana Marinescu")
                append("email", "ioana.marinescu@upb.ro")
                append("office", "EC 210")
                append("office_hours", "Joi 10:00-12:00 EC210 impare\nVineri 8-10")
            },
        )
        assertEquals(HttpStatusCode.Found, saved.status)
        val professor = browser.dataset().professorsById.getValue("marinescu")
        assertEquals("ioana.marinescu@upb.ro", professor.email)
        assertEquals(2, professor.officeHours.size)
        assertEquals(WeekRule.Odd, professor.officeHours[0].slot.weeks)
        assertEquals("ec210", professor.officeHours[0].roomId)
        assertNull(professor.officeHours[1].roomId)

        val wrong = browser.submitForm(
            "$semester/professors/marinescu",
            parameters {
                append("name", "Ioana Marinescu")
                append("office_hours", "Joi")
            },
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, wrong.status)
        assertTrue("rândul 1" in wrong.bodyAsText())
    }

    @Test
    fun aProfessorWithClassesStays() = withSemester { _, browser ->
        browser.submitForm("$semester/professors/ilie/delete", parameters { })
        assertTrue("ilie" in browser.dataset().professorsById)

        browser.submitForm(
            "$semester/professors",
            parameters {
                append("title", "As. drd.")
                append("name", "Dan Pop")
            },
        )
        assertTrue("dan-pop" in browser.dataset().professorsById)
        browser.submitForm("$semester/professors/dan-pop/delete", parameters { })
        assertTrue("dan-pop" !in browser.dataset().professorsById)
    }

    @Test
    fun subjectsAndBuildingsAreRenamed() = withSemester { _, browser ->
        browser.submitForm(
            "$semester/subjects/calit",
            parameters {
                append("name_ro", "Managementul calității")
                append("name_en", "Quality Management")
                append("code", "MC")
            },
        )
        browser.submitForm(
            "$semester/buildings/bn",
            parameters {
                append("name", "Corpul BN, Splaiul Independenței")
                append("address", "Splaiul Independenței 313")
            },
        )
        val dataset = browser.dataset()
        assertEquals("MC", dataset.subjectsById.getValue("calit").code)
        assertEquals("Quality Management", dataset.subjectsById.getValue("calit").name.en)
        assertEquals("Splaiul Independenței 313", dataset.buildingsById.getValue("bn").address)
    }

    @Test
    fun aNewStudentPasswordEndsTheirSessions() = withSemester { services, browser ->
        val token = browser.post("/api/v1/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest("ana@stud.upb.ro", "parola-buna", "Ana", "Pop", "0712345678"))
        }.body<SessionResponse>().token
        val student = services.accounts.list().single()
        assertTrue("ana@stud.upb.ro" in browser.get("/admin/students").bodyAsText())

        browser.submitForm("/admin/students/${student.id}/password", parameters { append("password", "parola-noua") })
        assertEquals(HttpStatusCode.Unauthorized, browser.get("/api/v1/me") { bearerAuth(token) }.status)
        val login = browser.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("ana@stud.upb.ro", "parola-noua"))
        }
        assertEquals(HttpStatusCode.OK, login.status)
    }

    @Test
    fun studentsArePagedAndSearchedInTheDatabase() = withSemester { services, browser ->
        // past the API's sign-up limit, so the accounts are made directly
        repeat(12) { i ->
            services.accounts.signup(SignupRequest("s$i@stud.upb.ro", "parola-buna", "Student", "Nr$i", "0712345678"))
        }
        val first = browser.get("/admin/students").bodyAsText()
        assertTrue("1-10 din 12" in first)
        val second = browser.get("/admin/students?page=2").bodyAsText()
        assertTrue("11-12 din 12" in second)
        val found = browser.get("/admin/students?q=nr1").bodyAsText()
        // nr1, nr10, nr11
        assertTrue("1-3 din 3" in found)
        // a LIKE wildcard in the search is taken literally
        assertTrue("Niciun student găsit" in browser.get("/admin/students?q=%25").bodyAsText())
    }

    @Test
    fun dashboardErrorsArePagesNotJson() = withSemester { _, browser ->
        val missing = browser.get("/admin/semesters/2026-1/subjects/nu-exista")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertTrue("Pagina nu există" in missing.bodyAsText())
        assertTrue("text/html" in missing.headers[HttpHeaders.ContentType].orEmpty())
        // the API keeps its JSON errors
        assertTrue("text/html" !in browser.get("/api/v1/me").headers[HttpHeaders.ContentType].orEmpty())
    }

    @Test
    fun theRoomsPageHasRoomsAndBuildingsInPages() = withSemester { services, browser ->
        val dataset = services.datasets.current()!!.dataset
        val rooms = browser.get("/admin/semesters/2026-1/rooms").bodyAsText()
        assertTrue("din ${dataset.rooms.size}" in rooms || dataset.rooms.size <= 10)
        val building = dataset.buildings.first()
        val buildings = browser.get("/admin/semesters/2026-1/rooms?view=buildings").bodyAsText()
        assertTrue("/admin/semesters/2026-1/buildings/${building.id}" in buildings)
        val edit = browser.get("/admin/semesters/2026-1/buildings/${building.id}").bodyAsText()
        assertTrue("Corpul ${building.code}" in edit)
        val filtered = browser.get("/admin/semesters/2026-1/rooms?building=${building.id}").bodyAsText()
        dataset.rooms.filter { it.buildingId != building.id }.forEach { assertFalse(">${it.code}</a>" in filtered) }
    }

    @Test
    fun editorsDoNotSeeStudents() = withSemester { services, browser ->
        services.admins.create("editor@faima.test", "parola-editor", AdminRole.EDITOR)
        browser.submitForm("/admin/logout", parameters { })
        browser.submitForm(
            "/admin/login",
            parameters {
                append("email", "editor@faima.test")
                append("password", "parola-editor")
            },
        )
        assertEquals(HttpStatusCode.Forbidden, browser.get("/admin/students").status)
        assertEquals(HttpStatusCode.OK, browser.get("$semester/subjects").status)
        assertTrue("Studenți" !in browser.get("$semester/subjects").bodyAsText())
    }
}
