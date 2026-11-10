// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.sync

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.model.Event
import net.mehstudios.etimetable.store.inMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class EventsSyncTest {
    private val careerDay = Event(
        id = "e1",
        title = "Ziua Carierei FAIMA",
        startsAt = Instant.parse("2026-11-19T08:00:00Z"),
        place = "Aula BN",
        image = "/api/v1/images/${"a".repeat(64)}.jpg",
        imageRatio = 16f / 9,
        publishedAt = Instant.parse("2026-11-10T09:00:00Z"),
    )
    private var served = listOf(careerDay)
    private val sent = mutableListOf<String?>()
    private val engine = MockEngine { request ->
        sent += request.headers[HttpHeaders.IfNoneMatch]
        val etag = "\"events-${served.size}\""
        if (request.headers[HttpHeaders.IfNoneMatch] == etag) {
            respond("", HttpStatusCode.NotModified)
        } else {
            respond(
                AppJson.encodeToString(served),
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ETag to listOf(etag),
                ),
            )
        }
    }
    private val store = inMemoryStore()
    private val sync = Sync(ApiClient(engine, "http://server.test"), store)

    @Test
    fun downloadsTheListThenOnlyChecks() = runTest {
        assertTrue(sync.refreshEvents())
        assertFalse(sync.refreshEvents())
        assertEquals(listOf(null, "\"events-1\""), sent)
        assertEquals(listOf(careerDay), store.events()?.events)

        served = listOf(careerDay, careerDay.copy(id = "e2", title = "Bal FAIMA"))
        assertTrue(sync.refreshEvents())
        assertEquals(listOf("e1", "e2"), store.events()?.events?.map { it.id })
    }

    @Test
    fun anEventWithoutAnEndIsOverWhenItStarts() {
        assertEquals(careerDay.startsAt, careerDay.over)
    }
}
