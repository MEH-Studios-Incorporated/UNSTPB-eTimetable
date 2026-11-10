// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.store

import app.cash.sqldelight.db.SqlDriver
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Event
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.schedule.ChangeNote
import kotlin.time.Instant

const val DATABASE_NAME = "etimetable.db"

data class CachedDataset(val dataset: Dataset, val etag: String?, val checkedAt: Instant)

data class StoredAnnouncement(val announcement: Announcement, val read: Boolean)

data class StoredProfile(val profile: Profile, val dirty: Boolean)

data class CachedEvents(val events: List<Event>, val etag: String?)

/** The offline copy the app and the widgets read. */
class LocalStore(driver: SqlDriver) {
    private val queries = EtimetableDatabase(driver).etimetableQueries

    fun dataset(): CachedDataset? = queries.selectDataset().executeAsOneOrNull()?.let {
        CachedDataset(AppJson.decodeFromString(it.json), it.etag, Instant.fromEpochMilliseconds(it.checked_at))
    }

    /** Saves a new dataset; with [changeNotes], they replace the stored notes in the same transaction. */
    fun saveDataset(dataset: Dataset, etag: String?, checkedAt: Instant, changeNotes: List<ChangeNote>? = null) =
        queries.transaction {
            queries.saveDataset(etag, AppJson.encodeToString(dataset), checkedAt.toEpochMilliseconds())
            if (changeNotes != null) {
                queries.deleteAllChangeNotes()
                changeNotes.forEach {
                    queries.insertChangeNote(
                        it.activityId,
                        it.kind.name,
                        it.seenAt.toEpochMilliseconds(),
                        AppJson.encodeToString(it),
                    )
                }
            }
        }

    /** Changes to the student's classes, oldest first. */
    fun changeNotes(): List<ChangeNote> = queries.selectChangeNotes().executeAsList().map {
        AppJson.decodeFromString(it)
    }

    /** Drops the notes seen before [before]. */
    fun pruneChangeNotes(before: Instant) = queries.deleteChangeNotesBefore(before.toEpochMilliseconds())

    fun markDatasetChecked(at: Instant) = queries.markDatasetChecked(at.toEpochMilliseconds())

    /** Events, earliest first, as last downloaded; they stay after sign-out, every student sees them. */
    fun events(): CachedEvents? = queries.selectEvents().executeAsOneOrNull()?.let {
        CachedEvents(AppJson.decodeFromString(it.json), it.etag)
    }

    fun saveEvents(events: List<Event>, etag: String?) {
        queries.saveEvents(etag, AppJson.encodeToString(events))
    }

    /**
     * Adds new announcements and updates known ones. A known one keeps its read
     * state unless its title or text changed, which makes it unread again.
     */
    fun saveAnnouncements(announcements: List<Announcement>) = queries.transaction {
        val existing = announcements().associate { it.announcement.id to it.announcement }
        announcements.forEach {
            val json = AppJson.encodeToString(it)
            val published = it.publishedAt.toEpochMilliseconds()
            val pinned = if (it.pinned) 1L else 0L
            queries.insertAnnouncementIfNew(it.id, published, pinned, json)
            queries.updateAnnouncement(published, pinned, json, it.id)
            val old = existing[it.id]
            if (old != null && (old.title != it.title || old.body != it.body)) queries.markAnnouncementUnread(it.id)
        }
    }

    /**
     * Makes the offline list equal to [announcements], the server's full list:
     * new ones are added, known ones are updated as in [saveAnnouncements],
     * the rest go.
     */
    fun replaceAnnouncements(announcements: List<Announcement>) = queries.transaction {
        if (announcements.isEmpty()) {
            queries.deleteAllAnnouncements()
        } else {
            queries.deleteAnnouncementsExcept(announcements.map { it.id })
        }
        saveAnnouncements(announcements)
    }

    /** Pinned first, then newest first. */
    fun announcements(): List<StoredAnnouncement> = queries.selectAnnouncements().executeAsList().map {
        StoredAnnouncement(AppJson.decodeFromString(it.json), it.read != 0L)
    }

    fun markRead(announcementId: String) = queries.markAnnouncementRead(announcementId)

    fun markAllRead() = queries.markAllAnnouncementsRead()

    fun unreadCount(): Long = queries.countUnread().executeAsOne()

    fun profile(): StoredProfile? = queries.selectProfile().executeAsOneOrNull()?.let {
        StoredProfile(AppJson.decodeFromString(it.json), it.dirty != 0L)
    }

    /** Forgets everything tied to the signed-in student; the timetable stays. */
    fun clearAccount() = queries.transaction {
        queries.deleteProfile()
        queries.deleteAllAnnouncements()
        queries.deleteAllChangeNotes()
    }

    /** [dirty] marks changes made offline that still have to reach the server. */
    fun saveProfile(profile: Profile, dirty: Boolean) {
        queries.saveProfile(AppJson.encodeToString(profile), if (dirty) 1L else 0L)
    }
}
