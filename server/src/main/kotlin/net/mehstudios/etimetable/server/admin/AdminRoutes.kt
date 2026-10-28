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
import kotlinx.html.*
import kotlinx.serialization.Serializable
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Semester
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.schedule.baseActivities
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import java.util.UUID

@Serializable
data class AdminCookie(val token: String)

private const val MAX_SHEET_BYTES = 5 * 1024 * 1024

private val DAY_NAMES = listOf("Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică")
private val TYPE_NAMES = mapOf(
    ActivityType.COURSE to "Curs",
    ActivityType.SEMINAR to "Seminar",
    ActivityType.LAB to "Laborator",
    ActivityType.PROJECT to "Proiect",
)

fun Route.adminRoutes(services: Services) {
    route("/admin") {
        staticResources("/static", "admin")

        get { call.respondRedirect("/admin/semesters") }

        get("/login") { call.respondHtml { loginPage(error = null) } }
        post("/login") {
            if (!sameOrigin()) return@post call.respond(HttpStatusCode.Forbidden)
            val form = call.receiveParameters()
            val token = services.admins.login(form["email"].orEmpty(), form["password"].orEmpty())
            if (token == null) {
                call.respondHtml(HttpStatusCode.Unauthorized) { loginPage(error = "Email sau parolă greșită.") }
            } else {
                call.sessions.set(AdminCookie(token))
                call.respondRedirect("/admin/semesters")
            }
        }
        post("/logout") {
            call.sessions.get<AdminCookie>()?.let { services.admins.logout(it.token) }
            call.sessions.clear<AdminCookie>()
            call.respondRedirect("/admin/login")
        }

        page("/password", services) { admin ->
            call.respondHtml {
                adminPage("Schimbă parola", admin, null) {
                    notice(call.parameters["msg"])
                    notice(call.parameters["err"], error = true)
                    form(action = "/admin/password", method = FormMethod.post, classes = "panel") {
                        label {
                            htmlFor = "password"
                            +"Parola nouă"
                        }
                        passwordInput(name = "password") {
                            id = "password"
                            required = true
                            minLength = "8"
                        }
                        button { +"Schimbă parola" }
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
        adminListRoutes(services)
    }
}

private fun Route.semesterRoutes(services: Services) {
    page("/semesters", services) { admin ->
        val semesters = services.datasets.semesters()
        call.respondHtml {
            adminPage("Semestre", admin, Section.SEMESTERS) {
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
                                    th { +"Activități" }
                                    th { +"Stare" }
                                    th { }
                                }
                            }
                            tbody {
                                semesters.forEach { s ->
                                    tr {
                                        td {
                                            a(href = "/admin/semesters/${s.semester.id}") { +s.semester.name.ro }
                                            br
                                            span("hint") { +s.semester.id }
                                        }
                                        td { +"${s.semester.firstDay} - ${s.semester.lastDay}" }
                                        td { +s.activityCount.toString() }
                                        td { if (s.isCurrent) span("tag current") { +"Curent" } }
                                        td {
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
                h2 { +"Semestru nou" }
                form(action = "/admin/semesters", method = FormMethod.post, classes = "panel") {
                    label {
                        htmlFor = "id"
                        +"Cod"
                    }
                    textInput(name = "id") {
                        id = "id"
                        required = true
                        placeholder = "2026-1"
                    }
                    p("hint") { +"Rămâne același tot semestrul. Litere, cifre, - și _." }
                    calendarFields(null)
                    label {
                        checkBoxInput(name = "copy_current") { checked = semesters.any { it.isCurrent } }
                        +" Copiază grupele, materiile și orarul semestrului curent"
                    }
                    button { +"Creează semestrul" }
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
        call.respondHtml {
            adminPage(semester.name.ro, admin, Section.SEMESTERS) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                p {
                    if (isCurrent) span("tag current") { +"Curent" } else span("tag") { +"Nu este în aplicație" }
                    +" ${dataset.activities.size} activități, ${dataset.groups.size} grupe, "
                    +"${dataset.subjects.size} materii, ${dataset.professors.size} profesori"
                }

                h2 { +"Importă orarul" }
                form(
                    action = "/admin/semesters/${semester.id}/import",
                    method = FormMethod.post,
                    encType = FormEncType.multipartFormData,
                    classes = "panel",
                ) {
                    p {
                        +"Un fișier CSV cu un rând pe activitate, în formatul descris în ghidul de administrare. "
                        a(href = "/admin/semesters/${semester.id}/export.csv") { +"Descarcă orarul actual" }
                        +" ca punct de plecare."
                    }
                    fileInput(name = "sheet") {
                        accept = ".csv,text/csv"
                        required = true
                    }
                    p("hint") {
                        +"Importul înlocuiește tot orarul acestui semestru. "
                        +"Dacă fișierul are erori, nu se salvează nimic."
                    }
                    button { +"Importă" }
                }

                h2 { +"Grupe" }
                if (dataset.groups.isEmpty()) {
                    p("hint") { +"Grupele apar după primul import." }
                } else {
                    div("panel") {
                        dataset.groups.forEach { group ->
                            a(
                                href = "/admin/semesters/${semester.id}/groups/${group.id.encodeURLParameter()}",
                                classes = "button secondary",
                            ) {
                                +group.code
                            }
                            +" "
                        }
                    }
                }

                h2 { +"Calendar" }
                form(action = "/admin/semesters/${semester.id}/calendar", method = FormMethod.post, classes = "panel") {
                    calendarFields(semester)
                    button { +"Salvează calendarul" }
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
            adminPage("Import", admin, Section.SEMESTERS) {
                if (imported == null) {
                    div("notice error") {
                        +"Orarul nu a fost importat. Corectează rândurile de mai jos și încearcă din nou."
                        ul("problems") { result.errors.forEach { li { +it } } }
                    }
                } else {
                    p("notice") {
                        +"Am importat ${imported.activities.size} activități pentru ${imported.groups.size} grupe. "
                        +"Aplicațiile le primesc la următoarea sincronizare."
                    }
                    if (result.warnings.isNotEmpty()) {
                        h2 { +"De verificat" }
                        ul("problems") { result.warnings.forEach { li { +it } } }
                    }
                }
                a(href = "/admin/semesters/${existing.semester.id}", classes = "button secondary") {
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
        val activities = baseActivities(dataset, Enrollment.National(group.id))
        call.respondHtml {
            adminPage("Orarul grupei ${group.code}", admin, Section.SEMESTERS) {
                p { a(href = "/admin/semesters/${dataset.semester.id}") { +dataset.semester.name.ro } }
                div("scroll") {
                    div("week") {
                        (1..5).forEach { day ->
                            div("day") {
                                h3 { +DAY_NAMES[day - 1] }
                                activities.filter { it.slot.isoDay == day }.sortedBy { it.slot.start }.forEach { a ->
                                    div("slot") {
                                        strong { +(dataset.subjectsById[a.subjectId]?.name?.ro ?: a.subjectId) }
                                        +"${a.slot.start} - ${a.slot.end} · ${TYPE_NAMES.getValue(a.type)}"
                                        br
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

private fun Route.announcementRoutes(services: Services) {
    page("/announcements", services) { admin ->
        val announcements = services.announcements.page(before = null, limit = 100)
        call.respondHtml {
            adminPage("Anunțuri", admin, Section.ANNOUNCEMENTS) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                form(action = "/admin/announcements", method = FormMethod.post, classes = "panel") {
                    label {
                        htmlFor = "title"
                        +"Titlu"
                    }
                    textInput(name = "title") {
                        id = "title"
                        required = true
                        maxLength = "200"
                    }
                    label {
                        htmlFor = "body"
                        +"Text"
                    }
                    textArea {
                        id = "body"
                        name = "body"
                        required = true
                    }
                    p("hint") { +"Markdown: **îngroșat**, *cursiv*, [link](https://faima.upb.ro), liste cu -." }
                    label {
                        htmlFor = "author"
                        +"Semnat de"
                    }
                    textInput(name = "author") {
                        id = "author"
                        required = true
                        placeholder = "Secretariat"
                    }
                    label {
                        checkBoxInput(name = "pinned")
                        +" Fixat sus în listă"
                    }
                    button { +"Publică" }
                }
                h2 { +"Publicate" }
                if (announcements.isEmpty()) p("hint") { +"Niciun anunț încă." }
                announcements.forEach { a ->
                    div("panel") {
                        p {
                            strong { +a.title }
                            if (a.pinned) {
                                +" "
                                span("tag pinned") { +"Fixat" }
                            }
                        }
                        p("hint") { +"${a.author} · ${a.publishedAt.toString().take(16).replace('T', ' ')} UTC" }
                        p { +a.body }
                        form(action = "/admin/announcements/${a.id}/pin", method = FormMethod.post) {
                            hiddenInput(name = "pinned") { value = (!a.pinned).toString() }
                            button(classes = "secondary") { +(if (a.pinned) "Nu mai fixa" else "Fixează") }
                        }
                        +" "
                        form(action = "/admin/announcements/${a.id}/delete", method = FormMethod.post) {
                            button(classes = "danger") { +"Șterge" }
                        }
                    }
                }
            }
        }
    }
    action("/announcements", services) { _ ->
        val form = call.receiveParameters()
        val title = form["title"].orEmpty().trim()
        val body = form["body"].orEmpty().trim()
        val author = form["author"].orEmpty().trim()
        if (title.isEmpty() || body.isEmpty() || author.isEmpty()) {
            return@action call.respondRedirect(
                "/admin/announcements?err=${"Titlul, textul și semnătura sunt obligatorii.".encodeURLParameter()}",
            )
        }
        services.announcements.publish(title, body, author, pinned = form["pinned"] == "on")
        call.respondRedirect("/admin/announcements?msg=${"Anunțul a fost publicat.".encodeURLParameter()}")
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

private fun Route.adminListRoutes(services: Services) {
    page("/admins", services, AdminRole.ADMIN) { admin ->
        val admins = services.admins.list()
        call.respondHtml {
            adminPage("Administratori", admin, Section.ADMINS) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
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
                                td {
                                    if (other.id != admin.id) {
                                        form(action = "/admin/admins/${other.id}/delete", method = FormMethod.post) {
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
                h2 { +"Cont nou" }
                form(action = "/admin/admins", method = FormMethod.post, classes = "panel") {
                    label {
                        htmlFor = "email"
                        +"Email"
                    }
                    emailInput(name = "email") {
                        id = "email"
                        required = true
                    }
                    label {
                        htmlFor = "password"
                        +"Parolă inițială"
                    }
                    passwordInput(name = "password") {
                        id = "password"
                        required = true
                        minLength = "8"
                    }
                    p("hint") { +"Spune-i persoanei să o schimbe după prima conectare." }
                    label {
                        htmlFor = "role"
                        +"Rol"
                    }
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
                    button { +"Creează contul" }
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

private fun FlowContent.calendarFields(semester: Semester?) {
    label {
        htmlFor = "name_ro"
        +"Nume"
    }
    textInput(name = "name_ro") {
        id = "name_ro"
        required = true
        value = semester?.name?.ro.orEmpty()
        placeholder =
            "Semestrul 1, 2026-2027"
    }
    label {
        htmlFor = "name_en"
        +"Nume în engleză"
    }
    textInput(name = "name_en") {
        id = "name_en"
        value = semester?.name?.en.orEmpty()
        placeholder =
            "Semester 1, 2026-2027"
    }
    label {
        htmlFor = "first_day"
        +"Prima zi de cursuri"
    }
    dateInput(name = "first_day") {
        id = "first_day"
        required = true
        value = semester?.firstDay?.toString().orEmpty()
    }
    label {
        htmlFor = "last_day"
        +"Ultima zi de cursuri"
    }
    dateInput(name = "last_day") {
        id = "last_day"
        required = true
        value = semester?.lastDay?.toString().orEmpty()
    }
    label {
        htmlFor = "breaks"
        +"Vacanțe și zile libere"
    }
    textArea {
        id = "breaks"
        name = "breaks"
        +(semester?.let(CalendarForm::breaksText).orEmpty())
    }
    p("hint") {
        +"Câte una pe rând: 2026-12-21 2027-01-03 Vacanța de iarnă | Winter break. Pentru o singură zi, o singură dată."
    }
    label {
        htmlFor = "overrides"
        +"Săptămâni cu paritatea schimbată"
    }
    textArea {
        id = "overrides"
        name = "overrides"
        +(semester?.let(CalendarForm::overridesText).orEmpty())
    }
    p("hint") { +"Câte una pe rând: o zi din săptămână și pară sau impară, de exemplu 2026-11-30 pară." }
}

private fun weeksLabel(rule: WeekRule): String? = when (rule) {
    WeekRule.All -> null
    WeekRule.Odd -> "săptămâni impare"
    WeekRule.Even -> "săptămâni pare"
    is WeekRule.Only -> "săptămânile ${rule.weeks.sorted().joinToString(", ")}"
}

private fun HTML.loginPage(error: String?) {
    adminPage("Administrare eTimetable", null, null) {
        div("login panel") {
            img(src = "/admin/static/faima-seal.png", alt = "Sigiliul FAIMA")
            h1 { +"Administrare eTimetable" }
            notice(error, error = true)
            form(action = "/admin/login", method = FormMethod.post) {
                label {
                    htmlFor = "email"
                    +"Email"
                }
                emailInput(name = "email") {
                    id = "email"
                    required = true
                    attributes["autocomplete"] = "username"
                }
                label {
                    htmlFor = "password"
                    +"Parolă"
                }
                passwordInput(name = "password") {
                    id = "password"
                    required = true
                }
                button { +"Conectează-te" }
            }
        }
    }
}

private suspend fun RoutingContext.currentAdmin(services: Services): Admin? =
    call.sessions.get<AdminCookie>()?.let { services.admins.adminFor(it.token) }

/**
 * The session cookie is SameSite=Lax, so other sites cannot post with it.
 * This also refuses posts whose Origin is another host, for older browsers.
 */
private fun RoutingContext.sameOrigin(): Boolean {
    val origin = call.request.header(HttpHeaders.Origin) ?: return true
    val host = call.request.header(HttpHeaders.Host) ?: return false
    return origin.substringAfter("://").substringBefore('/') == host
}

private fun Route.page(
    path: String,
    services: Services,
    role: AdminRole = AdminRole.EDITOR,
    body: suspend RoutingContext.(Admin) -> Unit,
) = get(path) {
    val admin = currentAdmin(services) ?: return@get call.respondRedirect("/admin/login")
    if (role == AdminRole.ADMIN && admin.role != AdminRole.ADMIN) return@get call.respond(HttpStatusCode.Forbidden)
    body(admin)
}

private fun Route.action(
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
