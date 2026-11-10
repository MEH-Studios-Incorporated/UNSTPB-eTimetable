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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.server.admin.Admin
import net.mehstudios.etimetable.server.admin.AdminRole
import net.mehstudios.etimetable.server.admin.AuditFilter
import net.mehstudios.etimetable.server.admin.ChangeKind
import net.mehstudios.etimetable.server.admin.SCOPE_TIMETABLE_WRITE
import net.mehstudios.etimetable.server.admin.TimetableImportReport
import net.mehstudios.etimetable.server.admin.columnLetter
import net.mehstudios.etimetable.server.admin.diffTimetables
import net.mehstudios.etimetable.server.timetable.ColumnMatch
import net.mehstudios.etimetable.server.timetable.Csv
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** Sends [csv] as the file of the import wizard's first step. */
internal suspend fun HttpClient.uploadSheet(semesterId: String, csv: String): HttpResponse = submitFormWithBinaryData(
    "/admin/semesters/$semesterId/import",
    formData {
        append(
            "sheet",
            csv.toByteArray(),
            Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"orar.csv\"") },
        )
    },
)

/** Runs every step of the wizard with the columns it found; returns the confirmation's answer. */
internal suspend fun HttpClient.importThroughWizard(semesterId: String, csv: String): HttpResponse {
    val draft = uploadSheet(semesterId, csv).headers[HttpHeaders.Location]!!.removeSuffix("/columns")
    submitForm("$draft/columns", parameters { append("keep", "1") })
    val preview = get("$draft/preview").bodyAsText()
    val version = assertNotNull(Regex("confirm\\?version=(\\d+)").find(preview), preview).groupValues[1]
    return submitForm(
        "$draft/confirm",
        parameters {
            append("version", version)
            append("checked", "on")
        },
    )
}

class ImportWizardTest {
    private fun withDashboard(block: suspend ApplicationTestBuilder.(Services, HttpClient) -> Unit) = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        application { module(services) }
        val browser = createClient {
            install(HttpCookies)
            install(ContentNegotiation) { json(AppJson) }
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

    /** The sample timetable with one room changed, one class removed and one added. */
    private fun editedSheet(): String {
        val rows = Csv.parse(TimetableSheet.export(sampleDataset())).map { it.toMutableList() }.toMutableList()
        val col = TimetableSheet.COLUMNS::indexOf
        rows.first { it[col("id")] == "micro-c" }[col("room")] = "EC210"
        rows.removeAll { it[col("id")] == "contab-s" }
        val copy = rows.first { it[col("id")] == "calit-c" }.toMutableList()
        copy[col("id")] = "calit-s-new"
        copy[col("type")] = "seminar"
        copy[col("day")] = "vineri"
        rows.add(rows.indexOfFirst { it[col("id")] == "calit-c" } + 1, copy)
        return Csv.write(rows)
    }

    /** The sample timetable with an unknown day on the row of an existing class. */
    private fun sheetWithError(): String {
        val rows = Csv.parse(TimetableSheet.export(sampleDataset())).map { it.toMutableList() }.toMutableList()
        rows.first { it[0] == "micro-c" }[TimetableSheet.COLUMNS.indexOf("day")] = "vinerea"
        return Csv.write(rows)
    }

    private fun count(page: String, label: String): Int? =
        Regex("$label\\s*</span>\\s*<span class=\"count tnum\">(\\d+)</span>").find(page)?.groupValues?.get(1)?.toInt()

    private suspend fun HttpClient.draftFor(csv: String): String {
        val upload = uploadSheet("2026-1", csv)
        assertEquals(HttpStatusCode.Found, upload.status)
        val location = upload.headers[HttpHeaders.Location].orEmpty()
        assertTrue(Regex("/admin/semesters/2026-1/import/[0-9a-f-]{36}/columns").matches(location), location)
        return location.removeSuffix("/columns")
    }

    @Test
    fun theWizardShowsTheDifferenceAndSavesOnlyOnConfirmation() = withDashboard { services, browser ->
        val before = services.datasets.current()!!.dataset
        val draft = browser.draftFor(editedSheet())

        val columns = browser.get("$draft/columns").bodyAsText()
        assertTrue("toate cele obligatorii" in columns, columns)
        assertTrue("Primele rânduri" in columns)
        assertEquals(
            HttpStatusCode.Found,
            browser.submitForm(
                "$draft/columns",
                parameters {
                    append("keep", "1")
                },
            ).status,
        )

        val preview = browser.get("$draft/preview").bodyAsText()
        assertEquals(1, count(preview, "Adăugate"))
        assertEquals(1, count(preview, "Modificate"))
        assertEquals(1, count(preview, "Eliminate"))
        assertEquals(0, count(preview, "Rânduri cu erori"))
        assertTrue(Regex("<s>\\s*AN015\\s*</s>\\s*<mark>\\s*EC210\\s*</mark>").containsMatchIn(preview), preview)
        assertTrue("7 activități rămân neschimbate" in preview, preview)
        val removed = browser.get("$draft/preview?filter=removed").bodyAsText()
        assertTrue("Eliminată" in removed && "Adăugată" !in removed)
        // nothing is saved by looking
        assertEquals(before, services.datasets.current()!!.dataset)

        val confirmLink = assertNotNull(Regex("href=\"([^\"]*/confirm\\?version=(\\d+))\"").find(preview))
        val version = confirmLink.groupValues[2]
        assertTrue("Am verificat diferențele" in browser.get(confirmLink.groupValues[1]).bodyAsText())

        val unchecked = browser.submitForm("$draft/confirm", parameters { append("version", version) })
        assertEquals(HttpStatusCode.Found, unchecked.status)
        assertTrue("err=" in unchecked.headers[HttpHeaders.Location].orEmpty())
        assertEquals(before, services.datasets.current()!!.dataset)

        val confirmed = browser.submitForm(
            "$draft/confirm",
            parameters {
                append("version", version)
                append("checked", "on")
            },
        )
        assertEquals(HttpStatusCode.Found, confirmed.status)
        assertTrue(confirmed.headers[HttpHeaders.Location].orEmpty().startsWith("/admin/semesters/2026-1?msg="))
        val after = services.datasets.current()!!.dataset
        assertEquals("EC210", after.roomsById[after.activitiesById.getValue("micro-c").roomId]?.code)
        assertNull(after.activitiesById["contab-s"])
        assertNotNull(after.activitiesById["calit-s-new"])
        val entry = services.audit.page(AuditFilter(), 0, 1).single()
        assertEquals("admin@faima.test", entry.adminEmail)
        assertTrue("1 adăugată, 1 modificată, 1 eliminată" in entry.summary, entry.summary)
        // the draft is gone once used
        val gone = browser.get("$draft/preview")
        assertEquals(HttpStatusCode.Found, gone.status)
        assertTrue(gone.headers[HttpHeaders.Location].orEmpty().startsWith("/admin/semesters/2026-1/import?err="))
    }

    @Test
    fun aConfirmationAfterTheTimetableChangedIsRefused() = withDashboard { services, browser ->
        val draft = browser.draftFor(editedSheet())
        browser.submitForm("$draft/columns", parameters { append("keep", "1") })
        val preview = browser.get("$draft/preview").bodyAsText()
        val version = assertNotNull(Regex("confirm\\?version=(\\d+)").find(preview)).groupValues[1]

        // someone edits the semester meanwhile
        val edited = services.datasets.current()!!.dataset
        services.datasets.saveSemester(edited.semester)
        assertNotEquals(version.toLong(), services.datasets.version("2026-1"))

        val page = browser.get("$draft/confirm?version=$version").bodyAsText()
        assertTrue("s-a schimbat după ce ai deschis previzualizarea" in page, page)
        val stale = browser.submitForm(
            "$draft/confirm",
            parameters {
                append("version", version)
                append("checked", "on")
            },
        )
        assertEquals(HttpStatusCode.Found, stale.status)
        assertTrue(stale.headers[HttpHeaders.Location].orEmpty().startsWith("$draft/preview?err="))
        assertEquals(edited, services.datasets.current()!!.dataset)
    }

    @Test
    fun aFileWithErrorsCannotBeConfirmed() = withDashboard { services, browser ->
        val before = services.datasets.current()!!.dataset
        val draft = browser.draftFor(sheetWithError())
        browser.submitForm("$draft/columns", parameters { append("keep", "1") })
        val preview = browser.get("$draft/preview").bodyAsText()
        assertEquals(1, count(preview, "Rânduri cu erori"))
        assertTrue("Rândul 2: zi necunoscută" in preview, preview)
        // the class whose row could not be read is not shown as removed
        assertEquals(0, count(preview, "Eliminate"))
        assertFalse("confirm?version=" in preview)
        assertTrue("Încarcă fișierul corectat" in preview)

        val version = services.datasets.version("2026-1")!!
        val page = browser.get("$draft/confirm?version=$version")
        assertEquals("$draft/preview", page.headers[HttpHeaders.Location])
        val refused = browser.submitForm(
            "$draft/confirm",
            parameters {
                append("version", version.toString())
                append("checked", "on")
            },
        )
        assertTrue(refused.headers[HttpHeaders.Location].orEmpty().startsWith("$draft/preview?err="))
        assertEquals(before, services.datasets.current()!!.dataset)
    }

    @Test
    fun columnsAreMatchedByNameWithoutCaseOrDiacritics() {
        val header = listOf("Zi", "Ora  început", "SFÂRȘIT", "Sală", "Cod_Materie", "programme", "Observații")
        val mapping = ColumnMatch.detect(header)
        assertEquals(
            mapOf("programme" to 5, "subject_code" to 4, "day" to 0, "start" to 1, "end" to 2, "room" to 3),
            mapping,
        )
        assertTrue("year" in ColumnMatch.missing(mapping))
        assertFalse("id" in ColumnMatch.missing(mapping))
        // a header cell is used once, by the first column that names it
        assertEquals(mapOf("day" to 0), ColumnMatch.detect(listOf("ziua", "zi")).filterKeys { it == "day" })
        assertEquals(
            listOf(listOf("programme", "day"), listOf("BEM", "luni")),
            ColumnMatch.apply(
                listOf(listOf("Zi", "x", "Program"), listOf("luni", "?", "BEM")),
                mapOf(
                    "day" to 0,
                    "programme" to 2,
                ),
            ),
        )
        assertEquals(listOf("A", "Z", "AA", "AB"), listOf(0, 25, 26, 27).map(::columnLetter))
    }

    @Test
    fun anUnknownColumnIsChosenByHand() = withDashboard { services, browser ->
        val rows = Csv.parse(TimetableSheet.export(sampleDataset())).toMutableList()
        val subjectCode = TimetableSheet.COLUMNS.indexOf("subject_code")
        rows[0] = rows[0].toMutableList().also { it[subjectCode] = "Disciplina (cod)" }
        val draft = browser.draftFor(Csv.write(rows))

        val columns = browser.get("$draft/columns").bodyAsText()
        assertTrue("Lipsește, obligatorie" in columns)
        assertFalse("toate cele obligatorii" in columns)
        // the preview needs every required column
        assertEquals("$draft/columns", browser.get("$draft/preview").headers[HttpHeaders.Location])

        fun mapping(extra: Pair<String, Int>?) = parameters {
            TimetableSheet.COLUMNS.forEachIndexed { i, column ->
                if (column != "subject_code") append("col_$column", i.toString())
            }
            extra?.let { append("col_${it.first}", it.second.toString()) }
        }
        val twice = browser.submitForm("$draft/columns", mapping("subject_code" to 0))
        assertTrue("err=" in twice.headers[HttpHeaders.Location].orEmpty())
        val chosen = browser.submitForm("$draft/columns", mapping("subject_code" to subjectCode))
        assertEquals("$draft/preview", chosen.headers[HttpHeaders.Location])
        val preview = browser.get("$draft/preview").bodyAsText()
        assertTrue("Fișierul nu schimbă nimic" in preview, preview)
        assertEquals(
            sampleDataset().activities.size.toString(),
            Regex("(\\d+) activități rămân").find(preview)?.groupValues?.get(1),
        )
        assertEquals(sampleDataset(), services.datasets.current()!!.dataset)
    }

    @Test
    fun theDiffComparesClassesById() {
        val old = sampleDataset()
        val first = old.activities.first()
        val otherRoom = old.rooms.first { it.id != first.roomId }
        val new = old.copy(
            activities = listOf(first.copy(roomId = otherRoom.id)) + old.activities.drop(2) +
                first.copy(id = "nou", openToErasmus = true),
        )
        val diff = diffTimetables(old, new, lines = mapOf("nou" to 12))
        assertEquals(1, diff.count(ChangeKind.ADDED))
        assertEquals(1, diff.count(ChangeKind.CHANGED))
        assertEquals(1, diff.count(ChangeKind.REMOVED))
        assertEquals(old.activities.size - 2, diff.unchanged)
        val changed = diff.changes.single { it.kind == ChangeKind.CHANGED }
        assertEquals(listOf("room"), changed.changedFields)
        assertEquals(old.roomsById[first.roomId]?.code, changed.before?.get("room"))
        assertEquals(otherRoom.code, changed.after?.get("room"))
        assertEquals(12, diff.changes.single { it.kind == ChangeKind.ADDED }.line)
        assertEquals(old.activities[1].id, diff.changes.last().activityId)
        assertEquals(0, diffTimetables(old, old).changes.size)
    }

    @Test
    fun aTokenSendsATimetableUntilItIsRevoked() = withDashboard { services, browser ->
        val created = browser.submitForm(
            "/admin/api-tokens",
            parameters {
                append("name", "Agent orar")
                append("scope", SCOPE_TIMETABLE_WRITE)
                append("expires", "")
            },
        )
        assertEquals(HttpStatusCode.OK, created.status)
        val secret = assertNotNull(Regex("ett_[A-Za-z0-9_-]{43}").find(created.bodyAsText())).value
        val stored = services.apiTokens.list().single()
        assertEquals("admin@faima.test", stored.createdByEmail)
        assertNull(stored.lastUsedAt)
        assertFalse(
            secret in services.db.connection.use { c ->
                c.createStatement().executeQuery("SELECT token_hash::text FROM api_token").use {
                    it.next()
                    it.getString(1)
                }
            },
        )
        assertTrue("a creat tokenul API Agent orar" in services.audit.page(AuditFilter(), 0, 1).single().summary)

        val url = "/api/admin/v1/semesters/2026-1/timetable"
        suspend fun send(csv: String, token: String? = secret, query: String = "") = browser.post(url + query) {
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Text.CSV)
            setBody(csv)
        }

        assertEquals(HttpStatusCode.Unauthorized, send(editedSheet(), token = null).status)
        val wrong = send(editedSheet(), token = "ett_" + "x".repeat(43))
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(ErrorCodes.UNAUTHORIZED, wrong.body<ApiError>().code)

        val before = services.datasets.current()!!.dataset
        val version = services.datasets.version("2026-1")!!
        val dry = send(editedSheet(), query = "?dry_run=true")
        assertEquals(HttpStatusCode.OK, dry.status, dry.bodyAsText())
        val report = dry.body<TimetableImportReport>()
        assertTrue(report.dryRun)
        assertFalse(report.saved)
        assertEquals(version, report.version)
        assertEquals(1, report.counts.added)
        assertEquals(1, report.counts.changed)
        assertEquals(1, report.counts.removed)
        assertEquals(7, report.counts.unchanged)
        val room = report.changes.single { it.kind == "changed" }.fields.single()
        assertEquals("room", room.field)
        assertEquals("AN015", room.old)
        assertEquals("EC210", room.new)
        assertTrue("\"new\":\"EC210\"" in dry.bodyAsText())
        // dry_run saves nothing
        assertEquals(before, services.datasets.current()!!.dataset)
        assertEquals(version, services.datasets.version("2026-1"))

        val dryErrors = send(sheetWithError(), query = "?dry_run=true").body<TimetableImportReport>()
        assertEquals(2, dryErrors.errors.single().line)
        val refused = send(sheetWithError())
        assertEquals(HttpStatusCode.Conflict, refused.status)
        val problem = refused.body<ApiError>()
        assertEquals(ErrorCodes.INVALID_TIMETABLE, problem.code)
        assertTrue(problem.details.single().startsWith("Rândul 2"))
        assertEquals(before, services.datasets.current()!!.dataset)

        val saved = send(editedSheet())
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
        val savedReport = saved.body<TimetableImportReport>()
        assertTrue(savedReport.saved)
        assertEquals(services.datasets.version("2026-1"), savedReport.version)
        assertNotNull(services.datasets.current()!!.dataset.activitiesById["calit-s-new"])
        val entry = services.audit.page(AuditFilter(), 0, 1).single()
        assertEquals("token API Agent orar", entry.adminEmail)
        assertTrue("prin API" in entry.summary)
        assertNotNull(services.apiTokens.list().single().lastUsedAt)
        assertTrue("Agent orar" in browser.get("/admin/api-tokens").bodyAsText())

        val revoke = browser.submitForm("/admin/api-tokens/${stored.id}/revoke", parameters { })
        assertEquals(HttpStatusCode.Found, revoke.status)
        assertNotNull(services.apiTokens.list().single().revokedAt)
        assertTrue("a revocat tokenul API Agent orar" in services.audit.page(AuditFilter(), 0, 1).single().summary)
        assertEquals(HttpStatusCode.Unauthorized, send(editedSheet(), query = "?dry_run=true").status)
    }

    @Test
    fun anExpiredTokenIsRefusedAndEditorsCannotMakeTokens() = withDashboard { services, browser ->
        val account = services.admins.list().single()
        val admin = Admin(account.id, account.email, AdminRole.ADMIN)
        val (_, expired) = services.apiTokens.create(
            "Vechi",
            SCOPE_TIMETABLE_WRITE,
            Clock.System.now() - 1.hours,
            admin,
        )
        assertNull(services.apiTokens.authenticate(expired))
        val (_, valid) = services.apiTokens.create("Nou", SCOPE_TIMETABLE_WRITE, Clock.System.now() + 1.hours, admin)
        assertNotNull(services.apiTokens.authenticate(valid))
        val past = browser.submitForm(
            "/admin/api-tokens",
            parameters {
                append("name", "Agent")
                append("scope", SCOPE_TIMETABLE_WRITE)
                append("expires", "2020-01-01")
            },
        )
        assertTrue("err=" in past.headers[HttpHeaders.Location].orEmpty())

        val (editor, password) = services.admins.createWithTemporaryPassword("editor@faima.test", AdminRole.EDITOR)
        services.admins.changePassword(editor.id, password, "parola-noua-pentru-panou", keepSession = null)
        val other = createClient {
            install(HttpCookies)
            sameSite()
            followRedirects = false
        }
        other.submitForm(
            "/admin/login",
            parameters {
                append("email", "editor@faima.test")
                append("password", "parola-noua-pentru-panou")
            },
        )
        assertEquals(HttpStatusCode.Forbidden, other.get("/admin/api-tokens").status)
        assertFalse("Tokenuri API" in other.get("/admin/semesters").bodyAsText())
    }

    @Test
    fun theSemesterPageUploadStartsTheWizard() = withDashboard { _, browser ->
        val page = browser.get("/admin/semesters/2026-1").bodyAsText()
        assertTrue("action=\"/admin/semesters/2026-1/import\"" in page)
        val upload = browser.get("/admin/semesters/2026-1/import").bodyAsText()
        assertTrue("aria-current=\"step\"" in upload)
    }
}
