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
    private val requests = mutableListOf<String?>()

    private val engine = MockEngine { request ->
        requests += request.headers[HttpHeaders.IfNoneMatch]
        when {
            !serverUp -> respond("", HttpStatusCode.BadGateway)
            request.headers[HttpHeaders.IfNoneMatch] == "\"v1\"" -> respond("", HttpStatusCode.NotModified)
            else -> respond(
                AppJson.encodeToString(SampleData.dataset),
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ETag to listOf("\"v1\""),
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
}
