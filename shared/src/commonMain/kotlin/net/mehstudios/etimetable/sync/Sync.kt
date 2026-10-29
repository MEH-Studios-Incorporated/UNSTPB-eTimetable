// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.sync

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.api.DatasetFetch
import net.mehstudios.etimetable.model.Announcement
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
                store.markDatasetChecked(clock.now())
                DatasetUpdate.UNCHANGED
            }
            is DatasetFetch.Fresh -> {
                store.saveDataset(fetch.dataset, fetch.etag, clock.now())
                DatasetUpdate.DOWNLOADED
            }
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
        store.replaceAnnouncements(all.take(ANNOUNCEMENT_LIMIT))
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
