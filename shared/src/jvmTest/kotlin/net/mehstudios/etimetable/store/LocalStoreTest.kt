// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.store

import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LocalStoreTest {
    private val store = inMemoryStore()

    private fun announcement(id: String, at: Long, pinned: Boolean = false, title: String = id) =
        Announcement(id, title, "body", Instant.fromEpochSeconds(at), "Secretariat", pinned)

    @Test
    fun keepsTheDatasetWithItsETag() {
        assertNull(store.dataset())
        store.saveDataset(SampleData.dataset, "\"v1\"", Instant.fromEpochSeconds(100))
        store.markDatasetChecked(Instant.fromEpochSeconds(200))

        val cached = store.dataset()
        assertEquals(SampleData.dataset, cached?.dataset)
        assertEquals("\"v1\"", cached?.etag)
        assertEquals(Instant.fromEpochSeconds(200), cached?.checkedAt)
    }

    @Test
    fun announcementsArePinnedFirstThenNewest() {
        store.saveAnnouncements(
            listOf(announcement("old", 1), announcement("new", 3), announcement("pin", 2, pinned = true)),
        )
        assertEquals(listOf("pin", "new", "old"), store.announcements().map { it.announcement.id })
    }

    @Test
    fun updatingAnAnnouncementKeepsItRead() {
        store.saveAnnouncements(listOf(announcement("a", 1), announcement("b", 2)))
        store.markRead("a")
        assertEquals(1, store.unreadCount())

        store.saveAnnouncements(listOf(announcement("a", 1, title = "edited")))
        val a = store.announcements().first { it.announcement.id == "a" }
        assertEquals("edited", a.announcement.title)
        assertEquals(true, a.read)
        assertEquals(1, store.unreadCount())
    }

    @Test
    fun profileWithDirtyFlag() {
        val profile = Profile(displayName = "Test", enrollment = Enrollment.Erasmus(setOf("micro-c")))
        store.saveProfile(profile, dirty = true)
        assertEquals(StoredProfile(profile, dirty = true), store.profile())
    }
}
