// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.mehstudios.etimetable.android.MainActivity
import net.mehstudios.etimetable.android.R

/** Removes announcement notifications once the app has shown what they said. */
interface PostedNotifications {
    fun cancel(id: String)

    fun cancelAll()

    /** For a repository without a phone around it, as in tests. */
    object None : PostedNotifications {
        override fun cancel(id: String) = Unit

        override fun cancelAll() = Unit
    }
}

/** The notification for a new announcement; tapping it opens the announcement. */
object AnnouncementNotifications {
    private const val CHANNEL = "announcements"

    /** The extra on MainActivity's intent naming the announcement to open. */
    const val EXTRA_ID = "announcementId"

    fun show(context: Context, id: String, title: String, text: String) {
        // below Android 13 the permission does not exist and notifications are allowed
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        createChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // one PendingIntent per announcement, or a newer one would replace the extra of an older
        val tap = PendingIntent.getActivity(
            context,
            id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }

    /** The [PostedNotifications] of [context]'s app; it posts no other notifications. */
    fun posted(context: Context): PostedNotifications {
        val manager = NotificationManagerCompat.from(context.applicationContext)
        return object : PostedNotifications {
            override fun cancel(id: String) = manager.cancel(id.hashCode())

            override fun cancelAll() = manager.cancelAll()
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.push_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.push_channel_hint) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
