// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.site

import io.ktor.http.HttpHeaders
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.datetime.LocalDate
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.img
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.facultyNow
import net.mehstudios.etimetable.server.Downloads
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.admin.BASE_STYLES
import kotlin.time.Clock

/** The public page at "/": what the app is, where to get it, and the way to the dashboard. */
fun Route.siteRoutes(services: Services, downloads: Downloads) {
    staticResources("/static", "site")
    get("/") {
        val english = call.request.queryParameters["lang"]?.let { it == "en" }
            ?: call.request.header(HttpHeaders.AcceptLanguage).orEmpty().let { it.startsWith("en") && "ro" !in it }
        val dataset = services.datasets.current()?.dataset
        call.respondHtml { landing(if (english) EN else RO, dataset, downloads) }
    }
}

private fun HTML.landing(t: Texts, dataset: Dataset?, downloads: Downloads) {
    lang = t.lang
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "description", content = t.lead)
        title("eTimetable · FAIMA")
        link(rel = "icon", href = "/static/faima-seal.png")
        style { unsafe { +(BASE_STYLES + SITE_STYLES) } }
    }
    body {
        header("top wrap") {
            a(href = t.self, classes = "brand") {
                img(src = "/static/faima-seal.png", alt = "")
                span {
                    strong { +"eTimetable" }
                    span { +"FAIMA · UNSTPB" }
                }
            }
            nav {
                a(href = "/?lang=${t.other}", classes = "lang") { +t.other.uppercase() }
                a(href = "/admin", classes = "btn secondary") { +t.dashboard }
            }
        }
        main {
            section("hero wrap") {
                div("copy") {
                    p("kicker") { +t.faculty }
                    h1 { +t.headline }
                    p("lead") { +t.lead }
                    div("downloads") {
                        store(downloads.android, t.android, "Android", t.soon)
                        store(downloads.ios, t.iphone, "iPhone", t.soon)
                    }
                    p("small") { +t.free }
                }
                div("phones") {
                    img(src = "/static/app-light.webp", alt = t.shotLight, classes = "phone back") {
                        attributes["width"] = "270"
                        attributes["height"] = "606"
                    }
                    img(src = "/static/app-dark.webp", alt = t.shotDark, classes = "phone front") {
                        attributes["width"] = "270"
                        attributes["height"] = "606"
                    }
                }
            }
            if (dataset != null) today(t, dataset)
            section("wrap features") {
                h2 { +t.whatTitle }
                div("grid") {
                    t.features.forEach { (title, text) ->
                        div("feature") {
                            h3 { +title }
                            p { +text }
                        }
                    }
                }
            }
            section("wrap start") {
                h2 { +t.startTitle }
                ol { t.steps.forEach { li { +it } } }
            }
            section("staff") {
                div("rectorat") {
                    attributes["aria-hidden"] = "true"
                    unsafe { +RECTORAT }
                }
                div("wrap") {
                    div {
                        h2 { +t.staffTitle }
                        p { +t.staffText }
                    }
                    a(href = "/admin", classes = "btn") { +t.staffButton }
                }
            }
        }
        footer("foot") {
            div("wrap") {
                div("logos") {
                    img(src = "/static/unstpb-logo-white.png", alt = t.unstpb, classes = "unstpb")
                    img(src = "/static/faima-seal-white.png", alt = t.faimaSeal, classes = "faima")
                }
                div("meh") {
                    img(src = "/static/meh-mark.png", alt = "")
                    div {
                        strong { +"Multimedia Entertainment Hub LLC" }
                        span { +"© 2026. ${t.rights}" }
                    }
                }
                p { +t.commissioned }
                p("links") {
                    a(href = "https://faima.upb.ro/") { +"faima.upb.ro" }
                    a(href = "https://upb.ro/") { +"upb.ro" }
                    a(href = "https://mehstudios.net/") { +"mehstudios.net" }
                    a(href = "mailto:support@mehstudios.net") { +"support@mehstudios.net" }
                }
            }
        }
    }
}

/** A download button, or the platform's name and "coming soon" while the deployment has no link for it. */
private fun FlowContent.store(url: String?, label: String, platform: String, soon: String) {
    if (url != null) {
        a(href = url, classes = "btn store") { +label }
    } else {
        span("btn store off") {
            +platform
            span("soon") { +soon }
        }
    }
}

/** Today's teaching week, from the semester the server serves: the page is live, not a brochure. */
private fun FlowContent.today(t: Texts, dataset: Dataset) {
    val date = Clock.System.facultyNow().date
    val calendar = TeachingCalendar(dataset.semester)
    val week = calendar.weekOf(date)
    val pause = calendar.breakOn(date)
    val line = when {
        week != null -> t.week(week.number, week.parity == Parity.ODD)
        pause != null -> pause.name.get(t.lang)
        else -> return
    }
    section("wrap") {
        div("today") {
            span("num") { +(week?.number?.toString() ?: "-") }
            div {
                strong { +"${t.todayIs} ${t.date(date)}: $line" }
                span { +dataset.semester.name.get(t.lang) }
            }
        }
    }
}

private class Texts(
    val lang: String,
    val other: String,
    val self: String,
    val faculty: String,
    val headline: String,
    val lead: String,
    val android: String,
    val iphone: String,
    val soon: String,
    val free: String,
    val dashboard: String,
    val shotLight: String,
    val shotDark: String,
    val todayIs: String,
    val whatTitle: String,
    val features: List<Pair<String, String>>,
    val startTitle: String,
    val steps: List<String>,
    val staffTitle: String,
    val staffText: String,
    val staffButton: String,
    val rights: String,
    val unstpb: String,
    val faimaSeal: String,
    val commissioned: String,
    val months: List<String>,
    val weekWord: String,
    val odd: String,
    val even: String,
) {
    fun week(number: Int, odd: Boolean) = "$weekWord $number, ${if (odd) this.odd else even}"

    fun date(d: LocalDate) = "${d.day} ${months[d.month.ordinal]}"
}

private val RO = Texts(
    lang = "ro",
    other = "en",
    self = "/?lang=ro",
    faculty = "Facultatea de Antreprenoriat, Ingineria și Managementul Afacerilor",
    headline = "Orarul FAIMA, pe\u00A0telefon",
    lead = "Grupa ta, sălile, profesorii și anunțurile facultății, într-o aplicație pentru Android și iPhone. " +
        "După prima sincronizare merge și fără internet.",
    android = "Descarcă pentru Android",
    iphone = "Descarcă pentru iPhone",
    soon = "în curând",
    free = "Gratuită pentru studenții FAIMA. Contul se face din aplicație, cu adresa de email.",
    dashboard = "Administrare",
    shotLight = "Ziua de joi în tema luminoasă: seminarele de germană, statistică și comunicare, cu sălile lor",
    shotDark = "Pagina unei materii în tema întunecată: laboratoarele tuturor grupelor, cu cele pe care le poți adăuga",
    todayIs = "Azi,",
    whatTitle = "Ce găsești în aplicație",
    features = listOf(
        "Orarul grupei tale" to
            "Alegi o dată grupa și semigrupa. Vezi ziua, săptămâna sau un tabel ca cel de la avizier, " +
            "cu sala și profesorul fiecărei ore.",
        "Săptămâni pare și impare" to
            "Aplicația știe în ce săptămână a semestrului ești și arată doar orele care se țin " +
            "atunci. Vacanțele sunt marcate.",
        "Profesori" to "Unde predă un profesor chiar acum, ce ore are săptămâna aceasta și când ține consultații.",
        "Recuperări" to
            "Găsești laboratorul altei grupe care nu se suprapune cu orarul tău și îl adaugi o singură dată " +
            "sau pentru tot semestrul.",
        "Studenți Erasmus" to
            "Îți alegi materiile și orele din contractul de studii. O oră care se suprapune cu alta " +
            "este refuzată pe loc, cu numele celei cu care se suprapune.",
        "Anunțuri" to "Anunțurile secretariatului ajung la toți studenții și rămân în aplicație și fără internet.",
    ),
    startTitle = "Cum începi",
    steps = listOf(
        "Instalezi aplicația pe telefon.",
        "Îți faci cont cu numele, adresa de email și telefonul.",
        "Alegi specializarea, anul și grupa, sau, dacă ești la Erasmus, materiile.",
    ),
    staffTitle = "Pentru secretariat",
    staffText = "Orarul, calendarul semestrului și anunțurile se administrează din panoul de administrare. " +
        "Orarul se importă dintr-un fișier CSV; aplicațiile primesc schimbările la următoarea sincronizare.",
    staffButton = "Intră în panoul de administrare",
    rights = "Toate drepturile rezervate.",
    unstpb = "Universitatea Națională de Știință și Tehnologie POLITEHNICA București",
    faimaSeal = "Sigiliul FAIMA",
    commissioned = "Realizată la comanda Facultății de Antreprenoriat, Ingineria și Managementul Afacerilor (FAIMA), " +
        "Universitatea Națională de Știință și Tehnologie POLITEHNICA București. Numele și siglele FAIMA și UNSTPB " +
        "aparțin universității.",
    months = listOf(
        "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
        "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie",
    ),
    weekWord = "săptămâna",
    odd = "impară",
    even = "pară",
)

private val EN = Texts(
    lang = "en",
    other = "ro",
    self = "/?lang=en",
    faculty = "Faculty of Entrepreneurship, Business Engineering and Management",
    headline = "The FAIMA timetable, on your\u00A0phone",
    lead = "Your group, rooms, professors and the faculty's announcements, in one app for Android and iPhone. " +
        "After the first sync it also works offline.",
    android = "Get it for Android",
    iphone = "Get it for iPhone",
    soon = "coming soon",
    free = "Free for FAIMA students. You create the account in the app, with your email address.",
    dashboard = "Dashboard",
    shotLight = "Thursday in the light theme: the German, statistics and communication seminars, with their rooms",
    shotDark = "A subject in the dark theme: every group's labs, with the ones you can add",
    todayIs = "Today,",
    whatTitle = "What the app does",
    features = listOf(
        "Your group's timetable" to
            "Pick your group and half-group once. See the day, the week, or a table like the one " +
            "on the notice board, with the room and professor of every class.",
        "Odd and even weeks" to "The app knows which week of the semester it is and only shows the classes held that " +
            "week. Breaks are marked.",
        "Professors" to "Where a professor is teaching right now, their classes this week and their office hours.",
        "Catching up" to
            "Find another group's lab that does not clash with your timetable and add it once, or for the " +
            "whole semester.",
        "Erasmus students" to
            "Pick the subjects and slots from your learning agreement. A slot that overlaps another " +
            "is refused on the spot, naming the one it clashes with.",
        "Announcements" to "The secretariat's announcements reach every student and stay in the app, offline too.",
    ),
    startTitle = "Getting started",
    steps = listOf(
        "Install the app on your phone.",
        "Create an account with your name, email address and phone number.",
        "Pick your programme, year and group or, as an Erasmus student, your subjects.",
    ),
    staffTitle = "For the secretariat",
    staffText = "The timetable, the semester calendar and the announcements are managed in the dashboard. " +
        "The timetable is imported from a CSV file; the apps get the changes at their next sync.",
    staffButton = "Open the dashboard",
    rights = "All rights reserved.",
    unstpb = "National University of Science and Technology POLITEHNICA Bucharest",
    faimaSeal = "FAIMA seal",
    commissioned = "Commissioned by the Faculty of Entrepreneurship, Business Engineering and Management (FAIMA), " +
        "National University of Science and Technology POLITEHNICA Bucharest. The FAIMA and UNSTPB names and logos " +
        "belong to the university.",
    months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ),
    weekWord = "week",
    odd = "odd",
    even = "even",
)

/**
 * Our own line drawing of the Rectorat's front, the building in the university's emblem.
 * Everything is mirrored around x = 600, the middle of the 1200-wide drawing.
 */
private val RECTORAT = buildString {
    val middle = 600
    append("""<svg viewBox="0 0 1200 260" preserveAspectRatio="xMidYMax meet" fill="none" """)
    append("""stroke="currentColor" stroke-width="1.4" stroke-linejoin="round">""")
    // the vaulted roof: two arcs over the attic
    append("""<path d="M260 112C380 54 820 54 940 112"/>""")
    append("""<path d="M300 112C410 74 790 74 900 112"/>""")
    // the attic and its marks, one every 40 from the middle out
    append("""<rect x="240" y="112" width="720" height="20"/>""")
    append("""<path d="${(-8..8).joinToString("") { "M${middle + it * 40} 116v12" }}"/>""")
    // the front, the line under the capitals and fifteen columns, one every 44 from the middle out
    append("""<rect x="260" y="132" width="680" height="104"/><path d="M260 146H940"/>""")
    (-7..7).forEach { append("""<rect x="${middle + it * 44 - 6}" y="146" width="12" height="90"/>""") }
    // two steps and the ground
    append("""<rect x="240" y="236" width="720" height="7"/><rect x="220" y="243" width="760" height="7"/>""")
    append("""<path d="M0 250H220M980 250H1200"/>""")
    append("</svg>")
}

private val SITE_STYLES = """
.wrap { max-width: 1200px; margin: 0 auto; padding: 0 clamp(16px, 4vw, 40px); }
.top { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-top: 20px; padding-bottom: 20px; }
.top .brand { display: flex; align-items: center; gap: 12px; color: var(--text); text-decoration: none; }
.top .brand img { width: 48px; height: auto; }
.top .brand strong { display: block; font-size: 18px; line-height: 22px; }
.top .brand span span { display: block; font-size: 13px; color: var(--muted); white-space: nowrap; }
.top nav { display: flex; align-items: center; gap: 16px; }
.top .lang { color: var(--muted); font-weight: 600; font-size: 13px; text-decoration: none; letter-spacing: 0.04em; }
.top .lang:hover { color: var(--text); }
.hero { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: clamp(24px, 5vw, 72px); align-items: center;
  padding-top: clamp(12px, 4vw, 48px); padding-bottom: clamp(32px, 5vw, 64px); }
.kicker { color: var(--faima); font-weight: 600; font-size: 14px; margin: 0 0 14px; }
.hero h1 { font-size: clamp(36px, 3.6vw + 12px, 64px); line-height: 1.04; letter-spacing: -0.025em; margin: 0 0 20px; max-width: 13ch; }
.lead { font-size: clamp(16px, 0.5vw + 13px, 20px); line-height: 1.5; color: #3D3D3B; max-width: 34em; margin: 0 0 28px; }
.downloads { display: flex; flex-wrap: wrap; gap: 10px; }
.btn.store { justify-content: center; padding: 12px 18px; font-size: 15px; border-radius: 8px; background: var(--ink); }
.btn.store.off { background: var(--alt); color: var(--muted); border-color: var(--border); cursor: default; filter: none; }
.btn.store .soon { font-weight: 500; font-size: 13px; }
.btn.store .soon::before { content: "·"; margin: 0 6px 0 2px; }
.small { color: var(--muted); font-size: 14px; margin: 14px 0 0; }
/* the two screenshots as one overlapping pair; their own height sets the block, so no gaps at any width */
.phones { display: flex; justify-content: center; align-items: flex-start; padding-bottom: 4%; }
.phone { position: relative; flex: none; width: clamp(140px, 44%, 280px); height: auto; border: 9px solid var(--ink);
  border-radius: 34px; background: var(--ink); box-shadow: 0 24px 48px rgba(30, 30, 30, 0.14); }
.phone.back { z-index: 1; }
.phone.front { z-index: 2; margin-left: -10%; margin-top: 14%; }
.today { display: flex; align-items: stretch; background: var(--surface); border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.today .num { display: flex; align-items: center; justify-content: center; min-width: 72px; background: var(--alt); color: var(--text);
  border-right: 1px solid var(--border);
  font-size: 30px; font-weight: 700; font-variant-numeric: tabular-nums; }
.today div { padding: 14px 20px; }
.today strong { display: block; font-size: 17px; }
.today span { color: var(--muted); }
.features { padding-top: 64px; }
.features h2, .start h2 { font-size: 28px; line-height: 34px; margin: 0 0 24px; }
.features .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr)); gap: 0 48px; }
.feature { border-top: 1px solid var(--border); padding: 18px 0 22px; }
.feature h3 { font-size: 17px; margin: 0 0 6px; }
.feature p { margin: 0; color: #3D3D3B; }
.start { padding-top: 40px; padding-bottom: 64px; }
.start ol { margin: 0; padding: 0; list-style: none; counter-reset: step; display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 230px), 1fr)); gap: 16px; }
.start li { counter-increment: step; background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 18px 20px; }
.start li::before { content: counter(step); display: block; font-size: 28px; font-weight: 700; color: var(--primary); margin-bottom: 6px;
  font-variant-numeric: tabular-nums; }
.staff { position: relative; overflow: hidden; background: var(--ink); color: #D6D6D2; padding: 56px 0 200px; }
.staff .wrap { position: relative; z-index: 1; display: flex; flex-wrap: wrap; gap: 20px 48px; align-items: center;
  justify-content: space-between; }
.staff h2 { color: #FFFFFF; font-size: 22px; margin: 0 0 6px; }
.staff p { margin: 0; max-width: 40em; }
.staff .btn { background: #FFFFFF; color: var(--ink); }
.rectorat { position: absolute; left: 50%; bottom: 0; width: 960px; transform: translateX(-50%); color: #343432;
  pointer-events: none; }
.rectorat svg { display: block; width: 100%; height: auto; }
.foot { background: var(--ink); color: #A9A9A4; font-size: 14px; padding: 8px 0 40px; }
.foot .wrap { padding-top: 28px; }
.foot .logos { display: flex; align-items: center; gap: 24px; margin-bottom: 24px; }
.foot .logos .unstpb { height: 56px; width: auto; }
.foot .logos .faima { height: 56px; width: auto; padding-left: 24px; border-left: 1px solid #3A3A3A; }
.foot .meh { display: flex; align-items: center; gap: 12px; color: #FFFFFF; }
.foot .meh img { width: 26px; height: auto; }
.foot .meh strong, .foot .meh span { display: block; }
.foot .meh span { color: #A9A9A4; font-size: 13px; }
.foot p { max-width: 60em; }
.foot a { color: #D6D6D2; }
.foot .links { display: flex; flex-wrap: wrap; gap: 6px 20px; }
@media (min-width: 1600px) {
  .wrap { max-width: 1320px; }
  body { font-size: 16px; }
}
@media (max-width: 860px) {
  .hero { grid-template-columns: 1fr; }
  .phones { max-width: 460px; width: 100%; margin: 0 auto; }
  .phone { width: 47%; border-width: 7px; border-radius: 26px; }
  .staff { padding-bottom: 110px; }
  .rectorat { width: 100%; }
  .foot .logos .unstpb, .foot .logos .faima { height: 44px; }
  .top { padding-top: 14px; padding-bottom: 6px; }
  .top .brand img { width: 38px; }
}
@media (max-width: 520px) {
  .downloads .btn.store { flex: 1 1 140px; padding: 11px 12px; }
  .top nav { gap: 12px; }
  .top .btn { padding: 7px 12px; font-size: 13px; }
  .start li { padding: 14px 16px; }
}
"""
