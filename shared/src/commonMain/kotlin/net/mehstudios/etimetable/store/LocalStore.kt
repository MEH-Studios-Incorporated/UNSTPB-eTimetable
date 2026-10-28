// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.store

import app.cash.sqldelight.db.SqlDriver
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Profile
import kotlin.time.Instant

const val DATABASE_NAME = "etimetable.db"

data class CachedDataset(val dataset: Dataset, val etag: String?, val checkedAt: Instant)

data class StoredAnnouncement(val announcement: Announcement, val read: Boolean)

data class StoredProfile(val profile: Profile, val dirty: Boolean)

/** The offline copy the app and the widgets read. */
class LocalStore(driver: SqlDriver) {
    private val queries = EtimetableDatabase(driver).etimetableQueries

    fun dataset(): CachedDataset? = queries.selectDataset().executeAsOneOrNull()?.let {
        CachedDataset(AppJson.decodeFromString(it.json), it.etag, Instant.fromEpochMilliseconds(it.checked_at))
    }

    fun saveDataset(dataset: Dataset, etag: String?, checkedAt: Instant) {
        queries.saveDataset(etag, AppJson.encodeToString(dataset), checkedAt.toEpochMilliseconds())
    }

    fun markDatasetChecked(at: Instant) = queries.markDatasetChecked(at.toEpochMilliseconds())

    /** Adds new announcements and updates known ones, keeping their read state. */
    fun saveAnnouncements(announcements: List<Announcement>) = queries.transaction {
        announcements.forEach {
            val json = AppJson.encodeToString(it)
            val published = it.publishedAt.toEpochMilliseconds()
            val pinned = if (it.pinned) 1L else 0L
            queries.insertAnnouncementIfNew(it.id, published, pinned, json)
            queries.updateAnnouncement(published, pinned, json, it.id)
        }
    }

    /** Pinned first, then newest first. */
    fun announcements(): List<StoredAnnouncement> = queries.selectAnnouncements().executeAsList().map {
        StoredAnnouncement(AppJson.decodeFromString(it.json), it.read != 0L)
    }

    fun markRead(announcementId: String) = queries.markAnnouncementRead(announcementId)

    fun unreadCount(): Long = queries.countUnread().executeAsOne()

    fun profile(): StoredProfile? = queries.selectProfile().executeAsOneOrNull()?.let {
        StoredProfile(AppJson.decodeFromString(it.json), it.dirty != 0L)
    }

    /** [dirty] marks changes made offline that still have to reach the server. */
    fun saveProfile(profile: Profile, dirty: Boolean) {
        queries.saveProfile(AppJson.encodeToString(profile), if (dirty) 1L else 0L)
    }
}
