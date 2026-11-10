// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.site

import io.ktor.http.HttpHeaders
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
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
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.picture
import kotlinx.html.section
import kotlinx.html.source
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.Semester
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.facultyNow
import net.mehstudios.etimetable.schedule.mondayOfWeek
import net.mehstudios.etimetable.server.Downloads
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.admin.BASE_STYLES
import net.mehstudios.etimetable.server.admin.Section
import net.mehstudios.etimetable.server.admin.svgIcon
import kotlin.time.Clock

/** The public page at "/": what the app is, where to get it, and the way to the dashboard. */
fun Route.siteRoutes(services: Services, downloads: Downloads) {
    staticResources("/static", "site")
    get("/") {
        val english = call.request.queryParameters["lang"]?.let { it == "en" }
            ?: call.request.header(HttpHeaders.AcceptLanguage).orEmpty().let { it.startsWith("en") && "ro" !in it }
        val t = if (english) EN else RO
        val semester = services.datasets.current()?.dataset?.semester
        val ticket = semester?.let { weekTicket(it, Clock.System.facultyNow().date, english) }
        call.respondHtml { landing(t, ticket, semester?.name?.get(t.lang), downloads) }
    }
}

/** The teaching week as the app's top bar shows it: the number, if the week has one, and a line. */
internal data class WeekTicket(val number: Int?, val text: String)

/**
 * The ticket for [today] in [semester]: "Săptămâna impară · 9 - 13 noiembrie", or the break and
 * its days. On Saturday and Sunday it is already the coming week, as in the app. Null outside
 * the semester.
 */
internal fun weekTicket(semester: Semester, today: LocalDate, english: Boolean): WeekTicket? {
    val t = if (english) EN else RO
    val day = if (today.dayOfWeek.isoDayNumber >= 6) today.mondayOfWeek().plus(7, DateTimeUnit.DAY) else today
    val calendar = TeachingCalendar(semester)
    calendar.weekOf(day)?.let { week ->
        val parity = if (week.parity == Parity.ODD) t.oddWeek else t.evenWeek
        return WeekTicket(week.number, "$parity · ${t.range(week.monday, week.monday.plus(4, DateTimeUnit.DAY))}")
    }
    val pause = calendar.breakOn(day) ?: return null
    return WeekTicket(null, "${pause.name.get(t.lang)} · ${t.range(pause.firstDay, pause.lastDay)}")
}

private fun HTML.landing(t: Texts, ticket: WeekTicket?, semesterName: String?, downloads: Downloads) {
    lang = t.lang
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "color-scheme", content = "light dark")
        meta(name = "description", content = t.lead)
        title("eTimetable · FAIMA")
        link(rel = "icon", href = "/static/faima-seal.png")
        link(rel = "alternate", href = "/?lang=${t.other}") { attributes["hreflang"] = t.other }
        style { unsafe { +(BASE_STYLES + SITE_STYLES) } }
    }
    body {
        header("top") {
            div("wrap") {
                a(href = t.self, classes = "brand") {
                    seal(dark = false)
                    span("name") {
                        strong { +"eTimetable" }
                        span { +"FAIMA · UNSTPB" }
                    }
                }
                span("spacer")
                nav("sections") {
                    attributes["aria-label"] = t.onThisPage
                    a(href = "#cum", classes = "lk") { +t.howTitle }
                    a(href = "#ce", classes = "lk") { +t.whatNav }
                    a(href = "#secretariat", classes = "lk") { +t.staffTitle }
                }
                a(href = "/?lang=${t.other}", classes = "lang") {
                    attributes["hreflang"] = t.other
                    attributes["lang"] = t.other
                    attributes["aria-label"] = t.otherName
                    +t.other.uppercase()
                }
                a(href = "#descarca", classes = "cta") { +t.download }
            }
        }
        main {
            section("hero wrap") {
                id = "descarca"
                div("copy") {
                    if (ticket != null) {
                        p("now") {
                            span("ticket") {
                                if (ticket.number != null) {
                                    span("num tnum") {
                                        attributes["aria-hidden"] = "true"
                                        +ticket.number.toString()
                                    }
                                    span("sr") { +"${t.weekWord} ${ticket.number}: " }
                                }
                                span("text") { +ticket.text }
                            }
                            if (semesterName != null) span("semester") { +semesterName }
                        }
                    }
                    h1 { +t.headline }
                    p("lead") { +t.lead }
                    div("stores") {
                        store(downloads.android, t.android, "Android", t.soon, "android")
                        store(downloads.ios, t.ios, "iOS", t.soon, "ios")
                    }
                    ul("facts") { t.facts.forEach { li { +it } } }
                }
                div("shot") {
                    picture {
                        source {
                            attributes["srcset"] = "/static/app-dark.webp"
                            attributes["media"] = "(prefers-color-scheme: dark)"
                        }
                        img(src = "/static/app-light.webp", alt = t.shot) {
                            attributes["width"] = "540"
                            attributes["height"] = "1212"
                        }
                    }
                }
            }
            section("band") {
                id = "cum"
                div("wrap") {
                    h2 { +t.howTitle }
                    ol("steps") {
                        t.steps.forEachIndexed { index, (title, text) ->
                            li {
                                span("step tnum") {
                                    attributes["aria-hidden"] = "true"
                                    +(index + 1).toString()
                                }
                                h3 { +title }
                                p { +text }
                            }
                        }
                    }
                }
            }
            section("wrap features") {
                id = "ce"
                h2 { +t.whatTitle }
                div("grid") {
                    t.features.forEach { feature ->
                        div("feature") {
                            unsafe { +svgIcon(feature.icon, 22) }
                            div {
                                h3 { +feature.title }
                                p { +feature.text }
                            }
                        }
                    }
                }
            }
            section("wrap staff") {
                id = "secretariat"
                div("panel") {
                    div {
                        h2 { +t.staffTitle }
                        p { +t.staffText }
                    }
                    a(href = "/admin", classes = "plain") { +t.staffButton }
                }
            }
            section("wrap privacy") {
                id = "confidentialitate"
                h2 { +t.privacyTitle }
                p { +t.privacyText }
            }
        }
        footer("foot") {
            div("wrap main") {
                a(href = "https://faima.upb.ro/", classes = "faculty") {
                    seal(dark = true)
                    span {
                        strong { +t.faculty }
                        span { +t.university }
                    }
                }
                a(href = "https://mehstudios.net/", classes = "meh") {
                    img(src = "/static/meh-mark.png", alt = "") {
                        attributes["width"] = "34"
                        attributes["height"] = "34"
                    }
                    span {
                        span { +t.developedBy }
                        strong { +"Multimedia Entertainment Hub LLC" }
                    }
                }
            }
            div("bottom") {
                div("wrap") {
                    p("rights") { +"© 2026 Multimedia Entertainment Hub LLC. ${t.rights} ${t.logos}" }
                    a(href = "#confidentialitate") { +t.privacyTitle }
                    a(href = "mailto:support@mehstudios.net") { +t.support }
                    a(href = "/?lang=${t.other}") {
                        attributes["hreflang"] = t.other
                        attributes["lang"] = t.other
                        +t.otherName
                    }
                }
            }
        }
    }
}

/** The FAIMA seal: black, and white in the dark theme; [dark] is for places that are always dark. */
private fun FlowContent.seal(dark: Boolean) {
    picture {
        attributes["class"] = "seal"
        if (!dark) {
            source {
                attributes["srcset"] = "/static/faima-seal-white.png"
                attributes["media"] = "(prefers-color-scheme: dark)"
            }
        }
        img(src = if (dark) "/static/faima-seal-white.png" else "/static/faima-seal.png", alt = "")
    }
}

/** A download button, or the platform's name and "coming soon" while the deployment has no link for it. */
private fun FlowContent.store(url: String?, label: String, platform: String, soon: String, kind: String) {
    val download = "M12 4v11M7 11l5 5 5-5M5 20h14"
    if (url != null) {
        a(href = url, classes = "store $kind") {
            unsafe { +svgIcon(download) }
            span { +label }
        }
    } else {
        span("store off") {
            +platform
            span("soon") { +soon }
        }
    }
}

private class Feature(val icon: String, val title: String, val text: String)

private class Texts(
    val lang: String,
    val other: String,
    val otherName: String,
    val self: String,
    val onThisPage: String,
    val download: String,
    val headline: String,
    val lead: String,
    val android: String,
    val ios: String,
    val soon: String,
    val facts: List<String>,
    val shot: String,
    val howTitle: String,
    val steps: List<Pair<String, String>>,
    val whatNav: String,
    val whatTitle: String,
    val features: List<Feature>,
    val staffTitle: String,
    val staffText: String,
    val staffButton: String,
    val privacyTitle: String,
    val privacyText: String,
    val faculty: String,
    val university: String,
    val developedBy: String,
    val rights: String,
    val logos: String,
    val support: String,
    val weekWord: String,
    val oddWeek: String,
    val evenWeek: String,
    val months: List<String>,
) {
    /** "9 - 13 noiembrie", or "30 noiembrie - 4 decembrie" across a month. */
    fun range(first: LocalDate, last: LocalDate): String = when {
        first == last -> date(last)
        first.month == last.month -> "${first.day} - ${date(last)}"
        else -> "${date(first)} - ${date(last)}"
    }

    private fun date(d: LocalDate) = "${d.day} ${months[d.month.ordinal]}"
}

private const val ICON_PLACE = "M12 21s7-6 7-11a7 7 0 1 0-14 0c0 5 7 11 7 11zM12 12a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"
private const val ICON_CATCH_UP = "M20 12a8 8 0 1 1-2.3-5.7M20 4v4h-4"
private const val ICON_GLOBE = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"

private val RO = Texts(
    lang = "ro",
    other = "en",
    otherName = "English",
    self = "/?lang=ro",
    onThisPage = "Pe această pagină",
    download = "Descarcă",
    headline = "Orarul tău de la FAIMA, la zi, pe telefon",
    lead = "Grupa ta, sălile, profesorii și anunțurile facultății, într-o aplicație pentru Android și iOS. " +
        "Alegi grupa o singură dată și vezi ce ai azi, în ce sală și cu cine.",
    android = "Descarcă pentru Android",
    ios = "Descarcă pentru iOS",
    soon = "în curând",
    facts = listOf(
        "Gratuită pentru studenții FAIMA",
        "Merge fără internet după prima sincronizare",
        "În română și engleză",
    ),
    shot = "Captură din aplicație: orarul unei zile în tema luminoasă sau, în tema întunecată, " +
        "pagina unei materii cu laboratoarele tuturor grupelor",
    howTitle = "Cum funcționează",
    steps = listOf(
        "Îți faci cont" to
            "Din aplicație, cu numele, adresa de email și telefonul. Nimeni nu verifică ce introduci: " +
            "aplicația e un instrument, nu o evidență oficială.",
        "Alegi grupa" to
            "Specializarea, anul, grupa și semigrupa. Dacă ești la Erasmus, îți alegi materiile și orele " +
            "din contractul de studii.",
        "Vezi orarul" to
            "Pe zi sau pe săptămână, cu ora curentă marcată și cu săptămânile pare și impare deja socotite.",
        "Afli ce e nou" to
            "Anunțurile și evenimentele facultății ajung la toți studenții. Schimbările de orar ajung în " +
            "aplicație la următoarea sincronizare.",
    ),
    whatNav = "Ce face",
    whatTitle = "Ce face aplicația",
    features = listOf(
        Feature(
            Section.SEMESTERS.icon,
            "Orarul grupei tale",
            "Alegi o dată grupa și semigrupa. Vezi ziua, săptămâna sau un tabel ca cel de la avizier, " +
                "cu sala și profesorul fiecărei ore.",
        ),
        Feature(
            Section.SCHEDULE.icon,
            "Săptămâni pare și impare",
            "Aplicația știe în ce săptămână a semestrului ești și arată doar orele care se țin atunci. " +
                "Vacanțele sunt marcate.",
        ),
        Feature(ICON_PLACE, "Săli și clădiri", "Fiecare oră își arată sala și clădirea, cu legătură spre hartă."),
        Feature(
            Section.PROFESSORS.icon,
            "Profesori",
            "Unde predă un profesor chiar acum, ce ore are săptămâna aceasta, când ține consultații " +
                "și adresa lui de email.",
        ),
        Feature(
            ICON_CATCH_UP,
            "Recuperări",
            "Găsești laboratorul altei grupe care nu se suprapune cu orarul tău și îl adaugi o singură dată " +
                "sau pentru tot semestrul.",
        ),
        Feature(
            ICON_GLOBE,
            "Studenți Erasmus",
            "Îți alegi materiile și orele din contractul de studii. O oră care se suprapune cu alta " +
                "este refuzată pe loc, cu numele celei cu care se suprapune.",
        ),
        Feature(
            Section.ANNOUNCEMENTS.icon,
            "Anunțuri",
            "Anunțurile secretariatului ajung la toți studenții, cu notificare pe telefon, și rămân în " +
                "aplicație și fără internet.",
        ),
        Feature(
            Section.EVENTS.icon,
            "Evenimente",
            "Ce organizează facultatea, cu data, ora, locul și descrierea.",
        ),
        Feature(
            Section.REPORTS.icon,
            "Probleme de orar",
            "Dacă o oră pare greșită, o semnalezi din fișa ei și ajunge la cei care se ocupă de orar.",
        ),
    ),
    staffTitle = "Pentru secretariat",
    staffText = "Orarul, calendarul semestrului, anunțurile și evenimentele se administrează dintr-un panou " +
        "în browser. Orarul se importă dintr-un fișier CSV și se previzualizează înainte să ajungă la " +
        "studenți; problemele de orar semnalate din aplicație apar într-o singură listă.",
    staffButton = "Intră în panoul de administrare",
    privacyTitle = "Confidențialitate",
    privacyText = "Serverul păstrează emailul, un hash al parolei, numele, numărul de telefon, grupa sau orele " +
        "alese, limba și, cât timp ești conectat, tokenul de notificări al telefonului. Anunțurile și " +
        "evenimentele noi ajung pe telefon prin Firebase Cloud Messaging de la Google, care transmite titlul " +
        "și primele rânduri. Dacă semnalezi o problemă cu orarul, personalul autorizat poate vedea numele, " +
        "emailul, descrierea ta și detaliile orei. Nu păstrează locația, contactele sau date despre utilizare. " +
        "Ștergerea contului șterge și sesizările tale.",
    faculty = "Facultatea de Antreprenoriat, Ingineria și Managementul Afacerilor",
    university = "Universitatea Națională de Știință și Tehnologie POLITEHNICA București",
    developedBy = "Dezvoltată de",
    rights = "Toate drepturile rezervate.",
    logos = "Numele și siglele FAIMA și UNSTPB aparțin universității.",
    support = "Asistență",
    weekWord = "Săptămâna",
    oddWeek = "Săptămâna impară",
    evenWeek = "Săptămâna pară",
    months = listOf(
        "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
        "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie",
    ),
)

private val EN = Texts(
    lang = "en",
    other = "ro",
    otherName = "Română",
    self = "/?lang=en",
    onThisPage = "On this page",
    download = "Download",
    headline = "Your FAIMA timetable, up to date, on your phone",
    lead = "Your group, rooms, professors and the faculty's announcements, in one app for Android and iOS. " +
        "Pick your group once and see what you have today, in which room and with whom.",
    android = "Get it for Android",
    ios = "Get it for iOS",
    soon = "coming soon",
    facts = listOf(
        "Free for FAIMA students",
        "Works offline after the first sync",
        "In Romanian and English",
    ),
    shot = "A screen of the app: a day's timetable in the light theme or, in the dark theme, " +
        "a subject with every group's labs",
    howTitle = "How it works",
    steps = listOf(
        "Create an account" to
            "In the app, with your name, email address and phone number. Nobody checks what you enter: " +
            "the app is a tool, not an official record.",
        "Pick your group" to
            "Your programme, year, group and half-group. As an Erasmus student you pick your subjects and " +
            "slots from your learning agreement.",
        "See your timetable" to
            "By day or by week, with the current time marked and odd and even weeks already worked out.",
        "Stay up to date" to
            "The faculty's announcements and events reach every student. Timetable changes reach the app " +
            "at its next sync.",
    ),
    whatNav = "What it does",
    whatTitle = "What the app does",
    features = listOf(
        Feature(
            Section.SEMESTERS.icon,
            "Your group's timetable",
            "Pick your group and half-group once. See the day, the week, or a table like the one " +
                "on the notice board, with the room and professor of every class.",
        ),
        Feature(
            Section.SCHEDULE.icon,
            "Odd and even weeks",
            "The app knows which week of the semester it is and only shows the classes held that week. " +
                "Breaks are marked.",
        ),
        Feature(ICON_PLACE, "Rooms and buildings", "Every class shows its room and building, with a link to the map."),
        Feature(
            Section.PROFESSORS.icon,
            "Professors",
            "Where a professor is teaching right now, their classes this week, their office hours and " +
                "their email address.",
        ),
        Feature(
            ICON_CATCH_UP,
            "Catching up",
            "Find another group's lab that does not clash with your timetable and add it once, or for the " +
                "whole semester.",
        ),
        Feature(
            ICON_GLOBE,
            "Erasmus students",
            "Pick the subjects and slots from your learning agreement. A slot that overlaps another " +
                "is refused on the spot, naming the one it clashes with.",
        ),
        Feature(
            Section.ANNOUNCEMENTS.icon,
            "Announcements",
            "The secretariat's announcements reach every student with a notification on the phone, and " +
                "stay in the app, offline too.",
        ),
        Feature(
            Section.EVENTS.icon,
            "Events",
            "What the faculty organises, with the date, time, place and description.",
        ),
        Feature(
            Section.REPORTS.icon,
            "Timetable problems",
            "If a class looks wrong, report it from its sheet and it reaches the people who manage the timetable.",
        ),
    ),
    staffTitle = "For the secretariat",
    staffText = "The timetable, the semester calendar, the announcements and the events are managed in a " +
        "dashboard in the browser. The timetable is imported from a CSV file and previewed before it reaches " +
        "students; timetable problems reported from the app arrive in one list.",
    staffButton = "Open the dashboard",
    privacyTitle = "Privacy",
    privacyText = "The server keeps your email, a hash of your password, your name, your phone number, your " +
        "group or picked subjects, your language and, while you are signed in, this phone's notification " +
        "token. New announcements and events reach your phone through Google's Firebase Cloud Messaging, " +
        "which carries their title and first lines. If you report a timetable problem, authorized timetable " +
        "staff can see your name, email, description and the class details. It keeps no location, contacts " +
        "or usage tracking. Deleting your account also removes your reports.",
    faculty = "Faculty of Entrepreneurship, Business Engineering and Management",
    university = "National University of Science and Technology POLITEHNICA Bucharest",
    developedBy = "Developed by",
    rights = "All rights reserved.",
    logos = "The FAIMA and UNSTPB names and logos belong to the university.",
    support = "Support",
    weekWord = "Week",
    oddWeek = "Odd week",
    evenWeek = "Even week",
    months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ),
)

private val SITE_STYLES = """
:root { --onp: #FFFFFF; --foot: #292929; --foot-line: #3A3A3A; --foot-text: #D6D6D2; --foot-muted: #A3A39F;
  --lift: 0 12px 32px rgba(0, 0, 0, 0.10); color-scheme: light; }
@media (prefers-color-scheme: dark) {
  :root { --bg: #0A0A0A; --surface: #141414; --alt: #1E1E1E; --border: #2A2A2A; --strong: #3A3A3A; --text: #EDEDED;
    --muted: #9A9A9A; --primary: #2EBFF6; --primary-soft: #10202B; --now: #F0564C; --onp: #0A0A0A; --foot: #141414;
    --foot-line: #2A2A2A; --lift: 0 12px 32px rgba(0, 0, 0, 0.5); color-scheme: dark; }
}
@media (prefers-reduced-motion: no-preference) { html { scroll-behavior: smooth; } }
body { line-height: 21px; }
:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
.wrap { max-width: 1168px; margin: 0 auto; padding-left: 24px; padding-right: 24px; }
section[id] { scroll-margin-top: 16px; }
h2 { font-size: 22px; line-height: 28px; margin: 0; }
.seal { display: block; flex: none; }
.seal img { display: block; width: 100%; height: 100%; object-fit: contain; }

.top { background: var(--surface); border-bottom: 1px solid var(--border); }
.top .wrap { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 28px; padding-top: 14px; padding-bottom: 14px; }
.brand { display: flex; align-items: center; gap: 10px; color: var(--text); text-decoration: none; border-radius: 6px; }
.brand .seal { width: 44px; height: 36px; }
.brand .name strong { display: block; font-size: 17px; line-height: 22px; font-weight: 700; }
.brand .name span { display: block; font-size: 12px; line-height: 16px; color: var(--muted); white-space: nowrap; }
.top .spacer { flex: 1; }
.sections { display: flex; flex-wrap: wrap; gap: 4px 24px; font-size: 14px; line-height: 20px; font-weight: 500; }
.lk { color: var(--text); text-decoration: none; padding: 6px 0; border-radius: 4px; transition: color 150ms; }
.lk:hover { color: var(--primary); }
.lang { color: var(--muted); font-size: 13px; line-height: 18px; font-weight: 600; letter-spacing: 0.04em; text-decoration: none;
  border: 1px solid var(--border); border-radius: 6px; padding: 7px 10px; transition: color 150ms, border-color 150ms; }
.lang:hover { color: var(--text); border-color: var(--muted); }
.cta, .store { display: inline-flex; align-items: center; gap: 10px; border: 1px solid var(--primary); border-radius: 6px;
  background: var(--primary); color: var(--onp); font-weight: 600; text-decoration: none; white-space: nowrap;
  transition: opacity 150ms, transform 150ms, border-color 150ms; }
.cta { padding: 7px 14px; font-size: 14px; line-height: 20px; }
.cta:hover, a.store:hover { opacity: 0.9; transform: translateY(-1px); }
.cta:active, a.store:active { transform: none; }

.hero { display: flex; flex-wrap: wrap; align-items: center; gap: 40px 64px; padding-top: 56px; padding-bottom: 64px; }
.hero .copy { flex: 1 1 440px; min-width: 0; max-width: 600px; }
.now { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin: 0 0 18px; font-size: 13px; line-height: 18px; }
.ticket { display: inline-flex; align-items: stretch; border: 1px solid var(--border); border-radius: 6px; overflow: hidden;
  background: var(--surface); }
.ticket .num { background: var(--text); color: var(--bg); font-weight: 700; padding: 3px 9px; }
.ticket .text { padding: 3px 10px; font-weight: 500; }
.now .semester { color: var(--muted); }
.hero h1 { font-size: 40px; line-height: 46px; letter-spacing: -0.01em; margin: 0; max-width: 15em; }
.lead { font-size: 17px; line-height: 26px; color: var(--muted); margin: 16px 0 0; max-width: 520px; }
.stores { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 28px; }
.store { padding: 12px 20px; font-size: 15px; line-height: 21px; }
.store.ios { background: var(--text); border-color: var(--text); color: var(--bg); }
.store.off { background: var(--alt); border: 1px dashed var(--strong); color: var(--muted); cursor: default; gap: 0; }
.store .soon { font-weight: 500; }
.store .soon::before { content: "·"; margin: 0 6px; }
.facts { display: flex; flex-wrap: wrap; gap: 6px 20px; list-style: none; margin: 20px 0 0; padding: 0; font-size: 14px;
  line-height: 20px; color: var(--muted); }
.facts li { display: flex; align-items: center; gap: 8px; }
.facts li::before { content: ""; flex: none; width: 5px; height: 5px; border-radius: 50%; background: var(--primary); }
.shot { flex: 0 1 300px; width: 300px; max-width: 100%; margin: 0 auto; }
.shot picture { display: block; }
.shot img { display: block; width: 100%; height: auto; border: 1px solid var(--border); border-radius: 28px;
  background: var(--surface); box-shadow: var(--lift); }

.band { background: var(--surface); border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
.band .wrap { padding-top: 44px; padding-bottom: 48px; }
.steps { list-style: none; margin: 24px 0 0; padding: 0; display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 230px), 1fr)); gap: 24px 32px; }
.steps li { border-top: 2px solid var(--text); padding-top: 14px; }
.steps .step { display: block; font-size: 13px; line-height: 18px; font-weight: 600; color: var(--muted); }
.steps h3 { font-size: 17px; line-height: 22px; margin: 4px 0 6px; }
.steps p { margin: 0; color: var(--muted); }

.features { padding-top: 48px; padding-bottom: 48px; }
.features h2 { margin-bottom: 8px; }
.features .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr)); gap: 0 48px; }
.feature { display: flex; gap: 14px; padding: 16px 0; border-bottom: 1px solid var(--border); }
.feature svg { flex: none; margin-top: 1px; }
.feature h3 { font-size: 15px; line-height: 21px; margin: 0; }
.feature p { margin: 2px 0 0; color: var(--muted); font-size: 14px; line-height: 20px; }

.staff { padding-bottom: 48px; }
.panel { display: flex; flex-wrap: wrap; align-items: center; gap: 16px 40px; background: var(--surface);
  border: 1px solid var(--border); border-radius: 8px; padding: 24px 28px; }
.panel > div { flex: 1 1 420px; min-width: 0; }
.panel h2 { font-size: 17px; line-height: 22px; }
.panel p { margin: 6px 0 0; color: var(--muted); max-width: 640px; }
.plain { display: inline-flex; border: 1px solid var(--strong); border-radius: 6px; background: var(--bg); color: var(--text);
  padding: 10px 18px; font-size: 14px; line-height: 20px; font-weight: 600; text-decoration: none;
  transition: border-color 150ms, color 150ms; }
.plain:hover { border-color: var(--primary); color: var(--primary); }
.privacy { padding-bottom: 56px; }
.privacy h2 { font-size: 17px; line-height: 22px; }
.privacy p { margin: 6px 0 0; color: var(--muted); max-width: 720px; font-size: 14px; line-height: 20px; }

.foot { background: var(--foot); border-top: 1px solid var(--foot-line); color: var(--foot-text); }
.foot :focus-visible { outline-color: #2EBFF6; }
.foot a { color: var(--foot-text); text-decoration: none; transition: color 150ms; }
.foot a:hover { color: #FFFFFF; }
.foot .main { display: flex; flex-wrap: wrap; align-items: center; gap: 24px 56px; padding-top: 36px; padding-bottom: 28px; }
.foot .faculty { flex: 1 1 380px; min-width: 0; display: flex; align-items: center; gap: 16px; }
.foot .faculty .seal { width: 64px; height: 64px; }
.foot strong { display: block; color: #FFFFFF; font-size: 15px; line-height: 21px; font-weight: 600; }
.foot .faculty span span, .foot .meh span span { display: block; color: var(--foot-muted); font-size: 13px; line-height: 18px; }
.foot .faculty:hover strong, .foot .meh:hover strong { text-decoration: underline; text-underline-offset: 2px; }
.foot .meh { display: flex; align-items: center; gap: 12px; }
.foot .meh img { flex: none; width: 34px; height: 34px; object-fit: contain; }
.foot .bottom { border-top: 1px solid var(--foot-line); }
.foot .bottom .wrap { display: flex; flex-wrap: wrap; gap: 6px 24px; padding-top: 14px; padding-bottom: 20px; font-size: 13px;
  line-height: 18px; color: var(--foot-muted); }
.foot .rights { flex: 1 1 300px; margin: 0; }
.foot .bottom a { color: var(--foot-muted); }
.foot .bottom a:hover { color: #FFFFFF; }

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { transition: none !important; }
  .cta:hover, a.store:hover { transform: none; }
}
@media (max-width: 720px) {
  .top .wrap { gap: 8px 12px; padding-top: 10px; padding-bottom: 6px; }
  .sections { order: 10; flex-basis: 100%; gap: 0 16px; }
  .hero { padding-top: 32px; padding-bottom: 48px; }
  .hero h1 { font-size: 32px; line-height: 38px; }
}
@media (max-width: 520px) {
  .wrap { padding-left: 16px; padding-right: 16px; }
  .stores > * { flex: 1 1 100%; justify-content: center; }
  .panel { padding: 20px; }
  .panel > div { flex-basis: 100%; }
}
"""
