// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.withCharset
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.reports.ReportStatus
import net.mehstudios.etimetable.server.reports.TimetableReport
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.time.Instant

internal fun Route.reportRoutes(services: Services) {
    page("/reports", services) { admin -> reportsPage(services, admin, reportId(call.parameters["selected"])) }
    // the old address of one report, still in links and redirects
    page("/reports/{report}", services) { admin ->
        val id = reportId(call.parameters["report"]) ?: return@page call.respond(HttpStatusCode.NotFound)
        val report = services.reports.find(id) ?: return@page call.respond(HttpStatusCode.NotFound)
        reportsPage(services, admin, report.id, forceClosed = report.status != ReportStatus.OPEN)
    }
    page("/reports.csv", services) { _ ->
        val closed = call.parameters["status"] == "closed"
        val semester = call.parameters["semester"]?.takeIf { it.isNotBlank() }
        val text = call.parameters["q"].orEmpty()
        val total = services.reports.count(closed, semester, text)
        val rows = services.reports.page(closed, 0, total, semester, text)
        call.response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"probleme-de-orar-${if (closed) "inchise" else "deschise"}.csv\"",
        )
        call.respondText(reportsCsv(rows), ContentType.Text.CSV.withCharset(Charsets.UTF_8))
    }

    action("/reports/{report}/review", services) { admin ->
        val form = call.receiveParameters()
        val expected = call.sessions.get<AdminCookie>()?.token?.let(::reportCsrf).orEmpty()
        val supplied = form["csrf"].orEmpty()
        if (expected.isEmpty() || !MessageDigest.isEqual(expected.toByteArray(), supplied.toByteArray())) {
            return@action call.respond(HttpStatusCode.Forbidden)
        }
        val id = reportId(call.parameters["report"]) ?: return@action call.respond(HttpStatusCode.NotFound)
        val status = ReportStatus.entries.singleOrNull { it.value == form["status"] && it != ReportStatus.OPEN }
            ?: return@action call.respond(HttpStatusCode.UnprocessableEntity)
        if (services.reports.find(id) == null) return@action call.respond(HttpStatusCode.NotFound)
        if (!services.reports.review(id, status, admin.id)) {
            return@action call.respondHtml(HttpStatusCode.Conflict) {
                adminPage("Sesizare deja închisă", admin, Section.REPORTS) {
                    p("notice") { +"Alt editor a închis deja această sesizare. Starea nu a fost schimbată." }
                    a(href = "/admin/reports/$id", classes = "btn secondary") { +"Vezi sesizarea" }
                }
            }
        }
        services.audit.record(
            admin,
            AuditCategory.REPORTS,
            "a marcat o problemă de orar ca ${status.label().lowercase()}",
        )
        call.respondRedirect("/admin/reports?selected=$id&${"msg=" + "Problema a fost închisă.".encodeURLParameter()}")
    }
}

/**
 * The reports table, and the selected report in a panel beside it with the class as the
 * student saw it, what changed since, what they wrote and the actions.
 */
private suspend fun RoutingContext.reportsPage(
    services: Services,
    admin: Admin,
    selectedId: UUID?,
    forceClosed: Boolean = false,
) {
    val closed = forceClosed || call.parameters["status"] == "closed"
    val semester = call.parameters["semester"]?.takeIf { it.isNotBlank() }
    val text = call.parameters["q"].orEmpty().trim()
    val openCount = services.reports.count(closed = false, semesterId = semester, text = text)
    val closedCount = services.reports.count(closed = true, semesterId = semester, text = text)
    val paging = call.parameters.paging(if (closed) closedCount else openCount)
    val reports = services.reports.page(closed, paging.offset, paging.size, semester, text)
    val semesters = services.reports.semesters()
    val chosen = selectedId?.let { services.reports.find(it) }
    val dataset = chosen?.let { services.datasets.dataset(it.semesterId) }
    val others = chosen?.let { services.reports.openFor(it.semesterId, it.activityId, it.id) } ?: 0
    val csrf = call.sessions.get<AdminCookie>()?.token?.let(::reportCsrf).orEmpty()
    val query = call.request.queryParameters
    val status = if (closed) "closed" else null
    call.respondHtml {
        adminPage(
            "Probleme de orar",
            admin,
            Section.REPORTS,
            context = {
                +"Trimise de studenți din aplicație, de pe pagina unei clase. "
                +"Orarul nu se schimbă până nu corectezi clasa."
            },
            actions = {
                a(
                    href = linkTo(
                        "/admin/reports.csv",
                        query,
                        mapOf("page" to null, "size" to null, "selected" to null),
                    ),
                    classes = "btn secondary",
                ) {
                    +"Exportă CSV"
                }
            },
        ) {
            notice(call.parameters["msg"])
            notice(call.parameters["err"], error = true)
            div(if (chosen != null) "split" else "split single") {
                div("main-col") {
                    tableCard(
                        filters = {
                            nav("tabs") {
                                attributes["aria-label"] = "Stare"
                                a(
                                    href = linkTo(
                                        "/admin/reports",
                                        query,
                                        mapOf(
                                            "status" to null,
                                            "page" to null,
                                            "selected" to null,
                                        ),
                                    ),
                                    classes = if (closed) null else "active",
                                ) {
                                    +"Deschise"
                                    span("n") { +openCount.toString() }
                                }
                                a(
                                    href = linkTo(
                                        "/admin/reports",
                                        query,
                                        mapOf(
                                            "status" to "closed",
                                            "page" to null,
                                            "selected" to null,
                                        ),
                                    ),
                                    classes = if (closed) "active" else null,
                                ) {
                                    +"Închise"
                                    span("n") { +closedCount.toString() }
                                }
                            }
                            form(action = "/admin/reports", method = FormMethod.get, classes = "filters") {
                                if (status != null) hiddenInput(name = "status") { value = status }
                                searchInput(name = "q") {
                                    value = text
                                    placeholder = "Materie, student sau text"
                                    attributes["aria-label"] = "Caută în probleme"
                                }
                                if (semesters.size > 1) {
                                    select {
                                        name = "semester"
                                        attributes["aria-label"] = "Semestru"
                                        option {
                                            value = ""
                                            +"Toate semestrele"
                                        }
                                        semesters.forEach { (id, name) ->
                                            option {
                                                value = id
                                                selected = id == semester
                                                +name
                                            }
                                        }
                                    }
                                }
                                button(classes = "secondary") { +"Caută" }
                            }
                        },
                        footer = { pager(paging, "/admin/reports", query) },
                    ) {
                        if (reports.isEmpty()) {
                            emptyState(
                                if (closed) "Nicio problemă închisă" else "Nicio problemă deschisă",
                                if (text.isNotEmpty() || semester != null) {
                                    "Niciun raport nu se potrivește cu căutarea. Șterge căutarea sau alege toate semestrele."
                                } else if (!closed) {
                                    "Studenții trimit problemele din aplicație, de pe pagina clasei."
                                } else {
                                    null
                                },
                            )
                        } else {
                            reportTable(reports, chosen?.id, query)
                        }
                    }
                }
                if (chosen != null) reportPanel(chosen, dataset, others, csrf, query)
            }
        }
    }
}

private fun FlowContent.reportTable(reports: List<TimetableReport>, selected: UUID?, query: io.ktor.http.Parameters) {
    table {
        thead {
            tr {
                th { +"Stare" }
                th { +"Clasă" }
                th(classes = "wide") { +"Grupe" }
                th { +"Când" }
                th { +"Raportat de" }
                th(classes = "wide") { +"Primită" }
            }
        }
        tbody {
            reports.forEach { r ->
                tr(if (r.id == selected) "selected" else null) {
                    td {
                        style = "white-space: nowrap"
                        span(if (r.status == ReportStatus.OPEN) "dot open" else "dot ok")
                        +r.status.label()
                    }
                    td {
                        a(href = linkTo("/admin/reports", query, mapOf("selected" to r.id.toString()))) {
                            +r.snapshot.subjectName
                        }
                        div("hint") { +r.description.preview(70) }
                    }
                    td("wide") { +r.snapshot.audience.ifBlank { "-" } }
                    td("tnum") {
                        style = "white-space: nowrap"
                        +classTime(r)
                    }
                    td {
                        +r.studentName.ifBlank { r.studentEmail }
                        div("hint") { +r.studentEmail }
                    }
                    td("tnum wide") {
                        style = "white-space: nowrap"
                        +shortTime(r.createdAt)
                    }
                }
            }
        }
    }
}

private fun FlowContent.reportPanel(
    report: TimetableReport,
    dataset: net.mehstudios.etimetable.model.Dataset?,
    others: Int,
    csrf: String,
    query: io.ktor.http.Parameters,
) {
    val current = dataset?.activitiesById?.get(report.activityId)
    aside("panel") {
        attributes["aria-label"] = "Problema selectată"
        div("panel-head") {
            div {
                strong { +report.snapshot.subjectName }
                div("hint") { +"${report.snapshot.semesterName} · primită ${shortTime(report.createdAt)}" }
            }
            a(href = linkTo("/admin/reports", query, mapOf("selected" to null)), classes = "close") {
                attributes["aria-label"] = "Închide panoul"
                unsafe { +svgIcon("M6 6l12 12M18 6L6 18", 16) }
            }
        }
        h3 { +"Clasa, cum o vedea studentul" }
        dl("facts") {
            dt { +"Când" }
            dd { +classTime(report) }
            dt { +"Săptămâni" }
            dd { +(weeksLabel(report.snapshot.activity.slot.weeks) ?: "toate") }
            dt { +"Sală" }
            dd { +(report.snapshot.room ?: "nespecificată") }
            dt { +"Profesor" }
            dd { +(report.snapshot.professorName ?: "nespecificat") }
            dt { +"Grupe" }
            dd { +report.snapshot.audience.ifBlank { "nespecificate" } }
        }
        val changes = if (current != null && dataset != null) changesSince(report, current, dataset) else null
        when {
            current == null -> p("notice") { +"Clasa nu mai există în orar. Raportul păstrează datele de atunci." }
            changes.isNullOrEmpty() -> p("hint") { +"Clasa nu a fost modificată de la raportare." }
            else -> div("notice change") {
                strong { +"Clasa s-a schimbat de atunci:" }
                ul { changes.forEach { li { +it } } }
            }
        }
        h3 { +"Ce a scris studentul" }
        blockQuote("said") { +report.description }
        p("hint") { +"${report.studentName.ifBlank { "Student" }} · ${report.studentEmail}" }
        if (others > 0) {
            p("notice") {
                +"Încă $others ${plural(others, "raport deschis", "rapoarte deschise")} pentru aceeași clasă."
            }
        }
        if (report.status == ReportStatus.OPEN) {
            div("form-actions") {
                if (current != null) {
                    val back = linkTo(
                        "/admin/reports",
                        query,
                        mapOf("selected" to report.id.toString()),
                    ).encodeURLParameter()
                    a(
                        href = "/admin/semesters/${report.semesterId.encodeURLParameter()}/activities/" +
                            "${report.activityId.encodeURLParameter()}?back=$back",
                        classes = "btn",
                    ) { +"Corectează clasa" }
                }
                form(action = "/admin/reports/${report.id}/review", method = FormMethod.post, classes = "inline") {
                    hiddenInput(name = "csrf") { value = csrf }
                    button(classes = "secondary") {
                        name = "status"
                        value = ReportStatus.FIXED.value
                        +"Marchează corectată"
                    }
                    +" "
                    button(classes = "danger") {
                        name = "status"
                        value = ReportStatus.DISMISSED.value
                        +"Respinge"
                    }
                }
            }
            p("hint") { +"Corectează clasa, salvează, apoi marchează problema corectată." }
        } else {
            p("notice") {
                +listOfNotNull(
                    report.status.label(),
                    report.reviewedAt?.let(::shortTime),
                    report.reviewedBy ?: "cont de administrator șters",
                ).joinToString(" · ")
            }
        }
    }
}

/** What differs between the class as reported and as it is now, in words. */
private fun changesSince(
    report: TimetableReport,
    now: net.mehstudios.etimetable.model.Activity,
    dataset: net.mehstudios.etimetable.model.Dataset,
): List<String> {
    val then = report.snapshot.activity
    val out = mutableListOf<String>()
    if (then.slot.isoDay != now.slot.isoDay || then.slot.start != now.slot.start || then.slot.end != now.slot.end) {
        val day = DAY_NAMES[now.slot.isoDay - 1]
        out += "Ora: ${classTime(report).substringAfter(" · ")} → $day, ${now.slot.start} - ${now.slot.end}"
    }
    if (then.slot.weeks != now.slot.weeks) {
        out += "Săptămâni: ${weeksLabel(then.slot.weeks) ?: "toate"} → ${weeksLabel(now.slot.weeks) ?: "toate"}"
    }
    if (then.roomId != now.roomId) {
        out += "Sala: ${report.snapshot.room ?: "fără"} → ${dataset.roomsById[now.roomId]?.code ?: "fără"}"
    }
    if (then.professorId != now.professorId) {
        out +=
            "Profesor: ${report.snapshot.professorName ?: "fără"} → ${dataset.professorsById[now.professorId]?.displayName ?: "fără"}"
    }
    if (then.type != now.type) out += "Tip: ${TYPE_NAMES[then.type]} → ${TYPE_NAMES[now.type]}"
    if (then.audience != now.audience) out += "Grupele s-au schimbat"
    return out
}

private fun reportsCsv(rows: List<TimetableReport>): String {
    fun cell(text: String) = "\"" + text.replace("\"", "\"\"") + "\""
    val header =
        listOf(
            "primita", "stare", "semestru", "materie", "cand", "sala",
            "profesor", "grupe", "student", "email", "text",
        )
    return (
        listOf(header.joinToString(",")) + rows.map { r ->
            listOf(
                r.createdAt.toString(),
                r.status.value,
                r.semesterId,
                r.snapshot.subjectName,
                classTime(r),
                r.snapshot.room.orEmpty(),
                r.snapshot.professorName.orEmpty(),
                r.snapshot.audience,
                r.studentName,
                r.studentEmail,
                r.description,
            ).joinToString(",") { cell(it) }
        }
        ).joinToString("\r\n", postfix = "\r\n")
}

internal val REPORT_STYLES = """
.split { display: grid; grid-template-columns: minmax(0, 1fr) minmax(320px, 420px); gap: 16px; align-items: start; }
.split.single { grid-template-columns: minmax(0, 1fr); }
.split .main-col { min-width: 0; }
tbody tr.selected { background: var(--primary-soft); }
.panel { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 16px 18px;
  position: sticky; top: 16px; animation: slide 180ms ease-out both; }
@keyframes slide { from { opacity: 0; transform: translateX(8px); } to { opacity: 1; transform: none; } }
.panel-head { display: flex; gap: 12px; align-items: flex-start; justify-content: space-between; margin-bottom: 8px; }
.panel-head strong { font-size: 17px; line-height: 22px; }
.panel .close { color: var(--muted); padding: 6px; border-radius: 6px; display: inline-flex; }
.panel .close:hover { background: var(--alt); color: var(--text); }
.panel h3 { font-size: 12px; line-height: 16px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em;
  margin: 16px 0 6px; }
dl.facts { display: grid; grid-template-columns: max-content 1fr; gap: 4px 12px; margin: 0; font-size: 14px; }
dl.facts dt { color: var(--muted); }
dl.facts dd { margin: 0; }
blockquote.said { margin: 0 0 6px; padding: 10px 12px; background: var(--alt); border-radius: 6px; white-space: pre-wrap;
  overflow-wrap: anywhere; }
.notice.change { background: color-mix(in srgb, var(--change) 28%, var(--surface)); border-left-color: var(--change); color: var(--text); }
.notice.change ul { margin: 6px 0 0; padding-left: 18px; }
@media (max-width: 1600px) { .split:not(.single) .wide { display: none; } }
@media (max-width: 1100px) { .split { grid-template-columns: minmax(0, 1fr); } .panel { position: static; } }
"""

/** Cut by code points, so an emoji or other surrogate pair is never split. */
private fun String.preview(limit: Int): String {
    if (codePointCount(0, length) <= limit) return this
    return substring(0, offsetByCodePoints(0, limit)).trimEnd() + "..."
}

private fun reportId(value: String?): UUID? = runCatching { UUID.fromString(value) }.getOrNull()

private fun reportCsrf(token: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
    MessageDigest.getInstance("SHA-256").digest("timetable-report:$token".toByteArray()),
)

private fun ReportStatus.label(): String = when (this) {
    ReportStatus.OPEN -> "Deschisă"
    ReportStatus.FIXED -> "Rezolvată"
    ReportStatus.DISMISSED -> "Respinsă"
}

private fun classTime(report: TimetableReport): String {
    val activity = report.snapshot.activity
    val day = DAY_NAMES[activity.slot.isoDay - 1]
    return "${TYPE_NAMES[activity.type]} · $day, ${activity.slot.start} - ${activity.slot.end}"
}

private fun reportTime(instant: Instant): String {
    val local = instant.toLocalDateTime(FacultyTimeZone)
    return "${longDate(local.date)}, ${local.time.toString().take(5)}"
}
