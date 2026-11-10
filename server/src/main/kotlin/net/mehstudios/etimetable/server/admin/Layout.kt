// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.server.html.respondHtml
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.img
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.picture
import kotlinx.html.script
import kotlinx.html.source
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

/** A dashboard section in the sidebar, under its [group] heading when it starts one. */
enum class Section(val label: String, val href: String, val group: String?, val icon: String) {
    OVERVIEW("Prezentare", "/admin", null, "M4 11l8-6 8 6v8H4z"),
    SEMESTERS("Semestre și orar", "/admin/semesters", "Orar", "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4"),
    SCHEDULE("Gestionare orar", "/admin/schedule", null, "M4 4h16v16H4zM4 10h16M10 4v16"),
    SUBJECTS("Materii", "/admin/subjects", null, "M5 4h13v16H5zM9 4v16"),
    PROFESSORS("Profesori", "/admin/professors", null, "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 20c1-4 4-5 8-5s7 1 8 5"),
    ROOMS("Săli", "/admin/rooms", null, "M6 20V4h12v16M4 20h16M14 12v1"),
    ANNOUNCEMENTS("Anunțuri", "/admin/announcements", "Comunicare", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4zM10 21h4"),
    EVENTS("Evenimente", "/admin/events", null, "M4 8h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4z"),
    REPORTS("Probleme de orar", "/admin/reports", null, "M5 21V4M5 4h13l-3 4 3 4H5"),
    STUDENTS(
        "Studenți",
        "/admin/students",
        "Conturi",
        "M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 19c1-3 3-4 6-4s5 1 6 4M16 11a3 3 0 1 0 0-6M18 15c2 1 3 2 3 4",
    ),
    ADMINS("Administratori", "/admin/admins", null, "M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z"),
    AUDIT("Jurnal", "/admin/audit", null, "M8 4h11v16H8zM5 8h3M5 12h3M5 16h3M11 9h5M11 13h5"),
}

/** Sections only administrators see: other admins, the log, and the students' personal data. */
private val ADMIN_ONLY = setOf(Section.ADMINS, Section.STUDENTS, Section.AUDIT)

/**
 * Every dashboard page: the sidebar with the sections, a top bar with the teaching week and
 * the theme switch, then a header with [pageTitle], an optional [context] line and [actions]
 * on the right, then [content].
 */
fun HTML.adminPage(
    pageTitle: String,
    admin: Admin?,
    current: Section?,
    context: (FlowContent.() -> Unit)? = null,
    actions: (FlowContent.() -> Unit)? = null,
    content: FlowContent.() -> Unit,
) {
    lang = "ro"
    admin?.shell?.theme?.let { attributes["data-theme"] = it }
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "robots", content = "noindex")
        title("$pageTitle · eTimetable")
        link(rel = "icon", href = "/admin/static/faima-seal.png")
        style { unsafe { +(BASE_STYLES + ADMIN_STYLES + FEATURE_STYLES.joinToString("\n")) } }
        script(src = "/admin/static/admin.js") { defer = true }
    }
    body(if (admin == null) "plain" else "shell") {
        if (admin != null) {
            sidebar(admin, current)
            div("page") {
                topBar(admin.shell)
                main {
                    header("page-head") {
                        div {
                            h1 { +pageTitle }
                            if (context != null) div("context") { context() }
                        }
                        if (actions != null) div("actions") { actions() }
                    }
                    content()
                }
            }
        } else {
            main { content() }
        }
    }
}

private fun FlowContent.sidebar(admin: Admin, current: Section?) {
    aside("side") {
        a(href = "/admin", classes = "brand") {
            img(src = "/admin/static/faima-seal-white.png", alt = "")
            span {
                span("name") { +"eTimetable" }
                span("sub") { +"Administrare FAIMA" }
            }
        }
        nav {
            attributes["aria-label"] = "Secțiuni"
            Section.entries.filter { it !in ADMIN_ONLY || admin.role == AdminRole.ADMIN }.forEach { section ->
                if (section.group != null) div("group") { +section.group }
                a(href = section.href, classes = if (section == current) "active" else null) {
                    if (section == current) attributes["aria-current"] = "page"
                    unsafe { +svgIcon(section.icon) }
                    span("label") { +section.label }
                    val open = admin.shell?.openReports ?: 0
                    if (section == Section.REPORTS && open > 0) {
                        span("badge tnum") {
                            attributes["title"] = "$open ${plural(open, "problemă deschisă", "probleme deschise")}"
                            +open.toString()
                        }
                    }
                }
            }
        }
        div("who") {
            span("email") { +admin.email }
            span("role") { +(if (admin.role == AdminRole.ADMIN) "Administrator" else "Editor") }
            div("links") {
                a(href = "/admin/account") { +"Contul meu" }
                a(href = "/") {
                    attributes["target"] = "_blank"
                    +"Pagina publică"
                }
                form(action = "/admin/logout", method = FormMethod.post) {
                    button(classes = "link") { +"Ieșire" }
                }
            }
        }
    }
}

/** The teaching week as the app shows it, the semester, and the theme switch. */
private fun FlowContent.topBar(shell: Shell?) {
    div("topbar") {
        if (shell?.week != null) {
            span("ticket") {
                span("num tnum") { +shell.week.toString() }
                span("text") { +(shell.weekLabel ?: "") }
            }
        } else if (shell?.weekLabel != null) {
            span("ticket") { span("text") { +shell.weekLabel } }
        }
        if (shell?.semester != null) span("semester") { +shell.semester }
        span("spacer")
        button(type = ButtonType.button, classes = "secondary small theme-toggle") {
            attributes["data-theme-toggle"] = ""
            attributes["hidden"] = ""
            attributes["aria-label"] = "Schimbă tema"
            unsafe { +svgIcon(ICON_MOON, 16) }
            span { +"Temă" }
        }
    }
}

fun FlowContent.notice(text: String?, error: Boolean = false) {
    if (text.isNullOrBlank()) return
    p(if (error) "notice error" else "notice") { +text }
}

/**
 * docs/design.md tokens, shared by the dashboard and the public page. The font is
 * served by the server itself, so neither page makes requests to other sites.
 */
val BASE_STYLES = """
@font-face { font-family: "Libre Franklin"; src: url("/admin/static/libre-franklin.ttf") format("truetype"); font-weight: 100 900; }
:root { --bg: #FAFAF9; --surface: #FFFFFF; --alt: #F2F2F0; --border: #E3E3E0; --strong: #D4D4D0; --text: #292929;
  --muted: #5C5C5A; --primary: #1D6FA5; --primary-soft: #E6F0F7; --now: #D92A21; --change: #F5C753; --ink: #1E1E1E;
  --faima: #0D443B; }
* { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; }
body { margin: 0; background: var(--bg); color: var(--text); font: 400 15px/1.5 "Libre Franklin", system-ui, sans-serif; }
a { color: var(--primary); text-underline-offset: 2px; }
h1, h2, h3 { font-weight: 600; letter-spacing: -0.01em; }
.tnum { font-variant-numeric: tabular-nums; }
.btn, button { display: inline-flex; align-items: center; gap: 6px; padding: 9px 16px; border: 1px solid transparent;
  border-radius: 6px; background: var(--primary); color: #FFFFFF; font: 600 14px/20px "Libre Franklin", system-ui, sans-serif;
  cursor: pointer; text-decoration: none; white-space: nowrap; }
.btn:hover, button:hover { filter: brightness(0.94); }
.btn.secondary, button.secondary { background: var(--surface); color: var(--text); border-color: var(--strong); }
.btn.danger, button.danger { background: var(--surface); color: var(--now); border-color: var(--border); }
button.link { padding: 0; border: 0; background: none; color: inherit; font-weight: 500; }
"""

/**
 * Styles that belong to one feature live next to its routes as a constant and are
 * listed here, one line each, so features do not edit the same block of CSS.
 */
private val FEATURE_STYLES: List<String> = listOf(
    WEEK_GRID_STYLES,
)

private val ADMIN_STYLES = """
:root { --side: #292929; --side-line: #292929; --side-hover: #3A3A3A; --ok: #2F7A4B; --error-soft: #FBEAE9;
  --error-text: #8E1B15; --row-hover: #F7F7F5; --shadow: 0 8px 24px rgba(0, 0, 0, 0.08); color-scheme: light; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme=light]) { --bg: #0A0A0A; --surface: #141414; --alt: #1E1E1E; --border: #2A2A2A; --strong: #3A3A3A;
    --text: #EDEDED; --muted: #9A9A9A; --primary: #2EBFF6; --primary-soft: #10202B; --now: #F0564C; --ink: #141414;
    --side: #141414; --side-line: #2A2A2A; --side-hover: #2A2A2A; --ok: #5DB57E; --error-soft: #2A1514; --error-text: #F6A39D;
    --row-hover: #1A1A1A; --shadow: 0 8px 24px rgba(0, 0, 0, 0.5); color-scheme: dark; }
}
:root[data-theme=dark] { --bg: #0A0A0A; --surface: #141414; --alt: #1E1E1E; --border: #2A2A2A; --strong: #3A3A3A;
  --text: #EDEDED; --muted: #9A9A9A; --primary: #2EBFF6; --primary-soft: #10202B; --now: #F0564C; --ink: #141414;
  --side: #141414; --side-line: #2A2A2A; --side-hover: #2A2A2A; --ok: #5DB57E; --error-soft: #2A1514; --error-text: #F6A39D;
  --row-hover: #1A1A1A; --shadow: 0 8px 24px rgba(0, 0, 0, 0.5); color-scheme: dark; }
.btn, button { transition: filter 150ms, background-color 150ms, border-color 150ms; }
:root[data-theme=dark] .btn:not(.secondary):not(.danger), :root[data-theme=dark] button:not(.secondary):not(.danger):not(.link) { color: #0A0A0A; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme=light]) .btn:not(.secondary):not(.danger), :root:not([data-theme=light]) button:not(.secondary):not(.danger):not(.link) { color: #0A0A0A; }
}
.btn.secondary:hover, button.secondary:hover { filter: none; border-color: var(--muted); }
.btn.small, button.small { padding: 5px 10px; font-size: 13px; line-height: 18px; }
:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
body.shell { display: flex; flex-wrap: wrap; align-content: flex-start; min-height: 100vh; }
.side { flex: 0 0 240px; min-height: 100vh; display: flex; flex-direction: column; gap: 18px; background: var(--side); border-right: 1px solid var(--side-line);
  color: #D6D6D2; padding: 20px 14px; }
.side .brand { display: flex; gap: 10px; align-items: center; color: #FFFFFF; text-decoration: none; padding: 0 8px; }
.side .brand img { width: 34px; height: 34px; }
.side .brand .name { display: block; font-weight: 700; font-size: 16px; line-height: 20px; }
.side .brand .sub { display: block; font-size: 12px; line-height: 16px; color: #A3A39F; }
.side nav { display: flex; flex-direction: column; gap: 2px; }
.side nav .group { font-size: 11px; line-height: 16px; font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase;
  color: #8C8C88; padding: 14px 10px 4px; }
.side nav a { display: flex; align-items: center; gap: 10px; color: #D6D6D2; text-decoration: none; padding: 8px 10px; border-radius: 6px;
  font-size: 14px; line-height: 20px; font-weight: 500; transition: background-color 150ms, color 150ms; }
.side nav a svg { flex: none; }
.side nav a .label { flex: 1; min-width: 0; }
.side nav a:hover { background: var(--side-hover); color: #FFFFFF; }
.side nav a.active { background: var(--side-hover); color: #FFFFFF; box-shadow: inset 3px 0 0 #2EBFF6; }
.side .badge { font-size: 12px; line-height: 16px; font-weight: 600; background: #F0564C; color: #0A0A0A; border-radius: 8px; padding: 1px 7px; }
.side .who { margin-top: auto; padding: 14px 10px 0; border-top: 1px solid #3A3A3A; font-size: 13px; line-height: 18px; }
.side .who .email { display: block; color: #FFFFFF; overflow-wrap: anywhere; }
.side .who .role { display: block; color: #A3A39F; margin-bottom: 8px; }
.side .who .links { display: flex; flex-wrap: wrap; gap: 4px 12px; }
.side .who a, .side .who button.link { color: #D6D6D2; font: 400 13px/18px inherit; text-decoration: none; }
.side .who a:hover, .side .who button.link:hover { color: #FFFFFF; text-decoration: underline; }
.side .who form { margin: 0; }
.page { flex: 999 1 560px; min-width: 0; display: flex; flex-direction: column; }
.topbar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 16px; padding: 10px 32px; border-bottom: 1px solid var(--border);
  background: var(--surface); font-size: 13px; line-height: 18px; }
.topbar .ticket { display: inline-flex; align-items: stretch; border: 1px solid var(--border); border-radius: 6px; overflow: hidden; }
.topbar .ticket .num { background: var(--text); color: var(--bg); font-weight: 700; padding: 3px 9px; }
.topbar .ticket .text { padding: 3px 10px; font-weight: 500; }
.topbar .semester { color: var(--muted); }
.topbar .spacer { flex: 1; }
main { box-sizing: border-box; width: 100%; padding: 28px 32px 72px; }
main > * { animation: rise 180ms ease-out both; }
@keyframes rise { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: none; } }
.page-head { display: flex; flex-wrap: wrap; gap: 12px 24px; align-items: flex-end; justify-content: space-between; margin-bottom: 24px; }
.page-head h1 { font-size: 26px; line-height: 32px; margin: 0; }
.page-head .context { color: var(--muted); margin-top: 4px; }
.page-head .actions { display: flex; flex-wrap: wrap; gap: 8px; }
h2 { font-size: 17px; line-height: 22px; margin: 32px 0 12px; }
h2 .count { color: var(--muted); font-weight: 400; margin-left: 6px; }
.card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 18px 20px; margin-bottom: 16px; }
.card > :first-child { margin-top: 0; }
.cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; align-items: start; }
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 8px; }
.stat { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 14px 16px; }
.stat .value { display: block; font-size: 28px; line-height: 34px; font-weight: 600; font-variant-numeric: tabular-nums; }
.stat .label { display: block; color: var(--muted); font-size: 13px; }
.today { display: flex; flex-wrap: wrap; align-items: stretch; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; margin-bottom: 16px; }
.today .num { display: flex; align-items: center; justify-content: center; min-width: 76px; background: var(--alt); color: var(--text); border-right: 1px solid var(--border); font-size: 32px; font-weight: 700; font-variant-numeric: tabular-nums; }
.today .text { padding: 14px 18px; }
.today .text strong { display: block; font-size: 17px; }
.today .text span { color: var(--muted); }
ul.todo { list-style: none; margin: 0; padding: 0; }
ul.todo li { display: flex; gap: 12px; padding: 10px 0; border-top: 1px solid var(--border); }
ul.todo li:first-child { border-top: 0; padding-top: 0; }
ul.todo .mark { flex: none; width: 8px; height: 8px; margin-top: 8px; border-radius: 50%; background: var(--change); }
ul.todo .mark.ok { background: var(--ok); }
ul.todo .mark.bad { background: var(--now); }
table { width: 100%; border-collapse: separate; border-spacing: 0; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
th, td { text-align: left; padding: 10px 14px; border-bottom: 1px solid var(--border); vertical-align: top; }
th { font-size: 12px; line-height: 16px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; background: var(--alt); }
thead tr:first-child th:first-child { border-top-left-radius: 8px; }
thead tr:first-child th:last-child { border-top-right-radius: 8px; }
tr:last-child td { border-bottom: 0; }
tbody tr { transition: background-color 150ms; }
tbody tr:hover { background: var(--row-hover); }
td > a:first-child { color: var(--text); font-weight: 500; text-decoration: none; }
td > a:first-child:hover { color: var(--primary); text-decoration: underline; }
td .hint { margin-top: 2px; }
td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
td.actions { text-align: right; white-space: nowrap; }
td form, .inline { display: inline; margin: 0; }
td .btn, td button { padding: 5px 10px; font-size: 13px; }
.scroll { overflow-x: auto; }
.table-card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; margin-bottom: 16px; }
.table-card table { border: 0; border-radius: 0; }
.table-card thead tr:first-child th { border-radius: 0; }
.table-card tr:last-child td { border-bottom: 1px solid var(--border); }
.table-bar { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; padding: 12px 14px; border-bottom: 1px solid var(--border); }
.table-bar .filters { margin: 0; flex: 1 1 auto; }
.tabs { display: inline-flex; flex-wrap: wrap; gap: 2px; padding: 3px; background: var(--alt); border-radius: 8px; }
.tabs a { padding: 5px 12px; border-radius: 6px; color: var(--muted); text-decoration: none; font-size: 14px; line-height: 20px; font-weight: 500;
  transition: background-color 150ms, color 150ms; }
.tabs a:hover { color: var(--text); }
.tabs a.active { background: var(--surface); color: var(--text); box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08); }
.tabs .n { color: var(--muted); font-weight: 400; margin-left: 4px; font-variant-numeric: tabular-nums; }
.pager { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 20px; padding: 10px 14px; font-size: 13px; line-height: 18px; color: var(--muted); }
.pager .size { display: inline-flex; align-items: center; gap: 8px; margin: 0; }
.pager .size label { margin: 0; font-size: 13px; font-weight: 500; }
.pager .size select { width: auto; padding: 4px 8px; font-size: 13px; }
.pager .pages { display: inline-flex; align-items: center; gap: 2px; margin-left: auto; }
.pager .pages a, .pager .pages span { display: inline-flex; align-items: center; justify-content: center; min-width: 32px; height: 32px; padding: 0 6px;
  border-radius: 6px; color: var(--text); text-decoration: none; font-weight: 500; }
.pager .pages a:hover { background: var(--alt); }
.pager .pages .current { background: var(--primary-soft); color: var(--primary); font-weight: 700; }
.pager .pages .disabled { color: var(--strong); }
.pager .pages .gap { min-width: 20px; color: var(--muted); }
.qr { width: 200px; margin: 8px 0 12px; }
.qr svg { display: block; border-radius: 6px; }
.secret { font: 600 17px/24px ui-monospace, Menlo, Consolas, monospace; letter-spacing: 0.04em; background: var(--alt);
  border: 1px solid var(--border); border-radius: 6px; padding: 8px 12px; display: inline-block; overflow-wrap: anywhere; }
ul.codes { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 8px; list-style: none; padding: 0; margin: 12px 0;
  font: 600 15px/22px ui-monospace, Menlo, Consolas, monospace; }
ul.codes li { background: var(--alt); border-radius: 6px; padding: 6px 10px; }
form.back { margin: 8px 0 0; text-align: center; }
form.back button.link { color: var(--muted); font-size: 13px; }
.empty { padding: 32px 20px; text-align: center; color: var(--muted); }
.empty strong { display: block; color: var(--text); font-size: 15px; margin-bottom: 4px; }
.empty p { margin: 0 auto; max-width: 440px; }
.empty .form-actions { justify-content: center; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: 1px; background: var(--muted); }
.dot.open { background: var(--now); } .dot.ok { background: var(--ok); }
.field { margin: 0 0 14px; }
label { display: block; font-size: 13px; font-weight: 600; margin: 0 0 6px; }
label.check { display: flex; gap: 8px; align-items: center; font-weight: 500; font-size: 14px; }
input[type=text], input[type=time], input[type=url], input[type=email], input[type=password], input[type=date], input[type=search], input[type=number], textarea, select {
  width: 100%; max-width: 560px; padding: 9px 12px; border: 1px solid var(--strong); border-radius: 6px; background: var(--surface);
  font: inherit; color: var(--text); transition: border-color 150ms; }
input:autofill { filter: none; box-shadow: 0 0 0 100px var(--primary-soft) inset; -webkit-text-fill-color: var(--text); }
input:focus, textarea:focus, select:focus { outline: 2px solid var(--primary-soft); border-color: var(--primary); }
textarea { max-width: 100%; min-height: 140px; font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13px; line-height: 1.5; }
.hint { font-size: 13px; color: var(--muted); margin: 6px 0 0; }
.form-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.tag { display: inline-block; padding: 1px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; background: var(--alt); color: var(--muted); }
.tag.current { background: var(--primary); color: var(--surface); }
.tag.pinned { background: var(--change); color: #292929; }
.notice { padding: 10px 14px; border-radius: 6px; background: var(--primary-soft); border-left: 3px solid var(--primary); margin: 0 0 16px; }
.notice.error { background: var(--error-soft); border-left-color: var(--now); color: var(--error-text); }
ul.problems { margin: 8px 0 0; padding-left: 20px; }
.series { display: grid; grid-template-columns: minmax(170px, auto) 1fr; gap: 10px 20px; align-items: center; }
.series .label { color: var(--muted); font-size: 14px; }
.series .label strong { color: var(--text); }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chips a { padding: 5px 10px; border: 1px solid var(--strong); border-radius: 6px; background: var(--surface); color: var(--text);
  text-decoration: none; font-weight: 500; font-variant-numeric: tabular-nums; transition: border-color 150ms, color 150ms; }
.chips a:hover { border-color: var(--primary); color: var(--primary); }
.filters { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 12px; }
.filters input, .filters select { width: auto; min-width: 180px; }
.filters input[type=search] { flex: 1 1 260px; max-width: 420px; }
.filters input[type=checkbox] { min-width: 0; width: auto; }
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 0 12px; }
.buildings { grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px; }
.buildings .card { margin: 0; }
form.building { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 12px; }
form.building strong { min-width: 72px; font-size: 16px; }
form.building input { width: auto; flex: 1 1 200px; }
.chips a .hint { margin: 0; font-size: 12px; }
.inline-form { display: inline-flex; gap: 6px; margin: 0 6px 0 0; }
.inline-form input { width: 150px; padding: 5px 8px; font-size: 13px; }
.announcement h3 { font-size: 16px; margin: 0 0 2px; }
.announcement .meta { color: var(--muted); font-size: 13px; margin: 0 0 10px; }
.announcement .body { border-top: 1px solid var(--border); padding-top: 10px; }
.announcement .body p:first-child { margin-top: 0; }
.event-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; align-items: start; }
.event-card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; overflow: hidden;
  transition: border-color 150ms, box-shadow 150ms; }
.event-card:hover { border-color: var(--strong); box-shadow: var(--shadow); }
.event-card img { display: block; width: 100%; aspect-ratio: 16 / 9; height: auto; object-fit: cover; background: var(--alt); }
.event-card .text { padding: 12px 14px 14px; }
.event-card strong, .event-card .meta { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.event-card strong { font-size: 16px; line-height: 22px; }
.event-card .meta { color: var(--muted); font-size: 13px; margin-top: 2px; }
.event-card .form-actions { margin-top: 12px; }
.event-card.past img { opacity: 0.6; }
.preview .event-card { max-width: 360px; margin-bottom: 14px; }
.current-image { display: block; max-width: 240px; border-radius: 6px; margin-bottom: 8px; }
.preview { background: var(--surface); border: 1px dashed var(--strong); border-radius: 8px; padding: 18px 20px; }
.preview .label { font-size: 12px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 8px; }
body.plain { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #1E1E1E; }
body.plain main { max-width: 400px; padding: 24px 16px; }
.login { background: var(--surface); border-radius: 10px; padding: 28px 28px 24px; }
.login img { display: block; width: 88px; height: 88px; margin: 0 auto 14px; }
.login h1 { text-align: center; font-size: 20px; margin: 0 0 4px; }
.login .sub { text-align: center; color: var(--muted); margin: 0 0 20px; }
.login button { width: 100%; justify-content: center; margin-top: 6px; }
.login .back { display: block; text-align: center; margin-top: 16px; font-size: 13px; }
@media (max-width: 860px) {
  .side { flex-basis: 100%; min-width: 0; min-height: 0; padding: 12px 16px; gap: 10px; }
  .side nav { flex-direction: row; overflow-x: auto; gap: 4px; scrollbar-width: thin; scrollbar-color: #555555 transparent; }
  .side nav .group { display: none; }
  .side nav a { white-space: nowrap; }
  .side nav a.active { box-shadow: inset 0 -2px 0 #2EBFF6; }
  .side .who { display: flex; flex-wrap: wrap; gap: 4px 12px; align-items: baseline; padding-top: 10px; margin-top: 0; }
  .side .who .role { margin: 0; }
  .topbar { padding: 10px 16px; }
  main { padding: 20px 16px 56px; }
  .series { grid-template-columns: 1fr; gap: 4px; }
  .series .chips { margin-bottom: 10px; }
  .page-head h1 { font-size: 22px; line-height: 28px; }
  .pager .pages { margin-left: 0; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation: none !important; transition: none !important; }
}
"""

/** The FAIMA seal for the sign-in card: black on the light card, white on the dark one. */
internal fun FlowContent.seal() {
    picture {
        source {
            attributes["srcset"] = "/admin/static/faima-seal-white.png"
            attributes["media"] = "(prefers-color-scheme: dark)"
        }
        img(src = "/admin/static/faima-seal.png", alt = "Sigiliul FAIMA")
    }
}

/** A dashboard error as a page a person can read, never a JSON body or a blank screen. */
suspend fun io.ktor.server.application.ApplicationCall.respondAdminError(status: io.ktor.http.HttpStatusCode) {
    val (title, text) = when (status.value) {
        404 -> "Pagina nu există" to "Linkul este greșit sau ce arăta a fost șters între timp."
        403 -> "Nu ai acces aici" to "Pagina este doar pentru administratori, sau formularul a venit de pe alt site."
        400 -> "Cererea nu a putut fi citită" to "Reîncarcă pagina și încearcă din nou."
        else ->
            "Ceva nu a mers" to
                "Serverul a întâlnit o eroare. Încearcă din nou; dacă se repetă, spune-le dezvoltatorilor ora la care s-a întâmplat."
    }
    respondHtml(status) {
        adminPage(title, null, null) {
            div("login") {
                seal()
                h1 { +title }
                p("sub") { +text }
                a(href = "/admin", classes = "btn") {
                    style = "width: 100%; justify-content: center"
                    +"Înapoi la panou"
                }
            }
        }
    }
}
