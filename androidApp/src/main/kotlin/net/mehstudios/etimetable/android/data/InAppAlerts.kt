// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the in-app pop-up shows: one new announcement, or a summary when more arrive while it
 * is still on screen. [ids] is newest last; [title] is the newest announcement's.
 */
data class AnnouncementAlert(val ids: List<String>, val title: String) {
    val single: Boolean get() = ids.size == 1

    /** This alert with [id] added, as when a second announcement arrives over the first. */
    fun plus(id: String, title: String) = AnnouncementAlert(ids + id, title)
}

/**
 * New announcements that arrive while the app is on screen. They show as a pop-up in the app
 * instead of a system notification, so one announcement does not alert twice.
 */
class InAppAlerts {
    private val alert = MutableStateFlow<AnnouncementAlert?>(null)

    /**
     * The pop-up to show. The main screen collects it only while it is visible, so a collector
     * means the student is looking at the app.
     */
    val current: StateFlow<AnnouncementAlert?> = alert.asStateFlow()

    /**
     * Shows announcement [id] in the app, or adds it to the pop-up already showing. False when
     * the app is not on screen and nothing was shown.
     */
    fun offer(id: String, title: String): Boolean {
        if (alert.subscriptionCount.value == 0) return false
        alert.update { showing -> showing?.plus(id, title) ?: AnnouncementAlert(listOf(id), title) }
        return true
    }

    /** Hides [shown], unless a newer announcement has replaced it meanwhile. */
    fun dismiss(shown: AnnouncementAlert) {
        alert.compareAndSet(shown, null)
    }
}
