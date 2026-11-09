// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import net.mehstudios.etimetable.android.BuildConfig
import kotlin.coroutines.resume

/**
 * Firebase Cloud Messaging, set up from the build's Firebase settings instead of a
 * google-services.json, so a build without them still works and simply gets no push.
 */
object Push {
    private const val TAG = "eTimetable"
    private const val PREFS = "push"
    private const val ASKED = "asked_permission"

    val configured: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty() &&
            BuildConfig.FIREBASE_API_KEY.isNotEmpty() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotEmpty()

    /** Called once from the Application, before anything asks for a token. */
    fun start(context: Context) {
        if (!configured || FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            // the app id is "1:<project number>:android:<hash>", and the number is the sender
            .setGcmSenderId(BuildConfig.FIREBASE_APP_ID.split(':').getOrElse(1) { "" })
            .build()
        FirebaseApp.initializeApp(context, options)
    }

    /** This phone's FCM token, or null when push is off or Google Play services is missing. */
    suspend fun token(): String? {
        if (!configured) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) Log.w(TAG, "No FCM token", task.exception)
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    /**
     * Whether to show the step that asks for the notification permission: only once, only
     * where Android asks at all (13 and newer), and only when push can reach the phone.
     */
    fun shouldAsk(context: Context): Boolean {
        if (!configured || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return !granted && !prefs(context).getBoolean(ASKED, false)
    }

    fun markAsked(context: Context) {
        prefs(context).edit { putBoolean(ASKED, true) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
