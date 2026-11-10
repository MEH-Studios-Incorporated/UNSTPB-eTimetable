// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.store

import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.schedule.ChangeKind
import net.mehstudios.etimetable.schedule.ChangeNote
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
    fun editedTextBecomesUnreadButPinChangesKeepItRead() {
        store.saveAnnouncements(listOf(announcement("a", 1), announcement("b", 2)))
        store.markRead("a")
        assertEquals(1, store.unreadCount())

        store.saveAnnouncements(listOf(announcement("a", 1, title = "edited")))
        val a = store.announcements().first { it.announcement.id == "a" }
        assertEquals("edited", a.announcement.title)
        assertEquals(false, a.read)
        assertEquals(2, store.unreadCount())
        store.markAllRead()
        store.saveAnnouncements(listOf(announcement("a", 1, title = "edited", pinned = true)))
        assertEquals(true, store.announcements().first { it.announcement.id == "a" }.read)
        assertEquals(0, store.unreadCount())
    }

    @Test
    fun profileWithDirtyFlag() {
        val profile =
            Profile(firstName = "Test", lastName = "Student", enrollment = Enrollment.Erasmus(setOf("micro-c")))
        store.saveProfile(profile, dirty = true)
        assertEquals(StoredProfile(profile, dirty = true), store.profile())
    }

    @Test
    fun changeNotesAreKeptPrunedAndForgottenAtSignOut() {
        val note = ChangeNote("contab-s", ChangeKind.ROOM, Instant.fromEpochSeconds(100), old = "EC004", new = "BN212a")
        val removed = ChangeNote(
            "mkt-p-1541",
            ChangeKind.REMOVED,
            Instant.fromEpochSeconds(300),
            removed = SampleData.activity("mkt-p-1541"),
        )
        store.saveDataset(SampleData.dataset, "\"v1\"", Instant.fromEpochSeconds(100), listOf(note, removed))
        assertEquals(listOf(note, removed), store.changeNotes())

        store.pruneChangeNotes(Instant.fromEpochSeconds(200))
        assertEquals(listOf(removed), store.changeNotes())

        store.clearAccount()
        assertEquals(emptyList(), store.changeNotes())
        assertEquals(SampleData.dataset, store.dataset()?.dataset)
    }
}
