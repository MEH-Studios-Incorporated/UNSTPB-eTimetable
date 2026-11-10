// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeekViewTest {
    private val week = "/admin/semesters/2026-1/schedule?view=week"

    private fun withAdmin(block: suspend ApplicationTestBuilder.(Services, HttpClient) -> Unit) = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        application { module(services) }
        val browser = createClient {
            install(HttpCookies)
            sameSite()
            followRedirects = false
        }
        browser.submitForm(
            "/admin/login",
            parameters {
                append("email", "admin@faima.test")
                append("password", "parola-admin")
            },
        )
        block(services, browser)
    }

    private data class Block(val classes: Set<String>, val href: String?, val label: String)

    /** Every block on the page, links and neutral ones alike. */
    private fun blocks(html: String): List<Block> =
        Regex("""<(a|div) ([^>]*class="wg-block[^"]*"[^>]*)>""").findAll(html).map { m ->
            fun attr(name: String) = Regex("""\b$name="([^"]*)"""").find(m.groupValues[2])?.groupValues?.get(1)
                ?.replace("&amp;", "&")
            Block(attr("class")!!.split(' ').toSet(), attr("href"), attr("title").orEmpty())
        }.toList()

    private fun List<Block>.activities() = mapNotNull { b ->
        b.href?.let { Regex("activity=([^&]+)").find(it)?.groupValues?.get(1) }
    }
        .toSet()

    @Test
    fun theWeekShowsAGroupHalfGroupRoomOrProfessor() = withAdmin { _, browser ->
        // without a choice, the first group
        val first = browser.get(week).bodyAsText()
        assertTrue("Grupa 1541E" in first)
        assertTrue("aria-current=\"page\">Săptămână" in first)
        assertEquals(
            setOf("micro-c", "contab-s", "calit-l-1541A", "calit-l-1541B", "calit-c", "mkt-p-1541"),
            blocks(first).activities(),
        )
        // each subject has its colour on its blocks
        assertTrue("--sc: #4C6A92" in first)

        val half = browser.get("$week&group=1541E%2FA").bodyAsText()
        assertTrue("Semigrupa 1541E/A" in half)
        assertEquals(setOf("micro-c", "contab-s", "calit-l-1541A", "calit-c", "mkt-p-1541"), blocks(half).activities())

        val room = browser.get("$week&room=ec210").bodyAsText()
        assertTrue("Sala EC210" in room)
        val roomBlocks = blocks(room)
        assertTrue(roomBlocks.activities().isEmpty())
        // the office hours in the room, as a neutral block that is not a link
        val office = roomBlocks.single()
        assertTrue("office" in office.classes)
        assertEquals(null, office.href)
        assertTrue("Ioana Marinescu" in office.label)

        val professor = browser.get("$week&professor=ilie").bodyAsText()
        assertEquals(
            setOf("calit-l-1541A", "calit-l-1541B", "calit-l-1542A", "calit-l-1542B"),
            blocks(professor).activities(),
        )
        val marinescu = blocks(browser.get("$week&professor=marinescu").bodyAsText())
        assertEquals(setOf("micro-c"), marinescu.activities())
        assertEquals(1, marinescu.count { "office" in it.classes })

        // the table view is still there, and links to the week
        val table = browser.get("/admin/semesters/2026-1/schedule").bodyAsText()
        assertTrue("schedule-bulk" in table)
        assertTrue("href=\"/admin/semesters/2026-1/schedule?view=week\"" in table)
    }

    @Test
    fun theParitySwitchDimsTheOtherWeeks() = withAdmin { _, browser ->
        fun dimmed(html: String) = blocks(html).filter { "dim" in it.classes }.activities()
        assertTrue(dimmed(browser.get("$week&group=1541E").bodyAsText()).isEmpty())
        val even = browser.get("$week&group=1541E&parity=pare").bodyAsText()
        assertEquals(setOf("mkt-p-1541"), dimmed(even))
        assertTrue("aria-current=\"true\">Pare" in even)
        // the blocks' links keep the switch
        assertTrue(blocks(even).all { it.href == null || "parity=pare" in it.href })
        assertTrue(dimmed(browser.get("$week&group=1541E&parity=impare").bodyAsText()).isEmpty())
        assertEquals(setOf("mkt-p-1542"), dimmed(browser.get("$week&group=1542E&parity=impare").bodyAsText()))
    }

    @Test
    fun aSelectedClassOpensInThePanelAndASaveComesBackToIt() = withAdmin { services, browser ->
        val url = "$week&group=1541E&parity=impare&activity=calit-l-1541A"
        val page = browser.get(url).bodyAsText()
        assertTrue("class=\"wg-panel\"" in page)
        assertTrue(blocks(page).single { "on" in it.classes }.href!!.endsWith("activity=calit-l-1541A"))
        assertTrue("action=\"/admin/semesters/2026-1/activities/calit-l-1541A\"" in page)
        assertTrue("Nicio suprapunere" in page)
        // the form shows the class, not the view's own parameters
        assertTrue("name=\"audience\" value=\"1541E/A\"" in page || "value=\"1541E/A\"" in page)
        val back = Regex("""name="back" value="([^"]*)"""").find(page)?.groupValues?.get(1)?.replace("&amp;", "&")
        assertNotNull(back)
        assertTrue(back.startsWith("/admin/semesters/2026-1/schedule?view=week"))
        assertTrue("activity=calit-l-1541A" in back && "parity=impare" in back)

        val saved = browser.submitForm(
            "/admin/semesters/2026-1/activities/calit-l-1541A",
            parameters {
                append("back", back)
                append("subject", "calit")
                append("type", "LAB")
                append("day", "3")
                append("start", "12:00")
                append("end", "14:00")
                append("weeks", "toate")
                append("professor", "ilie")
                append("room", "EC004")
                append("audience", "1541E/A")
            },
        )
        assertEquals(HttpStatusCode.Found, saved.status)
        val location = saved.headers[HttpHeaders.Location].orEmpty()
        assertTrue(location.startsWith("$back&msg="), location)
        val after = browser.get(location).bodyAsText()
        assertTrue("Activitatea a fost salvată." in after)
        assertTrue("class=\"wg-panel\"" in after)
        assertEquals("ec004", services.datasets.current()!!.dataset.activitiesById.getValue("calit-l-1541A").roomId)

        // without a selection, or with one that is gone, there is no panel
        assertFalse("class=\"wg-panel\"" in browser.get("$week&group=1541E").bodyAsText())
        assertFalse("class=\"wg-panel\"" in browser.get("$week&group=1541E&activity=gone").bodyAsText())
    }

    @Test
    fun aClashIsNamedInThePanel() = withAdmin { _, browser ->
        // the lab moved onto the series course
        browser.submitForm(
            "/admin/semesters/2026-1/activities/calit-l-1541A",
            parameters {
                append("subject", "calit")
                append("type", "LAB")
                append("day", "3")
                append("start", "09:00")
                append("end", "11:00")
                append("weeks", "toate")
                append("professor", "marinescu")
                append("room", "AN015")
                append("audience", "1541E/A")
            },
        )
        val page = browser.get("$week&activity=calit-l-1541A").bodyAsText()
        assertTrue("Se suprapune cu:" in page)
        assertTrue("aceeași sală, același profesor, aceleași grupe" in page)
    }

    @Test
    fun groupRoomAndProfessorPagesUseTheGrid() = withAdmin { _, browser ->
        val base = "/admin/semesters/2026-1"
        for (path in listOf("$base/groups/1541E", "$base/rooms/bn212b", "$base/professors/ilie")) {
            val page = browser.get(path).bodyAsText()
            assertTrue("class=\"wg\"" in page, path)
            assertFalse("class=\"week\"" in page, path)
            val links = blocks(page).mapNotNull { it.href }
            assertTrue(links.isNotEmpty(), path)
            // an edit goes back to the same page
            assertTrue(links.all { "back=" + path.replace("/", "%2F") in it }, path)
        }
        val group = browser.get("$base/groups/1541E").bodyAsText()
        assertTrue("1541E/A" in group)
        val professor = blocks(browser.get("$base/professors/marinescu").bodyAsText())
        assertEquals(1, professor.count { "office" in it.classes })
    }
}
