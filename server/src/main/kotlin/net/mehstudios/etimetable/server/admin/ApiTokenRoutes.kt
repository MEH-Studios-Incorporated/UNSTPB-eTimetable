// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.html.*
import kotlinx.io.readByteArray
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.schedule.facultyNow
import net.mehstudios.etimetable.server.ApiProblem
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.timetable.ColumnMatch
import net.mehstudios.etimetable.server.timetable.Csv
import net.mehstudios.etimetable.server.timetable.SheetFile
import net.mehstudios.etimetable.server.timetable.SheetFileError
import kotlin.time.Clock

private const val MAX_TOKEN_NAME = 100

/** The Tokenuri API page, for administrators: make a token for a program, see when it was used, revoke it. */
internal fun Route.apiTokenRoutes(services: Services) {
    page("/api-tokens", services, AdminRole.ADMIN) { admin ->
        val tokens = services.apiTokens.list()
        call.respondHtml {
            adminPage(
                "Tokenuri API",
                admin,
                Section.API_TOKENS,
                context = { +"Programele care trimit orarul fără browser se identifică cu un token." },
                actions = { a(href = "#new", classes = "btn") { +"Token nou" } },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                tableCard {
                    if (tokens.isEmpty()) {
                        emptyState(
                            "Niciun token",
                            "Un token lasă un program să trimită fișierul cu orarul unui semestru, " +
                                "cu aceeași verificare ca importul din panou.",
                        )
                        return@tableCard
                    }
                    table {
                        thead {
                            tr {
                                th { +"Nume" }
                                th { +"Drepturi" }
                                th { +"Creat" }
                                th { +"Expiră" }
                                th { +"Folosit ultima dată" }
                                th { +"Stare" }
                                th { }
                            }
                        }
                        tbody {
                            val now = Clock.System.now()
                            tokens.forEach { t ->
                                tr {
                                    td {
                                        strong { +t.name }
                                        div("hint") { +t.createdByEmail }
                                    }
                                    td { code { +t.scope } }
                                    td("tnum") { +shortTime(t.createdAt) }
                                    td("tnum") { +(t.expiresAt?.let(::shortTime) ?: "Nu expiră") }
                                    td("tnum") { +(t.lastUsedAt?.let(::shortTime) ?: "Niciodată") }
                                    td {
                                        when {
                                            t.revokedAt != null -> {
                                                span("dot")
                                                +"Revocat ${shortTime(t.revokedAt)}"
                                            }
                                            !t.usable(now) -> {
                                                span("dot")
                                                +"Expirat"
                                            }
                                            else -> {
                                                span("dot ok")
                                                +"Activ"
                                            }
                                        }
                                    }
                                    td("actions") {
                                        if (t.revokedAt == null) {
                                            form(
                                                action = "/admin/api-tokens/${t.id}/revoke",
                                                method = FormMethod.post,
                                                classes = "inline",
                                            ) {
                                                onSubmit =
                                                    "return confirm('Revoci tokenul? Programul care îl folosește " +
                                                    "nu mai poate trimite orarul.')"
                                                button(classes = "danger") { +"Revocă" }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                h2 {
                    id = "new"
                    +"Token nou"
                }
                form(action = "/admin/api-tokens", method = FormMethod.post, classes = "card") {
                    div("cols") {
                        style = "gap: 0 16px"
                        field("name", "Nume") {
                            textInput(name = "name") {
                                id = "name"
                                required = true
                                maxLength = MAX_TOKEN_NAME.toString()
                                placeholder = "Agentul care pregătește orarul"
                            }
                        }
                        field("scope", "Drepturi") {
                            select {
                                id = "scope"
                                name = "scope"
                                option {
                                    value = SCOPE_TIMETABLE_WRITE
                                    +"$SCOPE_TIMETABLE_WRITE (orarul semestrelor)"
                                }
                            }
                        }
                        field("expires", "Expiră pe (opțional)") {
                            dateInput(name = "expires") { id = "expires" }
                        }
                    }
                    p("hint") {
                        +"Tokenul apare o singură dată, după creare; serverul păstrează doar amprenta lui. "
                        +"Cine îl are poate înlocui orarul oricărui semestru, "
                        +"așa că dă-l doar programului care are nevoie de el."
                    }
                    div("form-actions") { button { +"Creează tokenul" } }
                }
                h2 { +"Cum se folosește" }
                div("card") { usage("<token>") }
            }
        }
    }

    action("/api-tokens", services, AdminRole.ADMIN) { admin ->
        val form = call.receiveParameters()
        val name = form["name"].orEmpty().trim()
        val scope = form["scope"].orEmpty()
        val today = Clock.System.facultyNow().date
        val expiryText = form["expires"].orEmpty().trim()
        val expiry = runCatching { LocalDate.parse(expiryText) }.getOrNull()
        val err = when {
            name.isEmpty() || name.length > MAX_TOKEN_NAME ->
                "Numele este obligatoriu și are cel mult $MAX_TOKEN_NAME de caractere."
            scope != SCOPE_TIMETABLE_WRITE -> "Drepturi necunoscute."
            expiryText.isNotEmpty() && (expiry == null || expiry <= today) ->
                "Data de expirare trebuie să fie după ziua de azi."
            else -> null
        }
        if (err != null) return@action call.respondRedirect("/admin/api-tokens?err=${err.encodeURLParameter()}")
        val (token, secret) = services.apiTokens.create(name, scope, expiry?.atStartOfDayIn(FacultyTimeZone), admin)
        services.audit.record(admin, AuditCategory.ACCOUNTS, "a creat tokenul API ${token.name} ($scope)")
        call.respondHtml {
            adminPage(
                "Token creat",
                admin,
                Section.API_TOKENS,
                context = { a(href = "/admin/api-tokens") { +"Tokenuri API" } },
            ) {
                div("card") {
                    p {
                        +"Tokenul ${token.name}. Copiază-l acum în programul care îl folosește: "
                        +"nu îl mai poți vedea după ce pleci de pe pagină."
                    }
                    p("secret") { +secret }
                    usage(secret)
                    div("form-actions") { a(href = "/admin/api-tokens", classes = "btn") { +"L-am copiat" } }
                }
            }
        }
    }

    action("/api-tokens/{id}/revoke", services, AdminRole.ADMIN) { admin ->
        val token = uuid(call.parameters["id"])?.let { services.apiTokens.revoke(it) }
            ?: return@action call.respondRedirect("/admin/api-tokens")
        services.audit.record(admin, AuditCategory.ACCOUNTS, "a revocat tokenul API ${token.name}")
        call.respondRedirect("/admin/api-tokens?msg=${"Tokenul ${token.name} a fost revocat.".encodeURLParameter()}")
    }
}

private fun FlowContent.usage(token: String) {
    p {
        +"Programul trimite fișierul CSV, în formatul din ghidul de administrare, la adresa semestrului. "
        +"Cu dry_run=true primește doar diferențele și erorile, fără să se salveze ceva; "
        +"fără el, orarul se înlocuiește dacă fișierul nu are erori."
    }
    pre("usage") {
        +"curl -X POST \\\n"
        +"  -H \"Authorization: Bearer $token\" \\\n"
        +"  -H \"Content-Type: text/csv\" \\\n"
        +"  --data-binary @orar.csv \\\n"
        +"  \"https://<server>/api/admin/v1/semesters/<semestru>/timetable?dry_run=true\""
    }
}

/**
 * The admin API: programs holding a token from the Tokenuri API page. Answers are JSON;
 * errors use the same [ApiError] body as the app API.
 */
fun Route.adminApiRoutes(services: Services) {
    route("/api/admin/v1") {
        post("/semesters/{id}/timetable") {
            val secret = call.request.header(HttpHeaders.Authorization)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring(7)?.trim()
            val token = secret?.takeIf { it.isNotEmpty() }?.let { services.apiTokens.authenticate(it) }
            if (token == null) {
                call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                throw ApiProblem(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED)
            }
            if (token.scope != SCOPE_TIMETABLE_WRITE) throw ApiProblem(HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN)

            val semesterId = call.parameters["id"].orEmpty()
            val version = services.datasets.version(semesterId)
                ?: throw ApiProblem(HttpStatusCode.NotFound, ErrorCodes.NO_SEMESTER)
            val existing = services.datasets.dataset(semesterId)
                ?: throw ApiProblem(HttpStatusCode.NotFound, ErrorCodes.NO_SEMESTER)
            val bytes = call.receiveChannel().readRemaining(MAX_SHEET_BYTES + 1L).readByteArray()
            if (bytes.size > MAX_SHEET_BYTES) {
                throw ApiProblem(HttpStatusCode.PayloadTooLarge, ApiError(ErrorCodes.BAD_REQUEST, "at most 5 MB"))
            }
            val text = try {
                SheetFile.text(bytes)
            } catch (e: SheetFileError) {
                throw ApiProblem(HttpStatusCode.BadRequest, ApiError(ErrorCodes.BAD_REQUEST, e.message.orEmpty()))
            }
            val rows = Csv.parse(text)
            val check = checkImport(existing, rows, ColumnMatch.detect(rows.firstOrNull().orEmpty()))
            val dryRun = call.request.queryParameters["dry_run"].let { it == "true" || it == "1" }
            if (dryRun) {
                return@post call.respond(
                    check.report(semesterId, dryRun = true, saved = false, version = version),
                )
            }

            val imported = check.result.dataset
                ?: throw ApiProblem(
                    HttpStatusCode.Conflict,
                    ApiError(ErrorCodes.INVALID_TIMETABLE, details = check.result.errors),
                )
            try {
                services.datasets.replace(imported, makeCurrent = false, expectedVersion = version)
            } catch (_: IllegalStateException) {
                throw ApiProblem(HttpStatusCode.Conflict, ErrorCodes.TIMETABLE_CHANGED)
            }
            services.audit.record(
                null,
                "token API ${token.name}",
                AuditCategory.TIMETABLE,
                "a importat orarul semestrului $semesterId prin API: ${diffSummary(check.diff)}",
            )
            val newVersion = services.datasets.version(semesterId) ?: version
            call.respond(check.report(semesterId, dryRun = false, saved = true, version = newVersion))
        }
    }
}
