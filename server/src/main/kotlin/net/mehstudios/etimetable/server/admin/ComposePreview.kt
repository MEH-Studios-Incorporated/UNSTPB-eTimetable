// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import net.mehstudios.etimetable.server.Services

/** Longer than any body the forms accept, so a preview still works past the limit. */
private const val MAX_PREVIEW_CHARS = 40_000

/** The Markdown preview admin.js asks for while someone types; the same HTML the page draws. */
internal fun Route.previewRoutes(services: Services) {
    action("/preview/markdown", services) { _ ->
        val text = call.receiveParameters()["text"].orEmpty()
        if (text.length > MAX_PREVIEW_CHARS) return@action call.respond(HttpStatusCode.PayloadTooLarge)
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondText(markdownFragment(text), ContentType.Text.Html.withCharset(Charsets.UTF_8))
    }
}

/** [markdown] as a bare HTML fragment, without the element around it. */
internal fun markdownFragment(text: String): String =
    createHTML(prettyPrint = false).div { markdown(text) }.removePrefix("<div>").removeSuffix("</div>")

/** The form on the left and the phone on the right; the phone goes under the form on narrow screens. */
internal fun FlowContent.composeLayout(form: FlowContent.() -> Unit, phone: FlowContent.() -> Unit) {
    div("compose") {
        div("compose-form") { form() }
        phone()
    }
}

/** The bar under a compose form: the main button, the preview without JavaScript, and a way out. */
internal fun FlowContent.composeActions(submit: String, cancel: String) {
    div("compose-actions") {
        button { +submit }
        button(classes = "secondary") {
            name = "preview"
            value = "1"
            attributes["formnovalidate"] = "formnovalidate"
            attributes["data-preview-button"] = ""
            +"Previzualizează"
        }
        span("spacer")
        a(href = cancel) { +"Renunță" }
    }
}

/** A field label with a live "12 / 200" count on the right. */
internal fun FlowContent.countedField(id: String, label: String, length: Int, max: Int, input: FlowContent.() -> Unit) {
    div("field") {
        div("label-row") {
            label {
                htmlFor = id
                +label
            }
            span("count tnum") {
                attributes["data-count"] = id
                attributes["data-max"] = max.toString()
                +"$length / $max"
            }
        }
        input()
    }
}

/**
 * The phone beside the form, with "Card" and "Pagină" to switch between the list item and the
 * opened page, and "Luminos" and "Întunecat" for the phone's theme. The switches are radio
 * buttons that CSS reads, so they work without JavaScript.
 */
internal fun FlowContent.phonePreview(
    dark: Boolean,
    card: FlowContent.() -> Unit,
    page: FlowContent.() -> Unit,
    below: (FlowContent.() -> Unit)? = null,
) {
    section("phone-preview") {
        attributes["aria-label"] = "Previzualizare pe telefon"
        attributes["data-phone-preview"] = ""
        switchInput("phone-view", "pv-card", checked = true)
        switchInput("phone-view", "pv-page", checked = false)
        switchInput("phone-tone", "pv-light", checked = !dark)
        switchInput("phone-tone", "pv-dark", checked = dark)
        div("phone-bar") {
            h2 { +"Pe telefon" }
            div("seg") {
                attributes["role"] = "group"
                attributes["aria-label"] = "Ce se previzualizează"
                switchLabel("pv-card", "Card")
                switchLabel("pv-page", "Pagină")
            }
            div("seg") {
                attributes["role"] = "group"
                attributes["aria-label"] = "Tema telefonului"
                switchLabel("pv-light", "Luminos")
                switchLabel("pv-dark", "Întunecat")
            }
        }
        div("phone") {
            div("view view-card") { card() }
            div("view view-page") { page() }
        }
        if (below != null) div("phone-note") { below() }
    }
}

private fun FlowContent.switchInput(group: String, id: String, checked: Boolean) {
    radioInput(name = group, classes = "pv") {
        this.id = id
        this.checked = checked
        if (group == "phone-tone") attributes["data-phone-tone"] = id.removePrefix("pv-")
    }
}

private fun FlowContent.switchLabel(target: String, text: String) {
    label {
        htmlFor = target
        +text
    }
}

/** The app's top bar on a tab: the seal, the name, the faculty. */
internal fun FlowContent.appBrandBar() {
    div("app-brand") {
        img(src = "/admin/static/faima-seal.png", alt = "", classes = "seal light")
        img(src = "/admin/static/faima-seal-white.png", alt = "", classes = "seal dark")
        div {
            strong { +"eTimetable" }
            span { +"FAIMA · UNSTPB" }
        }
    }
}

/** The app's bottom bar with [current] lit. */
internal fun FlowContent.appTabBar(current: Section) {
    div("app-tabs") {
        attributes["aria-hidden"] = "true"
        listOf(
            "Orar" to Section.SEMESTERS,
            "Materii" to Section.SUBJECTS,
            "Profesori" to Section.PROFESSORS,
            "Anunțuri" to Section.ANNOUNCEMENTS,
            "Evenimente" to Section.EVENTS,
        ).forEach { (label, section) ->
            span(if (section == current) "on" else null) {
                unsafe { +svgIcon(section.icon, 20) }
                span { +label }
            }
        }
    }
}

/** A page's top bar in the app: the round back button and the page's name. */
internal fun FlowContent.appPageBar(title: String) {
    div("app-pagebar") {
        span("app-back") { unsafe { +svgIcon(ICON_PREV, 18) } }
        strong { +title }
    }
}

/**
 * Text the preview shows from a form field. admin.js keeps it in step with the field named
 * [field]; [empty] stands in, greyed, while the field is blank.
 */
internal fun HtmlBlockTag.previewText(field: String, value: String, empty: String?) {
    attributes["data-preview-text"] = field
    if (empty != null) attributes["data-empty"] = empty
    if (value.isBlank()) {
        classes = classes + "is-empty"
        +empty.orEmpty()
    } else {
        +value
    }
}

internal const val ICON_PIN = "M9 4h6M10 4v5l-3 3h10l-3-3V4M12 12v8"
internal const val ICON_PICTURE = "M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4M9 9h.01"

internal val COMPOSE_STYLES = """
.compose [hidden] { display: none !important; }
.compose { display: flex; flex-wrap: wrap; gap: 24px; align-items: flex-start; }
.compose-form { flex: 999 1 480px; min-width: 0; }
.compose-form form.card { padding: 0; overflow: hidden; }
.compose-form .fields { padding: 20px; }
.compose-form .row3 { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0 12px; }
.compose-form .label-row { display: flex; justify-content: space-between; gap: 12px; }
.compose-form .label-row .count { font-size: 13px; color: var(--muted); }
.compose-form input[type=text], .compose-form input[type=url], .compose-form textarea { max-width: none; }
.compose-form textarea { min-height: 200px; resize: vertical; }
.compose-form label.check.notify { align-items: flex-start; }
.compose-form label.check.notify input { margin-top: 3px; }
.compose-form label.check .hint { display: block; font-weight: 400; margin-top: 2px; }
.compose-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; padding: 14px 20px; border-top: 1px solid var(--border); background: var(--bg); }
.compose-actions .spacer { flex: 1; }
.compose-actions a { color: var(--muted); font-weight: 600; font-size: 14px; }
.image-pick { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; border: 1px dashed var(--strong); border-radius: 8px; padding: 12px; background: var(--bg); }
.image-pick .thumb { flex: none; width: 128px; aspect-ratio: 16 / 9; border-radius: 6px; overflow: hidden; background: var(--alt); border: 1px solid var(--border);
  display: flex; align-items: center; justify-content: center; color: var(--muted); }
.image-pick .thumb { position: relative; }
.image-pick .thumb img { display: block; width: 100%; height: 100%; object-fit: cover; }
.image-pick .thumb .e-none { color: var(--muted); }
.image-pick .pick { flex: 1 1 220px; min-width: 0; }
.image-pick input[type=file] { max-width: 100%; font: inherit; font-size: 14px; }
.phone-preview { position: relative; flex: 1 1 340px; max-width: 400px; min-width: 0; display: flex; flex-direction: column; gap: 12px; }
.phone-preview > input.pv { position: absolute; width: 1px; height: 1px; margin: 0; opacity: 0; pointer-events: none; }
.phone-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; }
.phone-bar h2 { flex: 1 0 100%; margin: 0; }
.seg { display: inline-flex; border: 1px solid var(--border); border-radius: 6px; overflow: hidden; }
.seg label { margin: 0; padding: 6px 12px; font-size: 13px; line-height: 18px; font-weight: 600; cursor: pointer; background: var(--surface);
  color: var(--text); transition: background-color 150ms, color 150ms; }
.seg label + label { border-left: 1px solid var(--border); }
#pv-card:checked ~ .phone-bar label[for=pv-card], #pv-page:checked ~ .phone-bar label[for=pv-page],
#pv-light:checked ~ .phone-bar label[for=pv-light], #pv-dark:checked ~ .phone-bar label[for=pv-dark] { background: var(--text); color: var(--bg); }
#pv-card:focus-visible ~ .phone-bar label[for=pv-card], #pv-page:focus-visible ~ .phone-bar label[for=pv-page],
#pv-light:focus-visible ~ .phone-bar label[for=pv-light], #pv-dark:focus-visible ~ .phone-bar label[for=pv-dark] { outline: 2px solid var(--primary); outline-offset: -2px; }
#pv-card:checked ~ .phone .view-page, #pv-page:checked ~ .phone .view-card { display: none; }
.phone { --p-bg: #FAFAF9; --p-surface: #FFFFFF; --p-alt: #F2F2F0; --p-border: #E3E3E0; --p-text: #292929; --p-muted: #5C5C5A; --p-primary: #1D6FA5;
  --p-now: #D92A21; --p-clear: rgba(250, 250, 249, 0); --p-veil: rgba(250, 250, 249, 0.75);
  align-self: center; width: 320px; max-width: 100%; height: 640px; border: 1px solid var(--border); border-radius: 28px; overflow: hidden;
  display: flex; flex-direction: column; background: var(--p-bg); color: var(--p-text); box-shadow: var(--shadow); font-size: 15px; line-height: 21px;
  transition: background-color 200ms, color 200ms; }
#pv-dark:checked ~ .phone { --p-bg: #0A0A0A; --p-surface: #141414; --p-alt: #1E1E1E; --p-border: #2A2A2A; --p-text: #EDEDED; --p-muted: #9A9A9A;
  --p-primary: #2EBFF6; --p-now: #F0564C; --p-clear: rgba(10, 10, 10, 0); --p-veil: rgba(10, 10, 10, 0.75); }
.phone .view { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.phone .scroller { flex: 1; min-height: 0; overflow-y: auto; scrollbar-width: thin; }
.phone .is-empty { color: var(--p-muted); }
.phone .seal.dark, #pv-dark:checked ~ .phone .seal.light { display: none; }
#pv-dark:checked ~ .phone .seal.dark { display: block; }
.app-brand { display: flex; align-items: center; gap: 10px; padding: 12px 12px 4px 16px; }
.app-brand .seal { width: 36px; height: 36px; object-fit: contain; }
.app-brand strong { display: block; font-size: 17px; line-height: 22px; font-weight: 700; }
.app-brand span { display: block; font-size: 12px; line-height: 16px; color: var(--p-muted); }
.app-heading { font-size: 22px; line-height: 28px; font-weight: 600; margin: 12px 16px 8px; }
.app-tabs { flex: none; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); border-top: 1px solid var(--p-border); background: var(--p-surface); padding: 8px 2px 12px; }
.app-tabs > span { display: flex; flex-direction: column; align-items: center; gap: 2px; font-size: 10px; line-height: 14px; font-weight: 500; color: var(--p-muted); min-width: 0; }
.app-tabs > span span { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.app-tabs > span.on { color: var(--p-primary); }
.app-pagebar { flex: none; display: flex; align-items: center; gap: 12px; padding: 12px 12px 8px; }
.app-pagebar strong { font-size: 17px; line-height: 22px; font-weight: 600; }
.app-back { flex: none; display: inline-flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 50%;
  background: var(--p-surface); border: 1px solid var(--p-border); color: var(--p-text); }
.app-pinned { display: flex; align-items: center; gap: 4px; font-size: 13px; line-height: 18px; font-weight: 500; color: var(--p-muted); }
.app-md { font-size: 15px; line-height: 21px; overflow-wrap: anywhere; }
.app-md p, .app-md ul { margin: 0 0 8px; }
.app-md ul { padding-left: 20px; }
.app-md a { color: var(--p-primary); }
.a-item { display: flex; gap: 10px; padding: 14px 16px; border-bottom: 1px solid var(--p-border); margin: 0 0 0; }
.a-item .unread { flex: none; width: 8px; height: 8px; margin-top: 7px; border-radius: 50%; background: var(--p-now); }
.a-item .text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.a-item .title { font-size: 17px; line-height: 22px; font-weight: 600; overflow-wrap: anywhere; }
.a-item .excerpt { color: var(--p-muted); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
.a-meta { font-size: 12px; line-height: 16px; color: var(--p-muted); }
.a-page { display: flex; flex-direction: column; gap: 8px; padding: 0 16px 32px; }
.a-page .title { font-size: 22px; line-height: 28px; font-weight: 600; overflow-wrap: anywhere; }
.a-page hr { width: 100%; border: 0; border-top: 1px solid var(--p-border); margin: 8px 0; }
.e-list { padding: 0 16px 24px; }
.e-card { background: var(--p-surface); border: 1px solid var(--p-border); border-radius: 8px; overflow: hidden; }
.e-pic { position: relative; aspect-ratio: 16 / 9; background: var(--p-alt); overflow: hidden; }
.e-pic img, .e-hero img { display: block; width: 100%; height: 100%; object-fit: cover; }
.e-none { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; color: var(--p-muted);
  font-size: 12px; line-height: 16px; }
.e-card .text { padding: 12px 14px 14px; }
.e-card .title, .e-card .when { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.e-card .title { font-size: 17px; line-height: 22px; font-weight: 600; }
.e-card .when { color: var(--p-muted); margin-top: 2px; }
.e-hero { position: relative; aspect-ratio: 1; background: var(--p-alt); }
.e-hero .fade { position: absolute; inset: 0; background: linear-gradient(to bottom, var(--p-clear) 0%, var(--p-clear) 45%, var(--p-veil) 80%, var(--p-bg) 100%); }
.e-hero .app-back { position: absolute; top: 12px; left: 12px; }
.e-page { position: relative; margin-top: -72px; padding: 0 16px 32px; display: flex; flex-direction: column; gap: 8px; }
.e-page .title { font-size: 26px; line-height: 32px; font-weight: 600; overflow-wrap: anywhere; }
.e-page .when { font-size: 14px; line-height: 20px; font-weight: 500; }
.e-page .place { color: var(--p-muted); }
.e-page .app-md { margin-top: 8px; }
.e-link { align-self: flex-start; margin-top: 8px; padding: 9px 18px; border: 1px solid var(--p-border); border-radius: 6px; color: var(--p-primary); font-weight: 600; font-size: 14px; }
.phone-note { font-size: 13px; line-height: 18px; color: var(--muted); text-align: center; }
.phone-note p { margin: 0 0 4px; }
.list-thumb { display: flex; gap: 12px; align-items: flex-start; }
.list-thumb img { flex: none; width: 96px; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 4px; background: var(--alt); }
.list-thumb.past img { opacity: 0.7; }
.list-thumb > div { min-width: 0; }
.list-thumb a { color: var(--text); font-weight: 500; text-decoration: none; }
.list-thumb a:hover { color: var(--primary); text-decoration: underline; }
td.excerpt { max-width: 0; width: 40%; }
td .clip { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 860px) {
  .phone-preview { max-width: none; }
}
"""
