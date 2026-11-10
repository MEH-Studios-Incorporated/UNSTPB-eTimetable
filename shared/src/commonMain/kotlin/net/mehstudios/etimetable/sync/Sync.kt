// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.sync

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.api.DatasetFetch
import net.mehstudios.etimetable.api.EventsFetch
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.schedule.CHANGE_NOTE_LIFETIME
import net.mehstudios.etimetable.schedule.mergeChangeNotes
import net.mehstudios.etimetable.schedule.timetableChanges
import net.mehstudios.etimetable.store.LocalStore
import kotlin.time.Clock

enum class DatasetUpdate { DOWNLOADED, UNCHANGED, NO_SEMESTER }

/** Most announcements the app keeps; a semester has a few dozen. */
const val ANNOUNCEMENT_LIMIT = 100

/** Brings the offline copy up to date. Failures are thrown; the offline copy stays as it was. */
class Sync(private val api: ApiClient, private val store: LocalStore, private val clock: Clock = Clock.System) {
    suspend fun refreshDataset(): DatasetUpdate {
        val cached = store.dataset()
        val fetch = try {
            api.dataset(cached?.etag)
        } catch (e: ClientRequestException) {
            // before the faculty publishes the first semester
            if (e.response.status == HttpStatusCode.NotFound) return DatasetUpdate.NO_SEMESTER
            throw e
        }
        return when (fetch) {
            DatasetFetch.NotModified -> {
                val now = clock.now()
                store.markDatasetChecked(now)
                store.pruneChangeNotes(now - CHANGE_NOTE_LIFETIME)
                DatasetUpdate.UNCHANGED
            }
            is DatasetFetch.Fresh -> {
                // the student's classes before and after, compared with the profile on the phone
                val now = clock.now()
                val changes = timetableChanges(cached?.dataset, fetch.dataset, store.profile()?.profile, now)
                val sameSemester = cached?.dataset?.semester?.id == fetch.dataset.semester.id
                val notes = if (sameSemester) mergeChangeNotes(store.changeNotes(), changes, now) else emptyList()
                store.saveDataset(fetch.dataset, fetch.etag, now, notes)
                DatasetUpdate.DOWNLOADED
            }
        }
    }

    /** Downloads the events when they changed; true when the list is new, so its pictures can be fetched. */
    suspend fun refreshEvents(): Boolean = when (val fetch = api.events(store.events()?.etag)) {
        EventsFetch.NotModified -> false
        is EventsFetch.Fresh -> {
            store.saveEvents(fetch.events, fetch.etag)
            true
        }
    }

    /** Downloads the newest announcements and drops the ones deleted on the server. */
    suspend fun refreshAnnouncements() {
        val all = mutableListOf<Announcement>()
        var before: String? = null
        while (all.size < ANNOUNCEMENT_LIMIT) {
            val page = api.announcements(before)
            if (page.isEmpty()) break
            all += page
            before = page.last().id
        }
        val pinned = mutableListOf<Announcement>()
        before = null
        while (pinned.size < ANNOUNCEMENT_LIMIT) {
            val page = api.announcements(before, pinnedOnly = true)
            if (page.isEmpty()) break
            pinned += page
            before = page.last().id
        }
        store.replaceAnnouncements(
            (all.take(ANNOUNCEMENT_LIMIT) + pinned.take(ANNOUNCEMENT_LIMIT)).distinctBy {
                it.id
            },
        )
    }

    /**
     * Sends a profile changed offline, otherwise takes the server's copy.
     * A change the server refuses (a clash with a changed timetable) stays
     * local and marked, and the error is thrown for the app to show.
     */
    suspend fun syncProfile(token: String) {
        val local = store.profile()
        if (local != null && local.dirty) {
            val saved = api.saveProfile(token, local.profile)
            store.saveProfile(saved, dirty = false)
        } else {
            store.saveProfile(api.profile(token), dirty = false)
        }
    }
}
