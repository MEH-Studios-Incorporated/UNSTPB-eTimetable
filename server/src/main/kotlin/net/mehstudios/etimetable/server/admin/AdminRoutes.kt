// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.header
import io.ktor.server.request.path
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
import net.mehstudios.etimetable.server.timetable.TimetableSheet
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
            if (admin.mustChangePassword) return@get call.respondRedirect("/admin/account")
            overview(services, admin)
        }

        accountRoutes(services)
        semesterRoutes(services)
        announcementRoutes(services)
        previewRoutes(services)
        eventRoutes(services)
        editRoutes(services)
        reportRoutes(services)
        scheduleRoutes(services)
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

    action("/semesters", services) { admin ->
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
        services.audit.record(admin, AuditCategory.TIMETABLE, "a creat semestrul ${semester.name.ro}")
        call.respondRedirect("/admin/semesters/$id?msg=${"Semestrul a fost creat.".encodeURLParameter()}")
    }

    action("/semesters/{id}/current", services) { admin ->
        val id = call.parameters["id"].orEmpty()
        if (services.datasets.dataset(id) == null) return@action call.respond(HttpStatusCode.NotFound)
        services.datasets.makeCurrent(id)
        services.audit.record(admin, AuditCategory.TIMETABLE, "a făcut curent semestrul $id")
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

    action("/semesters/{id}/calendar", services) { admin ->
        val id = call.parameters["id"].orEmpty()
        if (services.datasets.dataset(id) == null) return@action call.respond(HttpStatusCode.NotFound)
        val message = try {
            services.datasets.saveSemester(CalendarForm.parse(call.receiveParameters(), id))
            services.audit.record(admin, AuditCategory.TIMETABLE, "a salvat calendarul semestrului $id")
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
        if (imported != null) {
            services.audit.record(
                admin,
                AuditCategory.TIMETABLE,
                "a importat orarul semestrului ${existing.semester.id}: ${imported.activities.size} activități",
            )
        }
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
                val colours = subjectColours(dataset)
                val path = "/admin/semesters/${dataset.semester.id}/groups/${group.id.encodeURLParameter()}"
                val back = linkTo(path, call.request.queryParameters, emptyMap())
                weekGrid(
                    activities.map { a ->
                        classEntry(
                            dataset,
                            colours,
                            a,
                            activityHref(dataset, a, back),
                            halvesOf(dataset, group, a),
                            who = dataset.professorsById[a.professorId]?.displayName?.trim(),
                        )
                    },
                    TeachingCalendar(dataset.semester),
                    path,
                    call.request.queryParameters,
                )
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
 * The session cookie is SameSite=Lax, so other sites cannot post with it. On top of that a
 * post must say it comes from this host: browsers send Origin on every form post, and
 * Referer where an older one does not. A post with neither, or one the browser marks as
 * coming from another site, is refused.
 */
internal fun RoutingContext.sameOrigin(): Boolean {
    val fetchSite = call.request.header("Sec-Fetch-Site")
    if (fetchSite != null && fetchSite != "same-origin" && fetchSite != "none") return false
    val host = call.request.header(HttpHeaders.Host) ?: call.request.local.serverHost
    val source = call.request.header(HttpHeaders.Origin)?.takeIf { it != "null" }
        ?: call.request.header(HttpHeaders.Referrer)
        ?: return false
    return withoutDefaultPort(source.substringAfter("://").substringBefore('/')) == withoutDefaultPort(host)
}

/** Origin leaves out :80 and :443, a Host header may carry them. */
private fun withoutDefaultPort(host: String) = host.removeSuffix(":80").removeSuffix(":443")

/** Pages a person with a temporary password may still open: the one where they change it. */
private val FIRST_SIGN_IN_PAGES = setOf("/admin/account", "/admin/account/password")

internal fun Route.page(
    path: String,
    services: Services,
    role: AdminRole = AdminRole.EDITOR,
    body: suspend RoutingContext.(Admin) -> Unit,
) = get(path) {
    val admin = currentAdmin(services) ?: return@get call.respondRedirect("/admin/login")
    if (admin.mustChangePassword && call.request.path() !in FIRST_SIGN_IN_PAGES) {
        return@get call.respondRedirect("/admin/account")
    }
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
    if (admin.mustChangePassword && call.request.path() !in FIRST_SIGN_IN_PAGES) {
        return@post call.respondRedirect("/admin/account")
    }
    if (role == AdminRole.ADMIN && admin.role != AdminRole.ADMIN) return@post call.respond(HttpStatusCode.Forbidden)
    body(admin)
}
