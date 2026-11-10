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
import kotlinx.html.*
import net.mehstudios.etimetable.server.Services

private fun roleName(role: AdminRole) = if (role == AdminRole.ADMIN) "Administrator" else "Editor"

/** The dashboard accounts, for administrators. */
internal fun Route.adminListRoutes(services: Services) {
    page("/admins", services, AdminRole.ADMIN) { admin ->
        val accounts = services.admins.list()
        call.respondHtml {
            adminPage(
                "Administratori",
                admin,
                Section.ADMINS,
                context = { +"Editorii schimbă orarul, anunțurile și evenimentele; administratorii, și conturile." },
                actions = { a(href = "#new", classes = "btn") { +"Cont nou" } },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                tableCard {
                    table {
                        thead {
                            tr {
                                th { +"Cont" }
                                th { +"Rol" }
                                th { +"Doi pași" }
                                th { +"Ultima conectare" }
                                th { +"Stare" }
                            }
                        }
                        tbody {
                            accounts.forEach { a ->
                                tr {
                                    td {
                                        a(href = "/admin/admins/${a.id}") { +a.email }
                                        if (a.id == admin.id) {
                                            +" "
                                            span("tag current") { +"Tu" }
                                        }
                                    }
                                    td { +roleName(a.role) }
                                    td { +(if (a.twoStep) "Pornită" else "Oprită") }
                                    td("tnum") { +(a.lastSignIn?.let(::shortTime) ?: "Niciodată") }
                                    td {
                                        span(if (a.disabled) "dot" else "dot ok")
                                        +(if (a.disabled) "Dezactivat" else "Activ")
                                    }
                                }
                            }
                        }
                    }
                }
                h2 {
                    id = "new"
                    +"Cont nou"
                }
                form(action = "/admin/admins", method = FormMethod.post, classes = "card") {
                    div("cols") {
                        style = "gap: 0 16px"
                        field("email", "Email") {
                            emailInput(name = "email") {
                                id = "email"
                                required = true
                            }
                        }
                        field("role", "Rol") {
                            select {
                                id = "role"
                                name = "role"
                                option {
                                    value = "editor"
                                    +"Editor: orar, anunțuri, evenimente"
                                }
                                option {
                                    value = "admin"
                                    +"Administrator: tot, inclusiv conturile"
                                }
                            }
                        }
                    }
                    p("hint") {
                        +"Panoul face o parolă temporară, pe care o vezi o singură dată. "
                        +"Persoana își alege parola ei la prima conectare."
                    }
                    div("form-actions") { button { +"Creează contul" } }
                }
            }
        }
    }

    action("/admins", services, AdminRole.ADMIN) { admin ->
        val form = call.receiveParameters()
        val role = if (form["role"] == "admin") AdminRole.ADMIN else AdminRole.EDITOR
        try {
            val (created, password) = services.admins.createWithTemporaryPassword(form["email"].orEmpty(), role)
            services.audit.record(
                admin,
                AuditCategory.ACCOUNTS,
                "a creat contul ${created.email} (${roleName(role).lowercase()})",
            )
            call.respondHtml { temporaryPasswordPage(admin, created.email, password, created = true) }
        } catch (e: AdminFormError) {
            call.respondRedirect("/admin/admins?err=${e.message.orEmpty().encodeURLParameter()}")
        }
    }

    page("/admins/{id}", services, AdminRole.ADMIN) { admin ->
        val account = uuid(call.parameters["id"])?.let { services.admins.account(it) }
            ?: return@page call.respond(HttpStatusCode.NotFound)
        val sessions = services.admins.sessions(account.id)
        val logins = services.admins.recentLogins(account.id, limit = 5)
        val self = account.id == admin.id
        call.respondHtml {
            adminPage(
                account.email,
                admin,
                Section.ADMINS,
                context = {
                    a(href = "/admin/admins") { +"Administratori" }
                    +" · ${roleName(account.role)}, ${if (account.disabled) "dezactivat" else "activ"}"
                },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                if (self) {
                    notice(
                        "Acesta este contul tău. Parola, sesiunile și autentificarea în doi pași se schimbă din Contul meu.",
                    )
                }
                div("cols") {
                    div("card") {
                        h2 {
                            style = "margin-top: 0"
                            +"Rol"
                        }
                        form(action = "/admin/admins/${account.id}/role", method = FormMethod.post) {
                            field("role", "Rol") {
                                select {
                                    id = "role"
                                    name = "role"
                                    disabled = self
                                    AdminRole.entries.forEach { r ->
                                        option {
                                            value = r.name.lowercase()
                                            selected = r == account.role
                                            +roleName(r)
                                        }
                                    }
                                }
                            }
                            if (!self) button(classes = "secondary") { +"Schimbă rolul" }
                        }
                        p("hint") {
                            +"Cont creat ${shortTime(
                                account.createdAt,
                            )}. Ultima conectare: ${account.lastSignIn?.let(::shortTime) ?: "niciodată"}."
                        }
                    }
                    div("card") {
                        h2 {
                            style = "margin-top: 0"
                            +"Acces"
                        }
                        p {
                            +"Sesiuni active: ${sessions.size}. "
                            +"Autentificare în doi pași: ${if (account.twoStep) "pornită" else "oprită"}."
                        }
                        if (!self) {
                            div("form-actions") {
                                style = "margin-top: 0"
                                postButton("/admin/admins/${account.id}/password", "Parolă temporară nouă", "secondary")
                                if (account.twoStep) {
                                    postButton(
                                        "/admin/admins/${account.id}/two-step/reset",
                                        "Resetează autentificarea în doi pași",
                                        "secondary",
                                        confirm = "Persoana se va conecta doar cu parola, până o pornește din nou.",
                                    )
                                }
                                if (sessions.isNotEmpty()) {
                                    postButton(
                                        "/admin/admins/${account.id}/sessions/end",
                                        "Închide sesiunile",
                                        "secondary",
                                    )
                                }
                                if (account.disabled) {
                                    postButton("/admin/admins/${account.id}/enable", "Reactivează", "secondary")
                                } else {
                                    postButton(
                                        "/admin/admins/${account.id}/disable",
                                        "Dezactivează",
                                        "danger",
                                        confirm = "Contul nu se va mai putea conecta până îl reactivezi.",
                                    )
                                }
                                postButton(
                                    "/admin/admins/${account.id}/delete",
                                    "Șterge contul",
                                    "danger",
                                    confirm = "Ștergi contul ${account.email}? Nu se poate anula.",
                                )
                            }
                            p("hint") {
                                +"O parolă temporară nouă închide toate sesiunile; "
                                +"persoana își alege alta la conectare."
                            }
                        }
                    }
                }
                h2 { +"Ultimele autentificări" }
                tableCard {
                    if (logins.isEmpty()) {
                        emptyState("Nicio autentificare înregistrată")
                    } else {
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
            }
        }
    }

    /** Runs [change] on another account; an admin cannot lock themselves out from here. */
    fun Route.manage(
        path: String,
        change: suspend io.ktor.server.routing.RoutingContext.(Admin, AdminAccount) -> String,
    ) {
        action("/admins/{id}$path", services, AdminRole.ADMIN) { admin ->
            val account = uuid(call.parameters["id"])?.let { services.admins.account(it) }
                ?: return@action call.respond(HttpStatusCode.NotFound)
            if (account.id == admin.id) return@action call.respond(HttpStatusCode.Forbidden)
            val query = try {
                "msg=${change(admin, account).encodeURLParameter()}"
            } catch (e: AdminFormError) {
                "err=${e.message.orEmpty().encodeURLParameter()}"
            }
            val back = if (path == "/delete" &&
                query.startsWith("msg")
            ) {
                "/admin/admins"
            } else {
                "/admin/admins/${account.id}"
            }
            call.respondRedirect("$back?$query")
        }
    }

    manage("/role") { admin, account ->
        val role = if (call.receiveParameters()["role"] == "admin") AdminRole.ADMIN else AdminRole.EDITOR
        services.admins.setRole(account.id, role)
        services.audit.record(
            admin,
            AuditCategory.ACCOUNTS,
            "a schimbat rolul lui ${account.email} în ${roleName(role).lowercase()}",
        )
        "Rolul a fost schimbat."
    }
    manage("/disable") { admin, account ->
        services.admins.setDisabled(account.id, true)
        services.audit.record(admin, AuditCategory.ACCOUNTS, "a dezactivat contul ${account.email}")
        "Contul a fost dezactivat și sesiunile lui s-au închis."
    }
    manage("/enable") { admin, account ->
        services.admins.setDisabled(account.id, false)
        services.audit.record(admin, AuditCategory.ACCOUNTS, "a reactivat contul ${account.email}")
        "Contul a fost reactivat."
    }
    manage("/two-step/reset") { admin, account ->
        services.admins.disableTotp(account.id, password = null)
        services.audit.record(
            admin,
            AuditCategory.ACCOUNTS,
            "a resetat autentificarea în doi pași pentru ${account.email}",
        )
        "Autentificarea în doi pași a fost oprită pentru acest cont."
    }
    manage("/sessions/end") { admin, account ->
        val ended = services.admins.endSessions(account.id)
        services.audit.record(admin, AuditCategory.ACCOUNTS, "a închis sesiunile lui ${account.email}")
        "$ended ${plural(ended, "sesiune închisă", "sesiuni închise")}."
    }
    manage("/delete") { admin, account ->
        services.admins.delete(account.id)
        services.audit.record(admin, AuditCategory.ACCOUNTS, "a șters contul ${account.email}")
        "Contul ${account.email} a fost șters."
    }
    action("/admins/{id}/password", services, AdminRole.ADMIN) { admin ->
        val account = uuid(call.parameters["id"])?.let { services.admins.account(it) }
            ?: return@action call.respond(HttpStatusCode.NotFound)
        if (account.id == admin.id) return@action call.respond(HttpStatusCode.Forbidden)
        val password = services.admins.resetPassword(account.id)
        services.audit.record(admin, AuditCategory.ACCOUNTS, "a dat o parolă temporară nouă lui ${account.email}")
        call.respondHtml { temporaryPasswordPage(admin, account.email, password, created = false) }
    }
}

private fun FlowContent.postButton(action: String, label: String, kind: String, confirm: String? = null) {
    form(action = action, method = FormMethod.post, classes = "inline") {
        if (confirm != null) onSubmit = "return confirm('${confirm.replace("'", "\\'")}')"
        button(classes = kind) { +label }
    }
}

private fun HTML.temporaryPasswordPage(admin: Admin, email: String, password: String, created: Boolean) {
    adminPage(
        if (created) "Cont creat" else "Parolă temporară",
        admin,
        Section.ADMINS,
        context = { a(href = "/admin/admins") { +"Administratori" } },
    ) {
        div("card") {
            p {
                +(
                    if (created) {
                        "Contul $email a fost creat. "
                    } else {
                        "Parola lui $email a fost înlocuită și sesiunile lui s-au închis. "
                    }
                    )
                +"Dă-i persoanei parola temporară de mai jos pe o cale sigură: "
                +"în persoană sau la telefon, nu în același email cu adresa."
            }
            p("secret tnum") { +password }
            p("hint") { +"Parola nu mai apare după ce pleci de pe pagină. La conectare, panoul îi cere o parolă nouă." }
            div("form-actions") { a(href = "/admin/admins", classes = "btn") { +"Gata" } }
        }
    }
}

/** The log of changes, for administrators. */
internal fun Route.auditRoutes(services: Services) {
    page("/audit", services, AdminRole.ADMIN) { admin ->
        val category = AuditCategory.entries.firstOrNull { it.name.lowercase() == call.parameters["category"] }
        val filter =
            AuditFilter(category, call.parameters["who"]?.takeIf { it.isNotBlank() }, call.parameters["q"].orEmpty())
        val paging = call.parameters.paging(services.audit.count(filter), defaultSize = 25)
        val entries = services.audit.page(filter, paging.offset, paging.size)
        val authors = services.audit.authors()
        call.respondHtml {
            adminPage(
                "Jurnal",
                admin,
                Section.AUDIT,
                context = { +"Cine a schimbat ce și când. Intrările nu pot fi modificate sau șterse din panou." },
            ) {
                tableCard(
                    filters = {
                        form(action = "/admin/audit", method = FormMethod.get, classes = "filters") {
                            searchInput(name = "q") {
                                value = filter.text
                                placeholder = "Caută în jurnal"
                                attributes["aria-label"] = "Caută în jurnal"
                            }
                            select {
                                name = "category"
                                attributes["aria-label"] = "Categorie"
                                option {
                                    value = ""
                                    +"Toate categoriile"
                                }
                                AuditCategory.entries.forEach { c ->
                                    option {
                                        value = c.name.lowercase()
                                        selected = c == category
                                        +c.label
                                    }
                                }
                            }
                            select {
                                name = "who"
                                attributes["aria-label"] = "Cine"
                                option {
                                    value = ""
                                    +"Toți"
                                }
                                authors.forEach { e ->
                                    option {
                                        value = e
                                        selected = e == filter.adminEmail
                                        +e
                                    }
                                }
                            }
                            button(classes = "secondary") { +"Filtrează" }
                        }
                    },
                    footer = { pager(paging, "/admin/audit", call.request.queryParameters) },
                ) {
                    if (entries.isEmpty()) {
                        emptyState(
                            "Nicio intrare",
                            "Jurnalul începe cu prima modificare făcută după actualizarea panoului.",
                        )
                    } else {
                        table {
                            thead {
                                tr {
                                    th { +"Când" }
                                    th { +"Cine" }
                                    th { +"Ce" }
                                    th { +"Categorie" }
                                }
                            }
                            tbody {
                                entries.forEach { e ->
                                    tr {
                                        td("tnum") {
                                            style = "white-space: nowrap"
                                            +shortTime(e.at)
                                        }
                                        td { +e.adminEmail }
                                        td { +e.summary }
                                        td { span("tag") { +e.category.label } }
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
