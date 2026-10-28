// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.lang
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

enum class Section(val label: String, val href: String) {
    SEMESTERS("Semestre", "/admin/semesters"),
    ANNOUNCEMENTS("Anunțuri", "/admin/announcements"),
    ADMINS("Administratori", "/admin/admins"),
}

/** Every dashboard page: the top bar with the sections, then [content]. */
fun HTML.adminPage(pageTitle: String, admin: Admin?, current: Section?, content: FlowContent.() -> Unit) {
    lang = "ro"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("$pageTitle · eTimetable")
        style { unsafe { +STYLES } }
    }
    body {
        if (admin != null) {
            header("bar") {
                a(href = "/admin/semesters", classes = "brand") { +"eTimetable" }
                nav {
                    Section.entries.filter {
                        it != Section.ADMINS || admin.role == AdminRole.ADMIN
                    }.forEach { section ->
                        a(href = section.href, classes = if (section == current) "active" else null) { +section.label }
                    }
                }
                div("who") {
                    span { +admin.email }
                    form(action = "/admin/logout", method = FormMethod.post) {
                        button(classes = "link") { +"Ieșire" }
                    }
                }
            }
        }
        main {
            // the login page puts its title inside the form panel
            if (admin != null) h1 { +pageTitle }
            content()
        }
    }
}

fun FlowContent.notice(text: String?, error: Boolean = false) {
    if (text.isNullOrBlank()) return
    p(if (error) "notice error" else "notice") { +text }
}

// docs/design.md tokens. The font is served by the server, so the dashboard
// makes no requests to other sites.
private val STYLES = """
@font-face { font-family: "Libre Franklin"; src: url("/admin/static/libre-franklin.ttf") format("truetype"); font-weight: 100 900; }
:root { --bg: #FAFAF9; --surface: #FFFFFF; --alt: #F2F2F0; --border: #E3E3E0; --text: #292929; --muted: #5C5C5A;
  --primary: #1D6FA5; --now: #D92A21; --change: #F5C753; }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 400 15px/1.45 "Libre Franklin", system-ui, sans-serif; }
a { color: var(--primary); }
.bar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 24px; padding: 12px 24px; background: #292929; color: #EDEDED; }
.bar a { color: #EDEDED; text-decoration: none; font-weight: 500; }
.bar .brand { font-weight: 700; font-size: 17px; margin-right: 8px; }
.bar nav { display: flex; gap: 20px; flex-wrap: wrap; }
.bar nav a.active { color: #FFFFFF; box-shadow: inset 0 -2px 0 #FFFFFF; padding-bottom: 2px; }
.bar .who { margin-left: auto; display: flex; gap: 12px; align-items: center; color: #BDBDBA; font-size: 13px; }
.bar .who form { margin: 0; }
.bar .link { color: #EDEDED; }
main { max-width: 1080px; margin: 0 auto; padding: 24px 16px 64px; }
h1 { font-size: 22px; line-height: 28px; font-weight: 600; margin: 8px 0 20px; }
h2 { font-size: 17px; line-height: 22px; font-weight: 600; margin: 28px 0 12px; }
.panel { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 16px; margin-bottom: 16px; }
table { width: 100%; border-collapse: collapse; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
th, td { text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--border); vertical-align: top; }
th { font-size: 13px; font-weight: 600; color: var(--muted); background: var(--alt); }
tr:last-child td { border-bottom: 0; }
.scroll { overflow-x: auto; }
label { display: block; font-size: 13px; font-weight: 600; margin: 12px 0 6px; }
input[type=text], input[type=email], input[type=password], input[type=date], textarea, select {
  width: 100%; max-width: 520px; padding: 10px 12px; border: 1px solid #D4D4D0; border-radius: 6px; background: #FFFFFF;
  font: inherit; color: var(--text); }
textarea { max-width: 100%; min-height: 120px; font-family: ui-monospace, Menlo, monospace; font-size: 13px; }
.hint { font-size: 13px; color: var(--muted); margin: 4px 0 0; }
button, .button { display: inline-block; margin-top: 16px; padding: 10px 16px; border: 0; border-radius: 6px; background: var(--primary);
  color: #FFFFFF; font: 600 14px "Libre Franklin", system-ui, sans-serif; cursor: pointer; text-decoration: none; }
button.secondary, .button.secondary { background: #FFFFFF; color: var(--text); border: 1px solid #D4D4D0; }
button.danger { background: #FFFFFF; color: var(--now); border: 1px solid var(--border); }
button.link { margin: 0; padding: 0; background: none; color: var(--primary); font-weight: 500; }
td form { display: inline; }
td button { margin: 0 8px 0 0; padding: 6px 10px; font-size: 13px; }
.tag { display: inline-block; padding: 1px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; background: var(--alt); color: var(--muted); }
.tag.current { background: var(--primary); color: #FFFFFF; }
.tag.pinned { background: var(--change); color: #292929; }
.notice { padding: 10px 14px; border-radius: 6px; background: var(--alt); }
.notice.error { background: #FBEAE9; color: #8E1B15; }
ul.problems { margin: 8px 0 0; padding-left: 20px; }
.week { display: grid; grid-template-columns: repeat(5, minmax(160px, 1fr)); gap: 8px; }
.day h3 { font-size: 13px; font-weight: 600; color: var(--muted); margin: 0 0 6px; }
.slot { background: var(--surface); border: 1px solid var(--border); border-left: 4px solid var(--primary); border-radius: 6px; padding: 8px 10px; margin-bottom: 6px; font-size: 13px; }
.slot strong { display: block; font-size: 14px; }
.login { max-width: 380px; margin: 48px auto; }
.login img { display: block; width: 120px; margin: 8px auto 12px; }
.login h1 { text-align: center; font-size: 20px; margin: 0 0 8px; }
"""
