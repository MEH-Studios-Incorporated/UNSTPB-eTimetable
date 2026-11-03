// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

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
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

enum class Section(val label: String, val href: String) {
    OVERVIEW("Prezentare", "/admin"),
    SEMESTERS("Semestre și orar", "/admin/semesters"),
    SUBJECTS("Materii", "/admin/subjects"),
    PROFESSORS("Profesori", "/admin/professors"),
    ROOMS("Săli", "/admin/rooms"),
    ANNOUNCEMENTS("Anunțuri", "/admin/announcements"),
    STUDENTS("Studenți", "/admin/students"),
    ADMINS("Administratori", "/admin/admins"),
}

/** Sections only administrators see: other admins, and the students' personal data. */
private val ADMIN_ONLY = setOf(Section.ADMINS, Section.STUDENTS)

/**
 * Every dashboard page: the sidebar with the sections, then a header with
 * [pageTitle], an optional [context] line and [actions] on the right, then [content].
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
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "robots", content = "noindex")
        title("$pageTitle · eTimetable")
        link(rel = "icon", href = "/admin/static/faima-seal.png")
        style { unsafe { +(BASE_STYLES + ADMIN_STYLES) } }
    }
    body(if (admin == null) "plain" else "shell") {
        if (admin != null) {
            aside("side") {
                a(href = "/admin", classes = "brand") {
                    img(src = "/admin/static/faima-seal-white.png", alt = "")
                    span {
                        span("name") { +"eTimetable" }
                        span("sub") { +"Administrare FAIMA" }
                    }
                }
                nav {
                    Section.entries.filter {
                        it !in ADMIN_ONLY || admin.role == AdminRole.ADMIN
                    }.forEach { section ->
                        a(href = section.href, classes = if (section == current) "active" else null) { +section.label }
                    }
                }
                div("who") {
                    span("email") { +admin.email }
                    span("role") { +(if (admin.role == AdminRole.ADMIN) "Administrator" else "Editor") }
                    div("links") {
                        a(href = "/admin/password") { +"Parola" }
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
        main {
            if (admin != null) {
                header("page-head") {
                    div {
                        h1 { +pageTitle }
                        if (context != null) div("context") { context() }
                    }
                    if (actions != null) div("actions") { actions() }
                }
            }
            content()
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

private val ADMIN_STYLES = """
body.shell { display: grid; grid-template-columns: 240px minmax(0, 1fr); min-height: 100vh; }
.side { position: sticky; top: 0; height: 100vh; display: flex; flex-direction: column; background: var(--ink); color: #D6D6D2; padding: 20px 14px; }
.side .brand { display: flex; gap: 10px; align-items: center; color: #FFFFFF; text-decoration: none; padding: 0 8px 20px; }
.side .brand img { width: 34px; height: 34px; }
.side .brand .name { display: block; font-weight: 700; font-size: 16px; line-height: 20px; }
.side .brand .sub { display: block; font-size: 12px; color: #A3A39F; }
.side nav { display: flex; flex-direction: column; gap: 2px; }
.side nav a { color: #D6D6D2; text-decoration: none; padding: 8px 10px; border-radius: 6px; font-weight: 500; border-left: 3px solid transparent; }
.side nav a:hover { background: #2A2A2A; color: #FFFFFF; }
.side nav a.active { background: #2A2A2A; color: #FFFFFF; border-left-color: #2EBFF6; }
.side .who { margin-top: auto; padding: 14px 10px 0; border-top: 1px solid #333333; font-size: 13px; }
.side .who .email { display: block; color: #FFFFFF; overflow-wrap: anywhere; }
.side .who .role { display: block; color: #A3A39F; margin-bottom: 8px; }
.side .who .links { display: flex; flex-wrap: wrap; gap: 4px 14px; }
.side .who a, .side .who button.link { color: #D6D6D2; font-size: 13px; text-decoration: none; }
.side .who a:hover, .side .who button.link:hover { color: #FFFFFF; text-decoration: underline; }
.side .who form { margin: 0; }
main { max-width: 1120px; width: 100%; padding: 28px 32px 72px; }
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
ul.todo .mark.ok { background: #3C8D5A; }
ul.todo .mark.bad { background: var(--now); }
table { width: 100%; border-collapse: separate; border-spacing: 0; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
th, td { text-align: left; padding: 10px 14px; border-bottom: 1px solid var(--border); vertical-align: top; }
th { font-size: 12px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; background: var(--alt); }
thead tr:first-child th:first-child { border-top-left-radius: 8px; }
thead tr:first-child th:last-child { border-top-right-radius: 8px; }
tr:last-child td { border-bottom: 0; }
td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
td.actions { text-align: right; white-space: nowrap; }
td form, .inline { display: inline; margin: 0; }
td .btn, td button { padding: 5px 10px; font-size: 13px; }
.scroll { overflow-x: auto; }
.field { margin: 0 0 14px; }
label { display: block; font-size: 13px; font-weight: 600; margin: 0 0 6px; }
label.check { display: flex; gap: 8px; align-items: center; font-weight: 500; font-size: 14px; }
input[type=text], input[type=email], input[type=password], input[type=date], input[type=search], textarea, select {
  width: 100%; max-width: 560px; padding: 9px 12px; border: 1px solid var(--strong); border-radius: 6px; background: var(--surface);
  font: inherit; color: var(--text); }
input:focus, textarea:focus, select:focus { outline: 2px solid var(--primary-soft); border-color: var(--primary); }
textarea { max-width: 100%; min-height: 140px; font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13px; line-height: 1.5; }
.hint { font-size: 13px; color: var(--muted); margin: 6px 0 0; }
.form-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.tag { display: inline-block; padding: 1px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; background: var(--alt); color: var(--muted); }
.tag.current { background: var(--primary); color: #FFFFFF; }
.tag.pinned { background: var(--change); color: #292929; }
.notice { padding: 10px 14px; border-radius: 6px; background: var(--primary-soft); border-left: 3px solid var(--primary); margin: 0 0 16px; }
.notice.error { background: #FBEAE9; border-left-color: var(--now); color: #8E1B15; }
ul.problems { margin: 8px 0 0; padding-left: 20px; }
.series { display: grid; grid-template-columns: minmax(170px, auto) 1fr; gap: 10px 20px; align-items: center; }
.series .label { color: var(--muted); font-size: 14px; }
.series .label strong { color: var(--text); }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chips a { padding: 5px 10px; border: 1px solid var(--strong); border-radius: 6px; background: var(--surface); color: var(--text);
  text-decoration: none; font-weight: 500; font-variant-numeric: tabular-nums; }
.chips a:hover { border-color: var(--primary); color: var(--primary); }
.filters { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 12px; }
.filters input, .filters select { width: auto; min-width: 180px; }
.filters input[type=search] { flex: 1 1 260px; max-width: 420px; }
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 0 12px; }
.buildings { grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px; }
.buildings .card { margin: 0; }
form.building { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 12px; }
form.building strong { min-width: 72px; font-size: 16px; }
form.building input { width: auto; flex: 1 1 200px; }
.chips a .hint { margin: 0; font-size: 12px; }
.inline-form { display: inline-flex; gap: 6px; margin: 0 6px 0 0; }
.inline-form input { width: 150px; padding: 5px 8px; font-size: 13px; }
a.slot { display: block; color: var(--text); text-decoration: none; }
a.slot:hover { border-color: var(--primary); }
.slot.office { border-left-color: var(--muted); background: var(--alt); }
.week { display: grid; grid-template-columns: repeat(5, minmax(170px, 1fr)); gap: 10px; }
.day h3 { font-size: 12px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; margin: 0 0 8px; }
.slot { background: var(--surface); border: 1px solid var(--border); border-left: 4px solid var(--primary); border-radius: 6px;
  padding: 8px 10px; margin-bottom: 8px; font-size: 13px; }
.slot.lab { border-left-color: #B07A1F; } .slot.seminar { border-left-color: #2F7F7A; } .slot.project { border-left-color: #7A4C92; }
.slot .time { font-variant-numeric: tabular-nums; color: var(--muted); }
.slot strong { display: block; font-size: 14px; margin: 2px 0; }
.announcement h3 { font-size: 16px; margin: 0 0 2px; }
.announcement .meta { color: var(--muted); font-size: 13px; margin: 0 0 10px; }
.announcement .body { border-top: 1px solid var(--border); padding-top: 10px; }
.announcement .body p:first-child { margin-top: 0; }
.preview { background: var(--surface); border: 1px dashed var(--strong); border-radius: 8px; padding: 18px 20px; }
.preview .label { font-size: 12px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 8px; }
body.plain { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--ink); }
body.plain main { max-width: 400px; padding: 24px 16px; }
.login { background: var(--surface); border-radius: 10px; padding: 28px 28px 24px; }
.login img { display: block; width: 88px; margin: 0 auto 14px; }
.login h1 { text-align: center; font-size: 20px; margin: 0 0 4px; }
.login .sub { text-align: center; color: var(--muted); margin: 0 0 20px; }
.login button { width: 100%; justify-content: center; margin-top: 6px; }
.login .back { display: block; text-align: center; margin-top: 16px; font-size: 13px; }
@media (max-width: 860px) {
  body.shell { display: block; }
  .side { position: static; height: auto; padding: 12px 16px; }
  .side .brand { padding: 0 0 10px; }
  .side nav { flex-direction: row; overflow-x: auto; gap: 4px; }
  .side nav a { white-space: nowrap; border-left: 0; border-bottom: 2px solid transparent; border-radius: 4px; }
  .side nav a.active { border-bottom-color: #2EBFF6; }
  .side .who { display: flex; flex-wrap: wrap; gap: 4px 12px; align-items: baseline; padding-top: 10px; margin-top: 10px; }
  .side .who .role { margin: 0; }
  main { padding: 20px 16px 56px; }
  .series { grid-template-columns: 1fr; gap: 4px; }
  .series .chips { margin-bottom: 10px; }
  .page-head h1 { font-size: 22px; line-height: 28px; }
}
"""
