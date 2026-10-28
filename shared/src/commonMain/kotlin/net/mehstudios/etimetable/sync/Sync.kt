// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.sync

import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.api.DatasetFetch
import net.mehstudios.etimetable.store.LocalStore
import kotlin.time.Clock

enum class DatasetUpdate { DOWNLOADED, UNCHANGED }

/** Brings the offline copy up to date. Failures are thrown; the offline copy stays as it was. */
class Sync(private val api: ApiClient, private val store: LocalStore, private val clock: Clock = Clock.System) {
    suspend fun refreshDataset(): DatasetUpdate {
        val cached = store.dataset()
        return when (val fetch = api.dataset(cached?.etag)) {
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

    suspend fun refreshAnnouncements() {
        store.saveAnnouncements(api.announcements())
    }
}
