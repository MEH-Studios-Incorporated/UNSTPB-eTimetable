// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteRoutesTest {
    @Test
    fun withoutDownloadLinksTheAppsAreComingSoon() = testApplication {
        application { module(Services(TestPostgres.freshDatabase())) }
        val page = client.get("/")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue("în curând" in html)
        assertTrue("href=\"/admin\"" in html)
    }

    @Test
    fun downloadLinksComeFromTheDeployment() = testApplication {
        val apk = "https://downloads.faima.test/etimetable.apk"
        application { module(Services(TestPostgres.freshDatabase()), downloads = Downloads(android = apk)) }
        val english = client.get("/?lang=en").bodyAsText()
        assertTrue("href=\"$apk\"" in english)
        assertTrue("Get it for Android" in english)
        assertTrue("iPhone" in english && "coming soon" in english)
    }

    @Test
    fun theCurrentWeekShowsWhenASemesterIsServed() = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        services.datasets.replace(sampleDataset(), makeCurrent = true)
        application { module(services) }
        assertTrue("Semestrul 1" in client.get("/?lang=ro").bodyAsText())
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
