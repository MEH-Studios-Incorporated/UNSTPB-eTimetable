// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.schedule.facultyNow
import net.mehstudios.etimetable.server.Services
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

/** Sign-in, the signed-in person's own account, the other accounts and the log. */
internal fun Route.accountRoutes(services: Services) {
    loginRoutes(services)
    ownAccountRoutes(services)
    adminListRoutes(services)
    auditRoutes(services)
}

internal fun RoutingContext.client() = Client(call.request.origin.remoteAddress, call.request.userAgent())

private fun Route.loginRoutes(services: Services) {
    get("/login") { call.respondHtml { loginPage(error = null) } }
    post("/login") {
        if (!sameOrigin()) return@post call.respond(HttpStatusCode.Forbidden)
        val form = call.receiveParameters()
        val email = form["email"].orEmpty()
        when (val result = services.admins.login(email, form["password"].orEmpty(), client())) {
            is LoginResult.Ok -> {
                call.sessions.set(AdminCookie(result.token))
                call.respondRedirect(if (result.needsCode) "/admin/login/code" else "/admin")
            }
            LoginResult.Wrong -> call.respondHtml(HttpStatusCode.Unauthorized) {
                loginPage(error = "Email sau parolă greșită.", email = email)
            }
            LoginResult.Disabled -> call.respondHtml(HttpStatusCode.Forbidden) {
                loginPage(error = "Contul este dezactivat. Cere unui administrator să îl reactiveze.", email = email)
            }
            is LoginResult.Locked -> call.respondHtml(HttpStatusCode.TooManyRequests) {
                loginPage(error = lockedText(result.minutes), email = email)
            }
        }
    }
    get("/login/code") {
        val token = call.sessions.get<AdminCookie>()?.token
        if (token == null || services.admins.pendingFor(token) == null) return@get call.respondRedirect("/admin/login")
        call.respondHtml { codePage(error = null) }
    }
    post("/login/code") {
        if (!sameOrigin()) return@post call.respond(HttpStatusCode.Forbidden)
        val token = call.sessions.get<AdminCookie>()?.token ?: return@post call.respondRedirect("/admin/login")
        val code = call.receiveParameters()["code"].orEmpty()
        when (val result = services.admins.verifySecondStep(token, code, client())) {
            is LoginResult.Ok -> call.respondRedirect("/admin")
            is LoginResult.Locked -> call.respondHtml(HttpStatusCode.TooManyRequests) {
                codePage(lockedText(result.minutes))
            }
            else -> {
                if (services.admins.pendingFor(token) == null) return@post call.respondRedirect("/admin/login")
                call.respondHtml(HttpStatusCode.Unauthorized) { codePage("Codul nu este corect sau a expirat.") }
            }
        }
    }
    post("/logout") {
        call.sessions.get<AdminCookie>()?.let { services.admins.logout(it.token) }
        call.sessions.clear<AdminCookie>()
        call.respondRedirect("/admin/login")
    }
}

private fun lockedText(minutes: Int) =
    "Prea multe încercări greșite. Contul este blocat încă $minutes ${plural(minutes, "minut", "minute")}."

private fun Route.ownAccountRoutes(services: Services) {
    // the old address, still linked from bookmarks
    page("/password", services) { _ -> call.respondRedirect("/admin/account") }

    page("/account", services) { admin ->
        val sessions = services.admins.sessions(admin.id)
        val logins = services.admins.recentLogins(admin.id)
        val twoStep = services.admins.twoStepEnabled(admin.id)
        val codesLeft = if (twoStep) services.admins.recoveryCodesLeft(admin.id) else 0
        val (msg, err) = message()
        call.respondHtml {
            adminPage("Contul meu", admin, null, context = { +admin.email }) {
                if (admin.mustChangePassword) {
                    notice("Alege o parolă nouă înainte de a continua. Parola de acum a fost dată de un administrator.")
                }
                notice(msg)
                notice(err, error = true)
                div("cols") {
                    passwordCard()
                    twoStepCard(twoStep, codesLeft)
                }
                h2 { +"Sesiuni active" }
                sessionTable(sessions, admin.sessionId)
                h2 { +"Ultimele autentificări" }
                loginTable(logins)
            }
        }
    }

    action("/account/password", services) { admin ->
        val form = call.receiveParameters()
        val password = form["password"].orEmpty()
        val result = try {
            if (password != form["confirm"]) throw AdminFormError("Parolele nu se potrivesc.")
            services.admins.changePassword(admin.id, form["current"].orEmpty(), password, keepSession = admin.sessionId)
            services.audit.record(admin, AuditCategory.ACCOUNTS, "și-a schimbat parola")
            msg("Parola a fost schimbată. Celelalte sesiuni s-au închis.")
        } catch (e: AdminFormError) {
            err(e.message.orEmpty())
        }
        call.respondRedirect("/admin/account?$result")
    }

    page("/account/two-step", services) { admin ->
        val secret = services.admins.newTotpSecret()
        call.respondHtml { twoStepSetupPage(admin, secret, error = null) }
    }
    action("/account/two-step", services) { admin ->
        val form = call.receiveParameters()
        val secret = runCatching { base32Decode(form["secret"].orEmpty()) }.getOrNull()
            ?: return@action call.respondRedirect("/admin/account/two-step")
        try {
            val codes = services.admins.enableTotp(
                admin.id,
                secret,
                form["code"].orEmpty(),
                keepSession = admin.sessionId,
            )
            services.audit.record(admin, AuditCategory.ACCOUNTS, "a pornit autentificarea în doi pași")
            call.respondHtml { recoveryCodesPage(admin, codes) }
        } catch (e: AdminFormError) {
            call.respondHtml(HttpStatusCode.UnprocessableEntity) { twoStepSetupPage(admin, secret, e.message) }
        }
    }
    action("/account/two-step/off", services) { admin ->
        val result = try {
            services.admins.disableTotp(admin.id, password = call.receiveParameters()["password"].orEmpty())
            services.audit.record(admin, AuditCategory.ACCOUNTS, "a oprit autentificarea în doi pași")
            msg("Autentificarea în doi pași este oprită.")
        } catch (e: AdminFormError) {
            err(e.message.orEmpty())
        }
        call.respondRedirect("/admin/account?$result")
    }

    action("/account/sessions/{session}/end", services) { admin ->
        val id = uuid(call.parameters["session"]) ?: return@action call.respond(HttpStatusCode.NotFound)
        if (services.admins.endSession(admin.id, id)) {
            services.audit.record(admin, AuditCategory.ACCOUNTS, "a închis una dintre sesiunile sale")
        }
        if (id == admin.sessionId) return@action call.respondRedirect("/admin/login")
        call.respondRedirect("/admin/account?${msg("Sesiunea a fost închisă.")}")
    }
    action("/account/sessions/end-others", services) { admin ->
        val ended = services.admins.endSessions(admin.id, keep = admin.sessionId)
        if (ended >
            0
        ) {
            services.audit.record(
                admin,
                AuditCategory.ACCOUNTS,
                "a închis celelalte $ended ${plural(ended, "sesiune", "sesiuni")}",
            )
        }
        call.respondRedirect("/admin/account?${msg("Celelalte sesiuni au fost închise.")}")
    }
}

private fun FlowContent.passwordCard() {
    form(action = "/admin/account/password", method = FormMethod.post, classes = "card") {
        h2 {
            style = "margin-top: 0"
            +"Parola"
        }
        field("current", "Parola actuală") {
            passwordInput(name = "current") {
                id = "current"
                required = true
                attributes["autocomplete"] = "current-password"
            }
        }
        field("password", "Parola nouă") {
            passwordInput(name = "password") {
                id = "password"
                required = true
                minLength = ADMIN_MIN_PASSWORD_LENGTH.toString()
                attributes["autocomplete"] = "new-password"
            }
            p("hint") {
                +"Cel puțin $ADMIN_MIN_PASSWORD_LENGTH caractere, fără adresa de email în ea. "
                +"O frază de câteva cuvinte e ușor de ținut minte."
            }
        }
        field("confirm", "Parola nouă, încă o dată") {
            passwordInput(name = "confirm") {
                id = "confirm"
                required = true
                attributes["autocomplete"] = "new-password"
            }
        }
        div("form-actions") { button { +"Schimbă parola" } }
    }
}

private fun FlowContent.twoStepCard(enabled: Boolean, codesLeft: Int) {
    div("card") {
        h2 {
            style = "margin-top: 0"
            +"Autentificare în doi pași"
        }
        if (enabled) {
            p {
                span("dot ok")
                +"Pornită. La conectare, după parolă, panoul cere codul din aplicația de autentificare."
            }
            p("hint") {
                +"Coduri de rezervă nefolosite: $codesLeft din 10. "
                +"Dacă rămân puține, oprește și pornește din nou autentificarea ca să primești altele."
            }
            form(action = "/admin/account/two-step/off", method = FormMethod.post) {
                field("off-password", "Parola, ca să o oprești") {
                    passwordInput(name = "password") {
                        id = "off-password"
                        required = true
                        attributes["autocomplete"] = "current-password"
                    }
                }
                button(classes = "danger") { +"Oprește autentificarea în doi pași" }
            }
        } else {
            p {
                span("dot")
                +"Oprită. Cu ea pornită, o parolă aflată de altcineva nu ajunge pentru a intra în panou."
            }
            p("hint") {
                +"Ai nevoie de o aplicație de autentificare pe telefon, de exemplu Google Authenticator, "
                +"Microsoft Authenticator sau Aegis."
            }
            div("form-actions") { a(href = "/admin/account/two-step", classes = "btn") { +"Pornește" } }
        }
    }
}

private fun FlowContent.sessionTable(sessions: List<AdminSession>, current: UUID?) {
    tableCard(
        footer = {
            if (sessions.size > 1) {
                div("pager") {
                    form(action = "/admin/account/sessions/end-others", method = FormMethod.post) {
                        button(classes = "secondary small") { +"Închide celelalte sesiuni" }
                    }
                }
            }
        },
    ) {
        table {
            thead {
                tr {
                    th { +"Browser" }
                    th { +"Adresă" }
                    th { +"Conectat" }
                    th { +"Ultima activitate" }
                    th { }
                }
            }
            tbody {
                sessions.forEach { s ->
                    tr {
                        td {
                            +describeAgent(s.userAgent)
                            if (s.id == current) {
                                +" "
                                span("tag current") { +"Aceasta" }
                            }
                        }
                        td("tnum") { +(s.ip ?: "-") }
                        td("tnum") { +shortTime(s.createdAt) }
                        td("tnum") { +shortTime(s.lastSeenAt) }
                        td("actions") {
                            form(
                                action = "/admin/account/sessions/${s.id}/end",
                                method = FormMethod.post,
                                classes = "inline",
                            ) {
                                button(classes = "secondary") { +(if (s.id == current) "Ieșire" else "Închide") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.loginTable(logins: List<LoginAttempt>) {
    tableCard {
        if (logins.isEmpty()) {
            emptyState("Nicio autentificare înregistrată")
            return@tableCard
        }
        table {
            thead {
                tr {
                    th { +"Când" }
                    th { +"Rezultat" }
                    th { +"Browser" }
                    th { +"Adresă" }
                }
            }
            tbody {
                logins.forEach { l ->
                    tr {
                        td("tnum") { +shortTime(l.at) }
                        td {
                            span(if (l.ok) "dot ok" else "dot open")
                            +(if (l.ok) "Reușită" else "Parolă sau cod greșit")
                        }
                        td { +describeAgent(l.userAgent) }
                        td("tnum") { +(l.ip ?: "-") }
                    }
                }
            }
        }
    }
}

private fun HTML.twoStepSetupPage(admin: Admin, secret: ByteArray, error: String?) {
    val uri = Totp.uri(secret, admin.email)
    adminPage("Autentificare în doi pași", admin, null, context = { a(href = "/admin/account") { +"Contul meu" } }) {
        notice(error, error = true)
        div("cols") {
            div("card") {
                h2 {
                    style = "margin-top: 0"
                    +"1. Scanează codul"
                }
                p { +"Deschide aplicația de autentificare de pe telefon, adaugă un cont nou și scanează codul." }
                div("qr") { unsafe { +Totp.qrSvg(uri) } }
                p("hint") { +"Nu poți scana? Scrie în aplicație cheia de mai jos, ca cheie bazată pe timp." }
                p("secret tnum") { +Totp.base32(secret, grouped = true) }
            }
            form(action = "/admin/account/two-step", method = FormMethod.post, classes = "card") {
                h2 {
                    style = "margin-top: 0"
                    +"2. Scrie codul din aplicație"
                }
                hiddenInput(name = "secret") { value = Totp.base32(secret) }
                field("code", "Codul de 6 cifre") {
                    textInput(name = "code") {
                        id = "code"
                        required = true
                        attributes["inputmode"] = "numeric"
                        attributes["autocomplete"] = "one-time-code"
                        attributes["pattern"] = "[0-9 ]{6,7}"
                        attributes["maxlength"] = "7"
                    }
                }
                p("hint") { +"Codul se schimbă la 30 de secunde. După pornire, celelalte sesiuni ale tale se închid." }
                div("form-actions") {
                    button { +"Pornește autentificarea" }
                    a(href = "/admin/account", classes = "btn secondary") { +"Renunță" }
                }
            }
        }
    }
}

private fun HTML.recoveryCodesPage(admin: Admin, codes: List<String>) {
    adminPage("Coduri de rezervă", admin, null, context = { a(href = "/admin/account") { +"Contul meu" } }) {
        notice("Autentificarea în doi pași este pornită.")
        div("card") {
            p {
                +"Dacă pierzi telefonul, te poți conecta cu unul dintre aceste coduri în locul codului din aplicație. "
                +"Fiecare merge o singură dată. Păstrează-le într-un loc sigur: "
                +"nu le mai poți vedea după ce pleci de pe pagină."
            }
            ul("codes tnum") { codes.forEach { li { +it } } }
            div("form-actions") { a(href = "/admin/account", classes = "btn") { +"Le-am salvat" } }
        }
    }
}

private fun HTML.loginPage(error: String?, email: String = "") {
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
                        value = email
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

private fun HTML.codePage(error: String?) {
    adminPage("Codul de autentificare", null, null) {
        div("login") {
            img(src = "/admin/static/faima-seal.png", alt = "Sigiliul FAIMA")
            h1 { +"Codul de autentificare" }
            p("sub") { +"Scrie codul de 6 cifre din aplicația de pe telefon." }
            notice(error, error = true)
            form(action = "/admin/login/code", method = FormMethod.post) {
                field("code", "Cod") {
                    textInput(name = "code") {
                        id = "code"
                        required = true
                        attributes["autocomplete"] = "one-time-code"
                        attributes["autofocus"] = "autofocus"
                    }
                }
                button { +"Continuă" }
            }
            p("hint") {
                style = "text-align: center"
                +"Ai pierdut telefonul? Scrie unul dintre codurile de rezervă, de forma ABCDE-FGHJK."
            }
            form(action = "/admin/logout", method = FormMethod.post, classes = "back") {
                button(classes = "link") { +"Renunță" }
            }
        }
    }
}

/** Browser and system from a user agent, enough to recognise one's own devices. */
internal fun describeAgent(agent: String?): String {
    if (agent.isNullOrBlank()) return "Necunoscut"
    val browser = when {
        "Edg/" in agent -> "Edge"
        "OPR/" in agent -> "Opera"
        "Firefox/" in agent -> "Firefox"
        "Chrome/" in agent -> "Chrome"
        "Safari/" in agent -> "Safari"
        else -> "Alt browser"
    }
    val system = when {
        "Android" in agent -> "Android"
        "iPhone" in agent || "iPad" in agent -> "iOS"
        "Windows" in agent -> "Windows"
        "Mac OS X" in agent || "Macintosh" in agent -> "macOS"
        "Linux" in agent -> "Linux"
        else -> null
    }
    return listOfNotNull(browser, system).joinToString(", ")
}

/** "azi 08:51", "ieri 14:55" or "3 noiembrie 2026, 14:55", in Bucharest time. */
internal fun shortTime(at: Instant): String {
    val local = at.toLocalDateTime(FacultyTimeZone)
    val today = Clock.System.facultyNow().date
    val time = local.time.toString().take(5)
    return when (local.date) {
        today -> "azi $time"
        today.minusDays(1) -> "ieri $time"
        else -> "${longDate(local.date)}, $time"
    }
}

private fun kotlinx.datetime.LocalDate.minusDays(n: Int) = kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() - n)

internal fun uuid(text: String?): UUID? = runCatching { UUID.fromString(text) }.getOrNull()

private fun base32Decode(text: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val clean = text.uppercase().filter { it in alphabet }
    var buffer = 0
    var bits = 0
    val out = java.io.ByteArrayOutputStream()
    for (ch in clean) {
        buffer = (buffer shl 5) or alphabet.indexOf(ch)
        bits += 5
        if (bits >= 8) {
            out.write((buffer shr (bits - 8)) and 0xff)
            bits -= 8
        }
    }
    val bytes = out.toByteArray()
    require(bytes.size == 20)
    return bytes
}

private fun RoutingContext.message(): Pair<String?, String?> = call.parameters["msg"] to call.parameters["err"]

private fun msg(text: String) = "msg=${text.encodeURLParameter()}"

private fun err(text: String) = "err=${text.encodeURLParameter()}"
