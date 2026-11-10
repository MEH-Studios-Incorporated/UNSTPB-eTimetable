// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import net.mehstudios.etimetable.server.admin.applyScheduleChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScheduleRoutesTest {
    @Test
    fun previewDoesNotSaveAndStaleConfirmationCannotOverwriteANewerEdit() = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        services.datasets.replace(sampleDataset(), true)
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
        val url = "/admin/semesters/2026-1/schedule"
        val schedulePage = browser.get(url).bodyAsText()
        assertTrue("Gestionare orar" in schedulePage)
        assertTrue("data-search" in schedulePage)
        assertTrue("input.addEventListener('input', filter)" in schedulePage)
        assertTrue("Microeconomie" in browser.get("$url?q=1541E").bodyAsText())
        val version = services.datasets.version("2026-1")!!
        val before = services.datasets.current()!!.dataset
        val selected = before.activities.take(2)
        fun form(confirm: Boolean) = parameters {
            append("version", version.toString())
            selected.forEach { append("activity", it.id) }
            append("room", "")
            append("professor", "")
            append("shift", "30")
            if (confirm) append("confirm", "yes")
        }
        assertEquals(HttpStatusCode.OK, browser.submitForm(url, form(false)).status)
        assertEquals(before, services.datasets.current()!!.dataset)
        assertEquals(HttpStatusCode.Found, browser.submitForm(url, form(true)).status)
        val changed = services.datasets.current()!!.dataset
        assertNotEquals(before.activities.first().slot.start, changed.activities.first().slot.start)
        assertEquals(before.activities.drop(2), changed.activities.drop(2))
        val stale = browser.submitForm(url, form(true))
        assertEquals(HttpStatusCode.Found, stale.status)
        assertTrue("err=" in stale.headers[HttpHeaders.Location].orEmpty())
        assertEquals(changed, services.datasets.current()!!.dataset)
        assertEquals(
            HttpStatusCode.Forbidden,
            browser.submitForm(url, form(true)) {
                header(HttpHeaders.Origin, "https://other.test")
            }.status,
        )
        val currentVersion = services.datasets.version("2026-1")!!
        val emptySelection = browser.submitForm(
            url,
            parameters {
                append("version", currentVersion.toString())
                append("room", "")
                append("professor", "")
                append("shift", "30")
            },
        )
        assertEquals(HttpStatusCode.Found, emptySelection.status)
        val errorPage = browser.get(emptySelection.headers[HttpHeaders.Location].orEmpty()).bodyAsText()
        assertTrue("notice error" in errorPage)
        assertTrue("Selectează între 1 și 200 de activități." in errorPage)
    }

    @Test
    fun dayAndIncompleteFiltersReachTheServer() = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        val dataset = sampleDataset()
        services.datasets.replace(dataset, true)
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
        val url = "/admin/semesters/2026-1/schedule"
        fun listed(page: String) = dataset.activities.filter { "name=\"activity\" value=\"${it.id}\"" in page }
        val all = browser.get(url).bodyAsText()
        assertEquals(dataset.activities.toSet(), listed(all).toSet())
        // The filter form must submit normally; only the text search runs in the page.
        assertTrue("preventDefault" !in all)
        val day = dataset.activities.last().slot.isoDay
        assertTrue(dataset.activities.any { it.slot.isoDay != day })
        val onDay = dataset.activities.filter { it.slot.isoDay == day }
        assertEquals(onDay.toSet(), listed(browser.get("$url?day=$day").bodyAsText()).toSet())
        val incomplete = dataset.activities.filter { it.roomId == null || it.professorId == null }
        assertTrue(incomplete.isNotEmpty() && incomplete.size < dataset.activities.size)
        assertEquals(incomplete.toSet(), listed(browser.get("$url?incomplete=on").bodyAsText()).toSet())
    }

    @Test
    fun unknownSelectionsAndCrossMidnightMovesAreRejected() {
        val dataset = sampleDataset()
        assertFailsWith<IllegalArgumentException> {
            applyScheduleChange(
                dataset,
                parameters {
                    append("activity", "unknown")
                    append("shift", "60")
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyScheduleChange(
                dataset,
                parameters {
                    append("activity", dataset.activities.first().id)
                    append("shift", "-720")
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyScheduleChange(
                dataset,
                parameters {
                    append("activity", dataset.activities.first().id)
                    append("shift", "0")
                    append("room", "unknown")
                },
            )
        }
    }
}
