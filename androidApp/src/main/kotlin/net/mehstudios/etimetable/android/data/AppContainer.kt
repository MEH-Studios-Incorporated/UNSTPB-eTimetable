// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.content.Context
import io.ktor.client.engine.okhttp.OkHttp
import net.mehstudios.etimetable.android.BuildConfig
import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.store.createLocalStore
import net.mehstudios.etimetable.sync.Sync

/** The objects the whole app shares, created once by the Application. */
class AppContainer(context: Context) {
    private val store = createLocalStore(context)
    private val api = ApiClient(OkHttp.create(), BuildConfig.API_BASE_URL)
    val connectivity = Connectivity(context)
    val repository = AppRepository(store, api, Sync(api, store), SessionStore(context))
}
