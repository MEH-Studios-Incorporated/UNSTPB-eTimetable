// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import io.ktor.client.engine.okhttp.OkHttp
import net.mehstudios.etimetable.android.BuildConfig
import net.mehstudios.etimetable.android.widget.WidgetUpdates
import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.store.createLocalStore
import net.mehstudios.etimetable.sync.Sync
import okio.Path.Companion.toOkioPath

/** The objects the whole app shares, created once by the Application. */
class AppContainer(context: Context) {
    val store = createLocalStore(context)
    private val api = ApiClient(OkHttp.create(), BuildConfig.API_BASE_URL)
    val connectivity = Connectivity(context)
    val alerts = InAppAlerts()

    /** Pictures of events: in memory, and up to 100 MB on disk so they show offline. */
    val images: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory()) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .diskCache {
            DiskCache.Builder().directory(context.cacheDir.resolve("event-images").toOkioPath())
                .maxSizeBytes(100L * 1024 * 1024).build()
        }
        .build()
    val repository = AppRepository(
        store,
        api,
        Sync(api, store),
        SessionStore(context),
        pushToken = Push::token,
        notifications = AnnouncementNotifications.posted(context),
        prefetchImages = { events ->
            events.forEach { images.enqueue(ImageRequest.Builder(context).data(imageUrl(it.image)).build()) }
        },
    ) {
        // the home screen widgets show the same timetable
        WidgetUpdates.refresh(context.applicationContext)
    }
}

/** The full address of a picture the server gives as a path (`/api/v1/images/...`). */
fun imageUrl(path: String): String = BuildConfig.API_BASE_URL.trimEnd('/') + path
