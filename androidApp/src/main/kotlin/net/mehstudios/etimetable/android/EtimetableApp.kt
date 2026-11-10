// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import net.mehstudios.etimetable.android.data.AppContainer
import net.mehstudios.etimetable.android.data.Push
import net.mehstudios.etimetable.android.data.SyncWorker

class EtimetableApp :
    Application(),
    SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Push.start(this)
        container = AppContainer(this)
        SyncWorker.schedule(this)
    }

    // every AsyncImage uses the container's loader, with its disk cache
    override fun newImageLoader(context: PlatformContext): ImageLoader = container.images
}
