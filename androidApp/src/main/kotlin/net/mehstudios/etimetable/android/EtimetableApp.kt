// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android

import android.app.Application
import net.mehstudios.etimetable.android.data.AppContainer
import net.mehstudios.etimetable.android.data.SyncWorker

class EtimetableApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedule(this)
    }
}
