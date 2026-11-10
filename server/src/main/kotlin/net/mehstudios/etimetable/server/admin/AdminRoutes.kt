// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.utils.io.toByteArray
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import kotlinx.serialization.Serializable
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.Semester
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.baseActivities
import net.mehstudios.etimetable.schedule.facultyNow
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.announcements.ANNOUNCEMENT_PAGE_SIZE
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import java.util.UUID
import kotlin.time.Clock

@Serializable
data class AdminCookie(val token: String)

private const val MAX_SHEET_BYTES = 5 * 1024 * 1024

internal val DAY_NAMES = listOf("Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică")
private val MONTHS = listOf(
    "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
    "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie",
)
internal val TYPE_NAMES = mapOf(
    ActivityType.COURSE to "Curs",
    ActivityType.SEMINAR to "Seminar",
    ActivityType.LAB to "Laborator",
    ActivityType.PROJECT to "Proiect",
)

fun Route.adminRoutes(services: Services) {
    route("/admin") {
        staticResources("/static", "admin")

        get {
            val admin = currentAdmin(services) ?: return@get call.respondRedirect("/admin/login")
            overview(services, admin)
        }

        get("/login") { call.respondHtml { loginPage(error = null) } }
        post("/login") {
            if (!sameOrigin()) return@post call.respond(HttpStatusCode.Forbidden)
            val form = call.receiveParameters()
            val token = services.admins.login(form["email"].orEmpty(), form["password"].orEmpty())
            if (token == null) {
                call.respondHtml(HttpStatusCode.Unauthorized) { loginPage(error = "Email sau parolă greșită.") }
            } else {
                call.sessions.set(AdminCookie(token))
                call.respondRedirect("/admin")
            }
        }
        post("/logout") {
            call.sessions.get<AdminCookie>()?.let { services.admins.logout(it.token) }
            call.sessions.clear<AdminCookie>()
            call.respondRedirect("/admin/login")
        }

        page("/password", services) { admin ->
            call.respondHtml {
                adminPage("Schimbă parola", admin, null, context = { +admin.email }) {
                    notice(call.parameters["msg"])
                    notice(call.parameters["err"], error = true)
                    form(action = "/admin/password", method = FormMethod.post, classes = "card") {
                        field("password", "Parola nouă") {
                            passwordInput(name = "password") {
                                id = "password"
                                required = true
                                minLength = "8"
                                attributes["autocomplete"] = "new-password"
                            }
                        }
                        p("hint") { +"Cel puțin 8 caractere. După schimbare te conectezi din nou." }
                        div("form-actions") { button { +"Schimbă parola" } }
                    }
                }
            }
        }
        action("/password", services) { admin ->
            val password = call.receiveParameters()["password"].orEmpty()
            services.admins.changePassword(admin.id, password)
            call.sessions.clear<AdminCookie>()
            call.respondRedirect("/admin/login")
        }

        semesterRoutes(services)
        announcementRoutes(services)
        eventRoutes(services)
        adminListRoutes(services)
        editRoutes(services)
        reportRoutes(services)
        scheduleRoutes(services)
    }
}

/** The start page: today's teaching week, the numbers, and what still needs doing. */
private suspend fun RoutingContext.overview(services: Services, admin: Admin) {
    val current = services.datasets.current()
    val dataset = current?.dataset
    val students = services.accounts.counts()
    val announcements = services.announcements.page(before = null, limit = 100)
    val nextEvents = services.events.forApps().filter { it.over > Clock.System.now() }.take(3)
    val openReports = services.reports.openCount()
    val today = Clock.System.facultyNow().date
    call.respondHtml {
        adminPage(
            "Prezentare",
            admin,
            Section.OVERVIEW,
            context = { +"Azi, ${longDate(today)}" },
            actions = {
                if (dataset != null) {
                    a(href = "/admin/semesters/${dataset.semester.id}#import", classes = "btn secondary") {
                        +"Importă orarul"
                    }
                }
                a(href = "/admin/announcements#new", classes = "btn") { +"Anunț nou" }
            },
        ) {
            if (openReports > 0) {
                p("notice") {
                    a(href = "/admin/reports") {
                        val label = plural(openReports, "sesizare de orar", "sesizări de orar")
                        +"$openReports $label așteaptă verificarea."
                    }
                }
            }
            if (dataset == null) {
                div("card") {
                    h2 { +"Niciun semestru în aplicație" }
                    p {
                        +"Aplicația nu are încă ce arăta. Creează semestrul, importă orarul, apoi fă-l curent. "
                        a(href = "/admin/semesters") { +"Mergi la semestre" }
                    }
                }
                return@adminPage
            }
            todayStrip(dataset, today)
            div("stats") {
                stat(dataset.activities.size, "activități")
                stat(dataset.groups.size, "grupe")
                stat(dataset.subjects.size, "materii")
                stat(dataset.professors.size, "profesori")
                stat(
                    students.total,
                    "conturi de studenți",
                    "${students.national} în grupe, ${students.erasmus} Erasmus",
                )
                stat(announcements.size, "anunțuri publicate")
            }
            div("cols") {
                div("card") {
                    h2 { +"Ce e de făcut" }
                    todo(dataset)
                }
                div("card") {
                    h2 { +"Ultimele anunțuri" }
                    if (announcements.isEmpty()) p("hint") { +"Niciun anunț încă." }
                    ul("todo") {
                        announcements.take(4).forEach { a ->
                            li {
                                span("mark ok") { style = "background: var(--primary)" }
                                div {
                                    a(href = "/admin/announcements/${a.id}") { +a.title }
                                    div("hint") { +"${a.author} · ${dateTime(a)}" }
                                }
                            }
                        }
                    }
                }
                div("card") {
                    h2 { +"Evenimente următoare" }
                    if (nextEvents.isEmpty()) {
                        p("hint") {
                            +"Niciun eveniment programat. "
                            a(href = "/admin/events#new") { +"Adaugă unul" }
                        }
                    }
                    ul("todo") {
                        nextEvents.forEach { e ->
                            li {
                                span("mark ok") { style = "background: var(--primary)" }
                                div {
                                    a(href = "/admin/events/${e.id}") { +e.title }
                                    div("hint") { +whenAndWhere(e) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.todayStrip(dataset: Dataset, today: LocalDate) {
    val calendar = TeachingCalendar(dataset.semester)
    val week = calendar.weekOf(today)
    val pause = calendar.breakOn(today)
    div("today") {
        div("num") { +(week?.number?.toString() ?: "-") }
        div("text") {
            strong {
                +when {
                    week != null -> "Săptămâna ${week.number}, ${if (week.parity == Parity.ODD) "impară" else "pară"}"
                    pause != null -> pause.name.ro
                    today < dataset.semester.firstDay -> "Semestrul începe pe ${longDate(dataset.semester.firstDay)}"
                    else -> "Semestrul s-a încheiat"
                }
            }
            span {
                +"${dataset.semester.name.ro}, ${longDate(
                    dataset.semester.firstDay,
                )} - ${longDate(dataset.semester.lastDay)}"
            }
        }
    }
}

private fun FlowContent.stat(value: Int, label: String, detail: String? = null) {
    div("stat") {
        span("value") { +value.toString() }
        span("label") { +label }
        if (detail != null) span("label") { +detail }
    }
}

/** What the current semester still lacks, from the data itself. */
private fun FlowContent.todo(dataset: Dataset) {
    val noTeacher = dataset.activities.count { it.professorId == null }
    val noRoom = dataset.activities.count { it.roomId == null }
    val clashes = TimetableSheet.doubleBookings(dataset).size
    val noEmail = dataset.professors.count { it.email == null }
    val semesterLink = "/admin/semesters/${dataset.semester.id}"
    ul("todo") {
        if (dataset.activities.isEmpty()) {
            item("bad", "Semestrul nu are încă orar.", "Importă fișierul CSV", "$semesterLink#import")
        }
        if (noTeacher > 0) {
            item(
                "warn",
                "$noTeacher ${plural(noTeacher, "activitate nu are", "activități nu au")} profesor sau asistent.",
                "Completează coloana professor și importă din nou",
                "$semesterLink/export.csv",
            )
        }
        if (noRoom > 0) {
            item("warn", "$noRoom ${plural(noRoom, "activitate nu are", "activități nu au")} sală.", null, null)
        }
        if (clashes > 0) {
            item(
                "warn",
                "$clashes ${plural(clashes, "suprapunere", "suprapuneri")} de săli sau profesori.",
                "Vezi lista",
                "$semesterLink#problems",
            )
        }
        if (noEmail > 0) {
            item(
                "warn",
                "$noEmail ${plural(noEmail, "profesor nu are", "profesori nu au")} adresă de email.",
                null,
                null,
            )
        }
        if (dataset.activities.isNotEmpty() && noTeacher == 0 && noRoom == 0 && clashes == 0) {
            item("ok", "Orarul nu are probleme cunoscute.", null, null)
        }
    }
}

private fun UL.item(kind: String, text: String, linkText: String?, href: String?) {
    li {
        span("mark ${if (kind == "warn") "" else kind}".trim())
        div {
            +text
            if (linkText != null && href != null) {
                +" "
                a(href = href) { +linkText }
            }
        }
    }
}

/** Romanian counts: 1 activitate, 2 activități, 20 de activități. */
internal fun plural(n: Int, one: String, many: String) = when {
    n == 1 -> one
    n % 100 in 1..19 -> many
    else -> "de $many"
}

private fun Route.semesterRoutes(services: Services) {
    page("/semesters", services) { admin ->
        val semesters = services.datasets.semesters()
        call.respondHtml {
            adminPage(
                "Semestre și orar",
                admin,
                Section.SEMESTERS,
                context = { +"Aplicația arată semestrul marcat Curent." },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                if (semesters.isEmpty()) {
                    p("notice") { +"Nu există încă niciun semestru. Creează-l mai jos, apoi importă orarul." }
                } else {
                    div("scroll") {
                        table {
                            thead {
                                tr {
                                    th { +"Semestru" }
                                    th { +"Perioadă" }
                                    th(classes = "num") { +"Activități" }
                                    th { +"Stare" }
                                    th { }
                                }
                            }
                            tbody {
                                semesters.forEach { s ->
                                    tr {
                                        td {
                                            a(href = "/admin/semesters/${s.semester.id}") { +s.semester.name.ro }
                                            div("hint") { +s.semester.id }
                                        }
                                        td("tnum") {
                                            +"${longDate(s.semester.firstDay)} - ${longDate(s.semester.lastDay)}"
                                        }
                                        td("num") { +s.activityCount.toString() }
                                        td { if (s.isCurrent) span("tag current") { +"Curent" } }
                                        td("actions") {
                                            if (!s.isCurrent) {
                                                form(
                                                    action = "/admin/semesters/${s.semester.id}/current",
                                                    method = FormMethod.post,
                                                ) {
                                                    button(classes = "secondary") { +"Fă-l curent" }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                details("card") {
                    style = "margin-top: 24px"
                    if (semesters.isEmpty()) attributes["open"] = "open"
                    summary { strong { +"Semestru nou" } }
                    form(action = "/admin/semesters", method = FormMethod.post) {
                        style = "margin-top: 16px"
                        field("id", "Cod") {
                            textInput(name = "id") {
                                id = "id"
                                required = true
                                placeholder = "2026-1"
                            }
                        }
                        p("hint") { +"Rămâne același tot semestrul. Litere, cifre, - și _." }
                        calendarFields(null)
                        label("check") {
                            checkBoxInput(name = "copy_current") { checked = semesters.any { it.isCurrent } }
                            +"Copiază grupele, materiile și orarul semestrului curent"
                        }
                        div("form-actions") { button { +"Creează semestrul" } }
                    }
                }
            }
        }
    }

    action("/semesters", services) { _ ->
        val form = call.receiveParameters()
        val id = form["id"].orEmpty().trim()
        val semester = try {
            CalendarForm.parse(form, id)
        } catch (e: AdminFormError) {
            return@action call.respondRedirect("/admin/semesters?err=${e.message.orEmpty().encodeURLParameter()}")
        }
        if (services.datasets.dataset(id) != null) {
            return@action call.respondRedirect(
                "/admin/semesters?err=${"Există deja semestrul $id.".encodeURLParameter()}",
            )
        }
        val copy = if (form["copy_current"] == "on") services.datasets.current()?.dataset else null
        services.datasets.replace(copy?.copy(semester = semester) ?: Dataset(semester), makeCurrent = false)
        call.respondRedirect("/admin/semesters/$id?msg=${"Semestrul a fost creat.".encodeURLParameter()}")
    }

    action("/semesters/{id}/current", services) { _ ->
        val id = call.parameters["id"].orEmpty()
        if (services.datasets.dataset(id) == null) return@action call.respond(HttpStatusCode.NotFound)
        services.datasets.makeCurrent(id)
        call.respondRedirect("/admin/semesters?msg=${"Aplicația arată acum semestrul $id.".encodeURLParameter()}")
    }

    page("/semesters/{id}", services) { admin ->
        val dataset =
            services.datasets.dataset(call.parameters["id"].orEmpty())
                ?: return@page call.respond(HttpStatusCode.NotFound)
        val semester = dataset.semester
        val isCurrent = services.datasets.current()?.semesterId == semester.id
        val problems = TimetableSheet.doubleBookings(dataset)
        call.respondHtml {
            adminPage(
                semester.name.ro,
                admin,
                Section.SEMESTERS,
                context = {
                    if (isCurrent) span("tag current") { +"Curent" } else span("tag") { +"Nu este în aplicație" }
                    +"  ${dataset.activities.size} activități, ${dataset.groups.size} grupe, "
                    +"${dataset.subjects.size} materii, ${dataset.professors.size} profesori"
                },
                actions = {
                    a(href = "/admin/semesters/${semester.id}/export.csv", classes = "btn secondary") {
                        +"Descarcă orarul (CSV)"
                    }
                    if (!isCurrent) {
                        form(
                            action = "/admin/semesters/${semester.id}/current",
                            method = FormMethod.post,
                            classes = "inline",
                        ) {
                            button { +"Fă-l curent" }
                        }
                    }
                },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)

                h2 { +"Grupe" }
                if (dataset.groups.isEmpty()) {
                    p("hint") { +"Grupele apar după primul import." }
                } else {
                    div("card") { groupsBySeries(dataset) }
                }

                h2 {
                    id = "import"
                    +"Importă orarul"
                }
                form(
                    action = "/admin/semesters/${semester.id}/import",
                    method = FormMethod.post,
                    encType = FormEncType.multipartFormData,
                    classes = "card",
                ) {
                    p {
                        +"Un fișier CSV cu un rând pe activitate, în formatul din ghidul de administrare. "
                        +"Cel mai simplu: descarcă orarul actual, modifică-l și importă-l înapoi."
                    }
                    fileInput(name = "sheet") {
                        accept = ".csv,text/csv"
                        required = true
                    }
                    p("hint") {
                        +"Importul înlocuiește tot orarul acestui semestru. "
                        +"Dacă fișierul are erori, nu se salvează nimic."
                    }
                    div("form-actions") { button { +"Importă" } }
                }

                if (problems.isNotEmpty()) {
                    h2 {
                        id = "problems"
                        +"De verificat"
                        span("count") { +problems.size.toString() }
                    }
                    details("card") {
                        summary { +"Săli sau profesori cu două activități în același timp" }
                        ul("problems") { problems.forEach { li { +it } } }
                    }
                }

                h2 { +"Calendar" }
                form(action = "/admin/semesters/${semester.id}/calendar", method = FormMethod.post, classes = "card") {
                    calendarFields(semester)
                    div("form-actions") { button { +"Salvează calendarul" } }
                }
            }
        }
    }

    action("/semesters/{id}/calendar", services) { _ ->
        val id = call.parameters["id"].orEmpty()
        if (services.datasets.dataset(id) == null) return@action call.respond(HttpStatusCode.NotFound)
        val message = try {
            services.datasets.saveSemester(CalendarForm.parse(call.receiveParameters(), id))
            "msg=${"Calendarul a fost salvat.".encodeURLParameter()}"
        } catch (e: AdminFormError) {
            "err=${e.message.orEmpty().encodeURLParameter()}"
        }
        call.respondRedirect("/admin/semesters/$id?$message")
    }

    get("/semesters/{id}/export.csv") {
        currentAdmin(services) ?: return@get call.respondRedirect("/admin/login")
        val dataset =
            services.datasets.dataset(call.parameters["id"].orEmpty())
                ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"orar-${dataset.semester.id}.csv\"")
        call.respondText(
            TimetableSheet.export(dataset),
            io.ktor.http.ContentType.Text.CSV.withParameter("charset", "utf-8"),
        )
    }

    action("/semesters/{id}/import", services) { admin ->
        val existing =
            services.datasets.dataset(call.parameters["id"].orEmpty())
                ?: return@action call.respond(HttpStatusCode.NotFound)
        var sheet: String? = null
        call.receiveMultipart(formFieldLimit = MAX_SHEET_BYTES.toLong()).forEachPart { part ->
            if (part is PartData.FileItem &&
                part.name == "sheet"
            ) {
                sheet = part.provider().toByteArray().decodeToString()
            }
            part.dispose()
        }
        val result = TimetableSheet.import(sheet.orEmpty(), existing.semester)
        val imported = result.dataset
        if (imported != null) services.datasets.replace(imported, makeCurrent = false)
        call.respondHtml(if (imported == null) HttpStatusCode.UnprocessableEntity else HttpStatusCode.OK) {
            adminPage(
                if (imported == null) "Orarul nu a fost importat" else "Orar importat",
                admin,
                Section.SEMESTERS,
                context = { a(href = "/admin/semesters/${existing.semester.id}") { +existing.semester.name.ro } },
            ) {
                if (imported == null) {
                    div("notice error") {
                        +"Nu s-a salvat nimic. Corectează rândurile de mai jos și încearcă din nou."
                        ul("problems") { result.errors.forEach { li { +it } } }
                    }
                } else {
                    p("notice") {
                        +"Am importat ${imported.activities.size} activități pentru ${imported.groups.size} grupe. "
                        +"Aplicațiile le primesc la următoarea sincronizare."
                    }
                    if (result.warnings.isNotEmpty()) {
                        h2 {
                            +"De verificat"
                            span("count") { +result.warnings.size.toString() }
                        }
                        div("card") { ul("problems") { result.warnings.forEach { li { +it } } } }
                    }
                }
                a(href = "/admin/semesters/${existing.semester.id}", classes = "btn secondary") {
                    +"Înapoi la semestru"
                }
            }
        }
    }

    page("/semesters/{id}/groups/{group}", services) { admin ->
        val dataset =
            services.datasets.dataset(call.parameters["id"].orEmpty())
                ?: return@page call.respond(HttpStatusCode.NotFound)
        val group =
            dataset.groupsById[call.parameters["group"].orEmpty()] ?: return@page call.respond(HttpStatusCode.NotFound)
        val series = dataset.series.firstOrNull { it.id == group.seriesId }
        val programme = dataset.programmes.firstOrNull { it.id == series?.programmeId }
        val activities = baseActivities(dataset, Enrollment.National(group.id))
        call.respondHtml {
            adminPage(
                "Grupa ${group.code}",
                admin,
                Section.SEMESTERS,
                context = {
                    a(href = "/admin/semesters/${dataset.semester.id}") { +dataset.semester.name.ro }
                    +listOfNotNull(programme?.code, series?.let { "anul ${it.year}, seria ${it.name}" })
                        .joinToString(prefix = " · ", separator = ", ")
                },
                actions = {
                    val back = "/admin/semesters/${dataset.semester.id}/groups/${group.id.encodeURLParameter()}"
                    a(
                        href = "/admin/semesters/${dataset.semester.id}/activities/new?group=" +
                            "${group.code.encodeURLParameter()}&back=${back.encodeURLParameter()}",
                        classes = "btn",
                    ) { +"Adaugă activitate" }
                },
            ) {
                div("scroll") {
                    div("week") {
                        (1..5).forEach { day ->
                            div("day") {
                                h3 { +DAY_NAMES[day - 1] }
                                activities.filter { it.slot.isoDay == day }.sortedBy { it.slot.start }.forEach { a ->
                                    val back = "/admin/semesters/${dataset.semester.id}/groups/" +
                                        group.id.encodeURLParameter()
                                    a(
                                        href = "/admin/semesters/${dataset.semester.id}/activities/" +
                                            "${a.id.encodeURLParameter()}?back=${back.encodeURLParameter()}",
                                        classes = "slot ${slotKind(a.type)}".trim(),
                                    ) {
                                        span("time") {
                                            +"${a.slot.start} - ${a.slot.end} · ${TYPE_NAMES.getValue(a.type)}"
                                        }
                                        strong { +(dataset.subjectsById[a.subjectId]?.name?.ro ?: a.subjectId) }
                                        +listOfNotNull(
                                            dataset.roomsById[a.roomId]?.code,
                                            dataset.professorsById[a.professorId]?.displayName,
                                            weeksLabel(a.slot.weeks),
                                            a.audience.halfGroupIds.mapNotNull {
                                                dataset.halfGroupsById[it]
                                            }.joinToString { "${group.code}/${it.suffix}" }.ifEmpty { null },
                                        ).joinToString(" · ")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One row per series ("IMA, anul 2, seria A") with its groups, in the order the sheet gave them. */
private fun FlowContent.groupsBySeries(dataset: Dataset) {
    div("series") {
        dataset.series.forEach { series ->
            val groups = dataset.groups.filter { it.seriesId == series.id }
            if (groups.isEmpty()) return@forEach
            val programme = dataset.programmes.firstOrNull { it.id == series.programmeId }
            div("label") {
                strong { +(programme?.code ?: series.programmeId) }
                +", anul ${series.year}, seria ${series.name}"
            }
            div("chips") {
                groups.forEach { group ->
                    a(href = "/admin/semesters/${dataset.semester.id}/groups/${group.id.encodeURLParameter()}") {
                        +group.code
                    }
                }
            }
        }
    }
}

private fun Route.announcementRoutes(services: Services) {
    page("/announcements", services) { admin ->
        val before = call.parameters["before"]
        val announcements = services.announcements.page(before = before, limit = ANNOUNCEMENT_PAGE_SIZE + 1)
        call.respondHtml {
            announcementsPage(
                admin,
                announcements,
                draft = null,
                msg = call.parameters["msg"],
                err = call.parameters["err"],
            )
        }
    }
    action("/announcements", services) { admin ->
        val draft = Draft.from(call.receiveParameters())
        if (draft.preview || draft.error != null) {
            val announcements = services.announcements.page(before = null, limit = ANNOUNCEMENT_PAGE_SIZE + 1)
            return@action call.respondHtml {
                announcementsPage(
                    admin,
                    announcements,
                    draft,
                    msg = null,
                    err = null,
                )
            }
        }
        val announcement = with(draft) {
            services.announcements.publish(title, body, author, pinned = pinned)
        }
        // every message would fail, so the admin hears why instead of phones staying silent
        val drift = services.push.clockDrift()
        if (drift != null) {
            val notice = clockNotice(drift).encodeURLParameter()
            return@action call.respondRedirect("/admin/announcements?err=$notice")
        }
        services.push.announce(announcement)
        call.respondRedirect("/admin/announcements?msg=${"Anunțul a fost publicat.".encodeURLParameter()}")
    }
    page("/announcements/{id}", services) { admin ->
        val announcement =
            services.announcements.byId(call.parameters["id"].orEmpty())
                ?: return@page call.respond(HttpStatusCode.NotFound)
        call.respondHtml { editAnnouncementPage(admin, announcement, Draft.of(announcement)) }
    }
    action("/announcements/{id}", services) { admin ->
        val announcement =
            services.announcements.byId(call.parameters["id"].orEmpty())
                ?: return@action call.respond(HttpStatusCode.NotFound)
        val draft = Draft.from(call.receiveParameters())
        if (draft.preview || draft.error != null) {
            return@action call.respondHtml { editAnnouncementPage(admin, announcement, draft) }
        }
        services.announcements.update(announcement.id, draft.title, draft.body, draft.author, draft.pinned)
        call.respondRedirect("/admin/announcements?msg=${"Anunțul a fost salvat.".encodeURLParameter()}")
    }
    action("/announcements/{id}/pin", services) { _ ->
        services.announcements.setPinned(call.parameters["id"].orEmpty(), call.receiveParameters()["pinned"] == "true")
        call.respondRedirect("/admin/announcements")
    }
    action("/announcements/{id}/delete", services) { _ ->
        services.announcements.delete(call.parameters["id"].orEmpty())
        call.respondRedirect("/admin/announcements?msg=${"Anunțul a fost șters.".encodeURLParameter()}")
    }
}

/** An announcement being written: what the form sent, and whether to only show the preview. */
private class Draft(
    val title: String,
    val body: String,
    val author: String,
    val pinned: Boolean,
    val preview: Boolean,
) {
    val error: String? get() =
        if (title.length > 200 || body.length > 20_000 || author.length > 100) {
            "Titlul poate avea 200 de caractere, textul 20.000 și semnătura 100."
        } else if (title.isBlank() ||
            body.isBlank() ||
            author.isBlank()
        ) {
            "Titlul, textul și semnătura sunt obligatorii."
        } else {
            null
        }

    companion object {
        fun from(form: Parameters) = Draft(
            form["title"].orEmpty().trim(),
            form["body"].orEmpty().trim(),
            form["author"].orEmpty().trim(),
            form["pinned"] == "on",
            preview = form["preview"] != null,
        )

        fun of(a: Announcement) = Draft(a.title, a.body, a.author, a.pinned, preview = false)
    }
}

/** [fetched] holds one announcement more than a page when an older page exists. */
private fun HTML.announcementsPage(
    admin: Admin,
    fetched: List<Announcement>,
    draft: Draft?,
    msg: String?,
    err: String?,
) {
    val announcements = fetched.take(ANNOUNCEMENT_PAGE_SIZE)
    adminPage(
        "Anunțuri",
        admin,
        Section.ANNOUNCEMENTS,
        context = { +"Ajung la toți studenții, la următoarea sincronizare a aplicației." },
    ) {
        notice(msg)
        notice(err, error = true)
        h2 {
            id = "new"
            style = "margin-top: 0"
            +"Anunț nou"
        }
        announcementForm("/admin/announcements", draft, submit = "Publică")
        h2 {
            +"Publicate"
            span("count") { +announcements.size.toString() }
        }
        if (announcements.isEmpty()) p("hint") { +"Niciun anunț încă." }
        announcements.forEach { a ->
            div("card announcement") {
                h3 {
                    +a.title
                    if (a.pinned) {
                        +" "
                        span("tag pinned") { +"Fixat" }
                    }
                }
                p("meta") { +"${a.author} · ${dateTime(a)}" }
                p { +markdownPreview(a.body, 240) }
                div("form-actions") {
                    a(href = "/admin/announcements/${a.id}", classes = "btn secondary") { +"Editează" }
                    form(action = "/admin/announcements/${a.id}/pin", method = FormMethod.post, classes = "inline") {
                        hiddenInput(name = "pinned") { value = (!a.pinned).toString() }
                        button(classes = "secondary") { +(if (a.pinned) "Nu mai fixa" else "Fixează") }
                    }
                    form(action = "/admin/announcements/${a.id}/delete", method = FormMethod.post, classes = "inline") {
                        onSubmit = "return confirm('Ștergi anunțul? Dispare și din aplicații.')"
                        button(classes = "danger") { +"Șterge" }
                    }
                }
            }
        }
        if (fetched.size > ANNOUNCEMENT_PAGE_SIZE) {
            a(
                href = "/admin/announcements?before=${announcements.last().id.encodeURLParameter()}",
                classes = "btn secondary",
            ) {
                +"Anunțuri mai vechi"
            }
        }
        a(href = "/admin/announcements", classes = "btn secondary") { +"Cele mai noi" }
    }
}

private fun HTML.editAnnouncementPage(admin: Admin, announcement: Announcement, draft: Draft) {
    adminPage(
        "Editează anunțul",
        admin,
        Section.ANNOUNCEMENTS,
        context = {
            a(href = "/admin/announcements") { +"Anunțuri" }
            +" · publicat ${dateTime(announcement)}"
        },
    ) {
        announcementForm("/admin/announcements/${announcement.id}", draft, submit = "Salvează")
        p("hint") { +"Data publicării rămâne aceeași; aplicațiile primesc textul nou la următoarea sincronizare." }
    }
}

private fun FlowContent.announcementForm(action: String, draft: Draft?, submit: String) {
    if (draft?.error != null && !draft.preview) notice(draft.error, error = true)
    div("cols") {
        form(action = action, method = FormMethod.post, classes = "card") {
            field("title", "Titlu") {
                textInput(name = "title") {
                    id = "title"
                    required = true
                    maxLength = "200"
                    value = draft?.title.orEmpty()
                }
            }
            field("body", "Text") {
                textArea {
                    id = "body"
                    name = "body"
                    required = true
                    +draft?.body.orEmpty()
                }
                p("hint") {
                    +"**îngroșat**, *cursiv*, [link](https://faima.upb.ro). "
                    +"Pentru liste, scrie fiecare element pe un rând nou, cu spațiu după cratimă: - primul element."
                }
            }
            field("author", "Semnat de") {
                textInput(name = "author") {
                    id = "author"
                    required = true
                    placeholder = "Secretariat"
                    value = draft?.author.orEmpty()
                }
            }
            label("check") {
                checkBoxInput(name = "pinned") { checked = draft?.pinned == true }
                +"Fixat sus în listă"
            }
            div("form-actions") {
                button { +submit }
                button(classes = "secondary") {
                    name = "preview"
                    value = "1"
                    attributes["formnovalidate"] = "formnovalidate"
                    +"Previzualizează"
                }
            }
        }
        if (draft?.preview == true) {
            div("preview announcement") {
                div("label") { +"Așa apare în aplicație" }
                h3 { +draft.title.ifBlank { "(fără titlu)" } }
                p("meta") { +draft.author.ifBlank { "(fără semnătură)" } }
                div("body") { markdown(draft.body) }
            }
        }
    }
}

private fun Route.adminListRoutes(services: Services) {
    page("/admins", services, AdminRole.ADMIN) { admin ->
        val admins = services.admins.list()
        call.respondHtml {
            adminPage(
                "Administratori",
                admin,
                Section.ADMINS,
                context = { +"Editorii schimbă orarul și anunțurile; administratorii și conturile." },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                tableCard {
                    table {
                        thead {
                            tr {
                                th { +"Email" }
                                th { +"Rol" }
                                th { }
                            }
                        }
                        tbody {
                            admins.forEach { other ->
                                tr {
                                    td { +other.email }
                                    td { +(if (other.role == AdminRole.ADMIN) "Administrator" else "Editor") }
                                    td("actions") {
                                        if (other.id != admin.id) {
                                            form(
                                                action = "/admin/admins/${other.id}/delete",
                                                method = FormMethod.post,
                                            ) {
                                                onSubmit = "return confirm('Ștergi contul ${other.email}?')"
                                                button(classes = "danger") { +"Șterge" }
                                            }
                                        } else {
                                            a(href = "/admin/password") { +"Schimbă parola" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                h2 { +"Cont nou" }
                form(action = "/admin/admins", method = FormMethod.post, classes = "card") {
                    field("email", "Email") {
                        emailInput(name = "email") {
                            id = "email"
                            required = true
                        }
                    }
                    field("password", "Parolă inițială") {
                        passwordInput(name = "password") {
                            id = "password"
                            required = true
                            minLength = "8"
                            attributes["autocomplete"] = "new-password"
                        }
                        p("hint") { +"Spune-i persoanei să o schimbe după prima conectare." }
                    }
                    field("role", "Rol") {
                        select {
                            id = "role"
                            name = "role"
                            option {
                                value = "editor"
                                +"Editor: orar și anunțuri"
                            }
                            option {
                                value = "admin"
                                +"Administrator: tot, inclusiv conturile"
                            }
                        }
                    }
                    div("form-actions") { button { +"Creează contul" } }
                }
            }
        }
    }
    action("/admins", services, AdminRole.ADMIN) { _ ->
        val form = call.receiveParameters()
        val role = if (form["role"] == "admin") AdminRole.ADMIN else AdminRole.EDITOR
        val result = try {
            services.admins.create(form["email"].orEmpty(), form["password"].orEmpty(), role)
            "msg=${"Contul a fost creat.".encodeURLParameter()}"
        } catch (e: AdminFormError) {
            "err=${e.message.orEmpty().encodeURLParameter()}"
        }
        call.respondRedirect("/admin/admins?$result")
    }
    action("/admins/{id}/delete", services, AdminRole.ADMIN) { _ ->
        val id =
            runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                ?: return@action call.respond(HttpStatusCode.NotFound)
        val result = try {
            services.admins.delete(id)
            "msg=${"Contul a fost șters.".encodeURLParameter()}"
        } catch (e: AdminFormError) {
            "err=${e.message.orEmpty().encodeURLParameter()}"
        }
        call.respondRedirect("/admin/admins?$result")
    }
}

/** A label above its input. */
internal fun FlowContent.field(id: String, label: String, input: FlowContent.() -> Unit) {
    div("field") {
        label {
            htmlFor = id
            +label
        }
        input()
    }
}

private fun FlowContent.calendarFields(semester: Semester?) {
    div("cols") {
        style = "gap: 0 16px"
        field("name_ro", "Nume") {
            textInput(name = "name_ro") {
                id = "name_ro"
                required = true
                value = semester?.name?.ro.orEmpty()
                placeholder = "Semestrul 1, 2026-2027"
            }
        }
        field("name_en", "Nume în engleză") {
            textInput(name = "name_en") {
                id = "name_en"
                value = semester?.name?.en.orEmpty()
                placeholder = "Semester 1, 2026-2027"
            }
        }
        field("first_day", "Prima zi de cursuri") {
            dateInput(name = "first_day") {
                id = "first_day"
                required = true
                value = semester?.firstDay?.toString().orEmpty()
            }
        }
        field("last_day", "Ultima zi de cursuri") {
            dateInput(name = "last_day") {
                id = "last_day"
                required = true
                value = semester?.lastDay?.toString().orEmpty()
            }
        }
    }
    field("breaks", "Vacanțe și zile libere") {
        textArea {
            id = "breaks"
            name = "breaks"
            +(semester?.let(CalendarForm::breaksText).orEmpty())
        }
        p("hint") {
            +"Câte una pe rând: 2026-12-21 2027-01-03 Vacanța de iarnă | Winter break. "
            +"Pentru o singură zi, o singură dată."
        }
    }
    field("overrides", "Săptămâni cu paritatea schimbată") {
        textArea {
            id = "overrides"
            name = "overrides"
            +(semester?.let(CalendarForm::overridesText).orEmpty())
        }
        p("hint") { +"Câte una pe rând: o zi din săptămână și pară sau impară, de exemplu 2026-11-30 pară." }
    }
}

internal fun weeksLabel(rule: WeekRule): String? = when (rule) {
    WeekRule.All -> null
    WeekRule.Odd -> "săptămâni impare"
    WeekRule.Even -> "săptămâni pare"
    is WeekRule.Only -> "săptămânile ${rule.weeks.sorted().joinToString(", ")}"
}

/** "3 noiembrie 2026" */
internal fun longDate(date: LocalDate) = "${date.day} ${MONTHS[date.month.ordinal]} ${date.year}"

/** "3 noiembrie 2026, 20:42", in Bucharest time. */
private fun dateTime(a: Announcement): String {
    val local = a.publishedAt.toLocalDateTime(FacultyTimeZone)
    return "${longDate(local.date)}, ${local.time.toString().take(5)}"
}

private fun HTML.loginPage(error: String?) {
    adminPage("Administrare eTimetable", null, null) {
        div("login") {
            img(src = "/admin/static/faima-seal.png", alt = "Sigiliul FAIMA")
            h1 { +"Administrare eTimetable" }
            p("sub") { +"Orarul și anunțurile FAIMA" }
            notice(error, error = true)
            form(action = "/admin/login", method = FormMethod.post) {
                field("email", "Email") {
                    emailInput(name = "email") {
                        id = "email"
                        required = true
                        attributes["autocomplete"] = "username"
                    }
                }
                field("password", "Parolă") {
                    passwordInput(name = "password") {
                        id = "password"
                        required = true
                        attributes["autocomplete"] = "current-password"
                    }
                }
                button { +"Conectează-te" }
            }
            a(href = "/", classes = "back") { +"Înapoi la pagina aplicației" }
        }
    }
}

internal suspend fun RoutingContext.currentAdmin(services: Services): Admin? {
    val admin = call.sessions.get<AdminCookie>()?.let { services.admins.adminFor(it.token) } ?: return null
    return admin.copy(shell = shell(services))
}

/** The theme cookie admin.js sets; anything else means "follow the system". */
private const val THEME_COOKIE = "ett_theme"

private suspend fun RoutingContext.shell(services: Services): Shell {
    val theme = call.request.cookies[THEME_COOKIE]?.takeIf { it == "light" || it == "dark" }
    val dataset = services.datasets.current()?.dataset
        ?: return Shell(openReports = services.reports.openCount(), theme = theme)
    val today = Clock.System.facultyNow().date
    val calendar = TeachingCalendar(dataset.semester)
    val week = calendar.weekOf(today)
    val label = when {
        week != null -> "Săptămâna ${if (week.parity == Parity.ODD) "impară" else "pară"}"
        else -> calendar.breakOn(today)?.name?.ro
    }
    return Shell(
        week = week?.number,
        weekLabel = label,
        semester = dataset.semester.name.ro,
        openReports = services.reports.openCount(),
        theme = theme,
    )
}

/**
 * The session cookie is SameSite=Lax, so other sites cannot post with it.
 * This also refuses posts whose Origin is another host, for older browsers.
 */
private fun RoutingContext.sameOrigin(): Boolean {
    val origin = call.request.header(HttpHeaders.Origin) ?: return true
    val host = call.request.header(HttpHeaders.Host) ?: return false
    return origin.substringAfter("://").substringBefore('/') == host
}

internal fun Route.page(
    path: String,
    services: Services,
    role: AdminRole = AdminRole.EDITOR,
    body: suspend RoutingContext.(Admin) -> Unit,
) = get(path) {
    val admin = currentAdmin(services) ?: return@get call.respondRedirect("/admin/login")
    if (role == AdminRole.ADMIN && admin.role != AdminRole.ADMIN) return@get call.respond(HttpStatusCode.Forbidden)
    body(admin)
}

internal fun Route.action(
    path: String,
    services: Services,
    role: AdminRole = AdminRole.EDITOR,
    body: suspend RoutingContext.(Admin) -> Unit,
) = post(path) {
    if (!sameOrigin()) return@post call.respond(HttpStatusCode.Forbidden)
    val admin = currentAdmin(services) ?: return@post call.respondRedirect("/admin/login")
    if (role == AdminRole.ADMIN && admin.role != AdminRole.ADMIN) return@post call.respond(HttpStatusCode.Forbidden)
    body(admin)
}
