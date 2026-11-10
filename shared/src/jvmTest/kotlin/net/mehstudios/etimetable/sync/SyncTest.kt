// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.sync

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.schedule.ChangeKind
import net.mehstudios.etimetable.schedule.ChangeNote
import net.mehstudios.etimetable.store.inMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

class SyncTest {
    private var now = Instant.fromEpochSeconds(1_000)
    private val clock = object : Clock {
        override fun now() = now
    }
    private var serverUp = true
    private var served = SampleData.dataset
    private var etag = "\"v1\""
    private val requests = mutableListOf<String?>()

    private val engine = MockEngine { request ->
        requests += request.headers[HttpHeaders.IfNoneMatch]
        when {
            !serverUp -> respond("", HttpStatusCode.BadGateway)
            request.headers[HttpHeaders.IfNoneMatch] == etag -> respond("", HttpStatusCode.NotModified)
            else -> respond(
                AppJson.encodeToString(served),
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ETag to listOf(etag),
                ),
            )
        }
    }
    private val store = inMemoryStore()
    private val sync = Sync(ApiClient(engine, "http://server.test"), store, clock)

    @Test
    fun downloadsOnceThenOnlyChecks() = runTest {
        assertEquals(DatasetUpdate.DOWNLOADED, sync.refreshDataset())
        now = Instant.fromEpochSeconds(2_000)
        assertEquals(DatasetUpdate.UNCHANGED, sync.refreshDataset())

        assertEquals(listOf(null, "\"v1\""), requests)
        assertEquals(Instant.fromEpochSeconds(2_000), store.dataset()?.checkedAt)
        assertEquals(SampleData.dataset, store.dataset()?.dataset)
    }

    @Test
    fun aFailedRefreshKeepsTheOfflineCopy() = runTest {
        sync.refreshDataset()
        serverUp = false
        assertFailsWith<Exception> { sync.refreshDataset() }
        assertEquals(SampleData.dataset, store.dataset()?.dataset)
        assertEquals(Instant.fromEpochSeconds(1_000), store.dataset()?.checkedAt)
    }

    @Test
    fun aNewDatasetNotesTheStudentsChangedClassesButTheFirstDoesNot() = runTest {
        store.saveProfile(Profile(enrollment = Enrollment.National("1541E", "1541E/A")), dirty = false)
        sync.refreshDataset()
        assertEquals(emptyList(), store.changeNotes())

        served = SampleData.dataset.copy(
            activities = SampleData.dataset.activities.map {
                if (it.id ==
                    "contab-s"
                ) {
                    it.copy(roomId = "bn212a")
                } else {
                    it
                }
            },
        )
        etag = "\"v2\""
        now = Instant.fromEpochSeconds(2_000)
        assertEquals(DatasetUpdate.DOWNLOADED, sync.refreshDataset())

        assertEquals(
            listOf(ChangeNote("contab-s", ChangeKind.ROOM, now, old = "EC004", new = "BN212a")),
            store.changeNotes(),
        )
    }
}
