// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.sync

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.store.StoredProfile
import net.mehstudios.etimetable.store.inMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AccountSyncTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private var serverAnnouncements = listOf<Announcement>()
    private var serverProfile = Profile(firstName = "Ana", lastName = "Pop")
    private val store = inMemoryStore()

    private val engine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/api/v1/announcements" -> {
                val before = request.url.parameters["before"]
                val start = if (before == null) 0 else serverAnnouncements.indexOfFirst { it.id == before } + 1
                respond(AppJson.encodeToString(serverAnnouncements.drop(start).take(20)), headers = json)
            }
            "/api/v1/me" -> {
                if (request.method == HttpMethod.Put) {
                    serverProfile = AppJson.decodeFromString(request.body.toByteArray().decodeToString())
                }
                respond(AppJson.encodeToString(serverProfile), headers = json)
            }
            "/api/v1/dataset" -> respond("""{"code":"no_semester"}""", HttpStatusCode.NotFound, json)
            else -> error("unexpected ${request.url}")
        }
    }
    private val sync = Sync(ApiClient(engine, "http://server.test"), store)

    private fun announcement(n: Int) =
        Announcement("a$n", "Anunț $n", "Text", Instant.fromEpochSeconds(1_000L - n), "Secretariat")

    @Test
    fun announcementsDeletedOnTheServerGo() = runTest {
        serverAnnouncements = (1..25).map(::announcement)
        sync.refreshAnnouncements()
        assertEquals(25, store.announcements().size)
        store.markRead("a2")

        serverAnnouncements = serverAnnouncements.filter { it.id != "a1" }
        sync.refreshAnnouncements()
        val ids = store.announcements().map { it.announcement.id }
        assertEquals(24, ids.size)
        assertEquals("a2", ids.first())
        assertEquals(true, store.announcements().first().read)

        serverAnnouncements = emptyList()
        sync.refreshAnnouncements()
        assertEquals(0, store.announcements().size)
    }

    @Test
    fun anOfflineChangeIsSentThenTheServerWins() = runTest {
        val changed = Profile(firstName = "Ana", lastName = "Pop", enrollment = Enrollment.National("1541E", "1541E/A"))
        store.saveProfile(changed, dirty = true)
        sync.syncProfile("token")
        assertEquals(changed, serverProfile)
        assertEquals(StoredProfile(changed, dirty = false), store.profile())

        serverProfile = serverProfile.copy(lastName = "Popescu")
        sync.syncProfile("token")
        assertEquals("Ana Popescu", store.profile()?.profile?.fullName)
    }

    @Test
    fun noSemesterYet() = runTest {
        assertEquals(DatasetUpdate.NO_SEMESTER, sync.refreshDataset())
    }
}
