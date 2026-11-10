// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import net.mehstudios.etimetable.schedule.facultyNow
import net.mehstudios.etimetable.schedule.mondayOfWeek
import net.mehstudios.etimetable.server.site.WeekTicket
import net.mehstudios.etimetable.server.site.weekTicket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class SiteRoutesTest {
    @Test
    fun thePageHasEverySectionAndTheWayToTheDashboard() = testApplication {
        application { module(Services(TestPostgres.freshDatabase())) }
        val page = client.get("/?lang=ro")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        listOf("descarca", "cum", "ce", "secretariat", "confidentialitate").forEach {
            assertTrue("id=\"$it\"" in html, "section $it")
            assertTrue("href=\"#$it\"" in html, "link to $it")
        }
        assertTrue("<html lang=\"ro\"" in html)
        assertTrue("Cum funcționează" in html && "Ce face aplicația" in html && "Pentru secretariat" in html)
        assertTrue("href=\"/admin\"" in html)
        assertTrue("Dezvoltată de" in html && "Multimedia Entertainment Hub LLC" in html)
        assertTrue("Universitatea Națională de Știință și Tehnologie POLITEHNICA București" in html)
        assertTrue("href=\"mailto:support@mehstudios.net\"" in html)
        assertTrue("href=\"/?lang=en\"" in html)
    }

    @Test
    fun theProductRulesAreSaidAsTheyAre() = testApplication {
        application { module(Services(TestPostgres.freshDatabase())) }
        val html = client.get("/?lang=ro").bodyAsText()
        assertTrue("Nimeni nu verifică ce introduci" in html)
        assertTrue("ajung la toți studenții" in html)
        assertTrue("fără internet după prima sincronizare" in html)
        assertTrue("Studenți Erasmus" in html && "contractul de studii" in html)
    }

    @Test
    fun theScreenshotFollowsTheThemeAndNothingComesFromElsewhere() = testApplication {
        application { module(Services(TestPostgres.freshDatabase())) }
        val html = client.get("/").bodyAsText()
        assertTrue(
            "<source srcset=\"/static/app-dark.webp\" media=\"(prefers-color-scheme: dark)\">" in html,
        )
        assertTrue("src=\"/static/app-light.webp\"" in html)
        assertTrue("@media (prefers-color-scheme: dark)" in html)
        assertTrue("@media (prefers-reduced-motion: reduce)" in html)
        // fonts, pictures and styles all come from this server
        assertFalse(Regex("""(src|srcset)="https?:""").containsMatchIn(html))
        assertFalse(Regex("""url\(["']?https?:""").containsMatchIn(html))
        assertFalse("<link rel=\"stylesheet\"" in html)
        assertFalse("<script" in html)
    }

    @Test
    fun englishFromTheSwitchOrTheBrowser() = testApplication {
        application { module(Services(TestPostgres.freshDatabase())) }
        val switched = client.get("/?lang=en").bodyAsText()
        assertTrue("<html lang=\"en\"" in switched)
        assertTrue("How it works" in switched && "What the app does" in switched && "For the secretariat" in switched)
        assertTrue("Developed by" in switched && "href=\"/?lang=ro\"" in switched)
        assertTrue("Nobody checks what you enter" in switched)
        val browser = client.get("/") { header(HttpHeaders.AcceptLanguage, "en-GB,en;q=0.9") }.bodyAsText()
        assertTrue("<html lang=\"en\"" in browser)
        val romanian = client.get("/") { header(HttpHeaders.AcceptLanguage, "en-GB,ro;q=0.8") }.bodyAsText()
        assertTrue("<html lang=\"ro\"" in romanian)
    }

    @Test
    fun withoutDownloadLinksTheAppsAreComingSoon() = testApplication {
        application { module(Services(TestPostgres.freshDatabase())) }
        val html = client.get("/?lang=ro").bodyAsText()
        assertTrue("<span class=\"store off\">Android<span class=\"soon\">în curând</span>" in html)
        assertTrue("<span class=\"store off\">iOS<span class=\"soon\">în curând</span>" in html)
        assertFalse("class=\"store android\"" in html || "class=\"store ios\"" in html)
        assertFalse("Descarcă pentru Android" in html)
    }

    @Test
    fun downloadLinksComeFromTheDeployment() = testApplication {
        val apk = "https://downloads.faima.test/etimetable.apk"
        application { module(Services(TestPostgres.freshDatabase()), downloads = Downloads(android = apk)) }
        val english = client.get("/?lang=en").bodyAsText()
        assertTrue("<a href=\"$apk\" class=\"store android\">" in english)
        assertTrue("Get it for Android" in english)
        assertTrue("<span class=\"store off\">iOS<span class=\"soon\">coming soon</span>" in english)
    }

    @Test
    fun theCurrentWeekShowsWhenASemesterIsServed() = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        // a semester that started two weeks before this one, whatever day the test runs
        val today = Clock.System.facultyNow().date
        val sample = sampleDataset()
        val semester = sample.semester.copy(
            firstDay = today.mondayOfWeek().minus(14, DateTimeUnit.DAY),
            lastDay = today.plus(60, DateTimeUnit.DAY),
            breaks = emptyList(),
        )
        services.datasets.replace(sample.copy(semester = semester), makeCurrent = true)
        application { module(services) }
        val expected = weekTicket(semester, today, english = false)!!
        val html = client.get("/?lang=ro").bodyAsText()
        assertTrue("<span class=\"num tnum\" aria-hidden=\"true\">${expected.number}</span>" in html)
        assertTrue("<span class=\"text\">${expected.text}</span>" in html)
        assertTrue("Semestrul 1, 2026-2027" in html)
    }

    @Test
    fun withoutASemesterThereIsNoTicket() = testApplication {
        application { module(Services(TestPostgres.freshDatabase())) }
        val html = client.get("/?lang=ro").bodyAsText()
        assertFalse("class=\"ticket\"" in html)
        assertTrue("<h1>" in html)
    }

    @Test
    fun theTicketNamesTheWeekAndItsDays() {
        val semester = sampleDataset().semester
        // Tuesday 10 November 2026: the seventh week, which starts on 9 November
        val tuesday = LocalDate(2026, 11, 10)
        assertEquals(WeekTicket(7, "Săptămâna impară · 9 - 13 noiembrie"), weekTicket(semester, tuesday, false))
        assertEquals(WeekTicket(7, "Odd week · 9 - 13 November"), weekTicket(semester, tuesday, true))
        // on Saturday it is already the coming week
        assertEquals(
            WeekTicket(8, "Săptămâna pară · 16 - 20 noiembrie"),
            weekTicket(semester, LocalDate(2026, 11, 14), false),
        )
        assertEquals(
            WeekTicket(10, "Săptămâna pară · 30 noiembrie - 4 decembrie"),
            weekTicket(semester, LocalDate(2026, 12, 1), false),
        )
        assertEquals(
            WeekTicket(null, "Vacanța de iarnă · 21 decembrie - 3 ianuarie"),
            weekTicket(semester, LocalDate(2026, 12, 23), false),
        )
        assertEquals(
            WeekTicket(null, "Winter break · 21 December - 3 January"),
            weekTicket(semester, LocalDate(2026, 12, 23), true),
        )
        assertNull(weekTicket(semester, LocalDate(2026, 9, 1), false))
        assertNull(weekTicket(semester, LocalDate(2027, 2, 1), false))
    }

    @Test
    fun downloadsAreReadFromTheEnvironment() {
        val database = mapOf(
            "ETT_DB_URL" to "jdbc:postgresql://db/ett",
            "ETT_DB_USER" to "ett",
            "ETT_DB_PASSWORD" to "x",
        )
        val config = ServerConfig.fromEnvironment(
            database + ("ETT_ANDROID_URL" to "https://downloads.faima.test/a.apk"),
        )
        assertEquals("https://downloads.faima.test/a.apk", config.downloads.android)
        assertNull(config.downloads.ios)
    }
}
