// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.android.EtimetableApp
import net.mehstudios.etimetable.api.PushFields

/** Receives the server's messages from Firebase, and the phone's new tokens. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data[PushFields.KIND] != PushFields.KIND_ANNOUNCEMENT) return
        // a phone signed out while offline stays registered until its next sync ends the session
        if (!runBlocking { repository().signedIn() }) return
        val id = data[PushFields.ID] ?: return
        val title = data[PushFields.TITLE].orEmpty()
        // with the app on screen its own pop-up is enough; a system notification would repeat it
        if (!container().alerts.offer(id, title)) {
            AnnouncementNotifications.show(this, id, title, data[PushFields.TEXT].orEmpty())
        }
        // the announcement is then on the phone when the notification is tapped, even offline
        SyncWorker.syncNow(this)
    }

    /** Firebase calls both on a background thread, so blocking on the repository is fine. */
    override fun onNewToken(token: String) {
        runBlocking { repository().registerPush(token) }
    }

    private fun container() = (application as EtimetableApp).container

    private fun repository() = container().repository
}
