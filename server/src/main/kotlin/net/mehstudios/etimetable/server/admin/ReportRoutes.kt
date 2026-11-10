// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
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
    page("/reports", services) { admin ->
        val closed = call.parameters["status"] == "closed"
        val openCount = services.reports.count(closed = false)
        val closedCount = services.reports.count(closed = true)
        val paging = call.parameters.paging(if (closed) closedCount else openCount)
        val reports = services.reports.page(closed, paging.offset, paging.size)
        call.respondHtml {
            adminPage(
                "Probleme de orar",
                admin,
                Section.REPORTS,
                context = { +"Sesizări trimise de studenți despre activitățile din orar." },
            ) {
                tableCard(
                    filters = {
                        nav("tabs") {
                            attributes["aria-label"] = "Stare"
                            a(href = "/admin/reports", classes = if (closed) null else "active") {
                                +"Deschise"
                                span("n") { +openCount.toString() }
                            }
                            a(href = "/admin/reports?status=closed", classes = if (closed) "active" else null) {
                                +"Închise"
                                span("n") { +closedCount.toString() }
                            }
                        }
                    },
                    footer = { pager(paging, "/admin/reports", call.request.queryParameters) },
                ) {
                    if (reports.isEmpty()) {
                        emptyState(
                            if (closed) "Nicio problemă închisă" else "Nicio problemă deschisă",
                            if (closed) null else "Studenții trimit problemele din aplicație, de pe pagina clasei.",
                        )
                    } else {
                        table {
                            thead {
                                tr {
                                    th { +"Activitate" }
                                    th { +"Student" }
                                    th { +"Problemă" }
                                    th { +"Trimisă" }
                                    th { +"Stare" }
                                }
                            }
                            tbody {
                                reports.forEach { report ->
                                    tr {
                                        td {
                                            a(href = "/admin/reports/${report.id}") { +report.snapshot.subjectName }
                                            div("hint") { +report.snapshot.semesterName }
                                            div("hint") { +classTime(report) }
                                        }
                                        td {
                                            +report.studentName
                                            div("hint") { +report.studentEmail }
                                        }
                                        td { +report.description.preview(160) }
                                        td { +reportTime(report.createdAt) }
                                        td { +report.status.label() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    page("/reports/{report}", services) { admin ->
        val id = reportId(call.parameters["report"]) ?: return@page call.respond(HttpStatusCode.NotFound)
        val report = services.reports.find(id) ?: return@page call.respond(HttpStatusCode.NotFound)
        val dataset = services.datasets.dataset(report.semesterId)
        val canEdit = dataset?.activitiesById?.containsKey(report.activityId) == true
        val csrf = call.sessions.get<AdminCookie>()?.token?.let(::reportCsrf).orEmpty()
        call.respondHtml {
            adminPage(
                "Sesizare de orar",
                admin,
                Section.REPORTS,
                context = {
                    a(href = "/admin/reports") { +"Probleme de orar" }
                    +" · ${reportTime(report.createdAt)}"
                },
                actions = {
                    if (canEdit) {
                        val semester = report.semesterId.encodeURLParameter()
                        val activity = report.activityId.encodeURLParameter()
                        val back = "/admin/reports/${report.id}".encodeURLParameter()
                        a(href = "/admin/semesters/$semester/activities/$activity?back=$back", classes = "btn") {
                            +"Editează activitatea"
                        }
                    }
                },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                div("card") {
                    h2 { +report.snapshot.subjectName }
                    p { +report.snapshot.semesterName }
                    p { +classTime(report) }
                    p { +"Săptămâni: ${weeksLabel(report.snapshot.activity.slot.weeks) ?: "toate"}" }
                    p { +"Sală: ${report.snapshot.room ?: "nespecificată"}" }
                    p { +"Profesor: ${report.snapshot.professorName ?: "nespecificat"}" }
                    p { +"Grupe: ${report.snapshot.audience.ifBlank { "nespecificate" }}" }
                    p("hint") { +"Datele activității la momentul trimiterii sesizării." }
                    if (!canEdit) {
                        p("notice") {
                            +"Activitatea nu mai există în orar. Sesizarea păstrează datele originale."
                        }
                    }
                }
                div("card") {
                    h2 { +"Trimisă de" }
                    p { +report.studentName }
                    p("hint") { +report.studentEmail }
                }
                div("card") {
                    h2 { +"Problema semnalată" }
                    p {
                        style = "white-space: pre-wrap; overflow-wrap: anywhere"
                        +report.description
                    }
                }
                if (report.status == ReportStatus.OPEN) {
                    div("card") {
                        h2 { +"Închide sesizarea" }
                        p("hint") { +"Corectează activitatea înainte să marchezi problema ca rezolvată." }
                        form(action = "/admin/reports/${report.id}/review", method = FormMethod.post) {
                            hiddenInput(name = "csrf") { value = csrf }
                            div("form-actions") {
                                button {
                                    name = "status"
                                    value = ReportStatus.FIXED.value
                                    +"Marchează rezolvată"
                                }
                                button(classes = "secondary") {
                                    name = "status"
                                    value = ReportStatus.DISMISSED.value
                                    +"Respinge sesizarea"
                                }
                            }
                        }
                    }
                } else {
                    p("notice") {
                        +listOfNotNull(
                            report.status.label(),
                            report.reviewedAt?.let(::reportTime),
                            report.reviewedBy ?: "cont de administrator șters",
                        ).joinToString(" · ")
                    }
                }
            }
        }
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
        call.respondRedirect("/admin/reports/$id")
    }
}

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
