// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.content.Context
import androidx.core.content.edit

/** How the timetable draws a day and a week. */
enum class TimetableLayout {
    /** Classes on an hour scale, the week as columns of hours. */
    TIMELINE,

    /** Classes as cards with the breaks between them, the week as an agenda by day. */
    LIST,

    /** Dense rows for a day, the week as a table of slots by days, like the faculty's sheet. */
    TABLE,
}

enum class StartView { DAY, WEEK }

data class TimetablePrefs(
    val layout: TimetableLayout = TimetableLayout.TIMELINE,
    val start: StartView = StartView.DAY,
) {
    companion object {
        private const val PREFS = "appearance"

        fun read(context: Context): TimetablePrefs {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return TimetablePrefs(
                TimetableLayout.entries.firstOrNull { it.name == prefs.getString("layout", null) }
                    ?: TimetableLayout.TIMELINE,
                StartView.entries.firstOrNull { it.name == prefs.getString("start", null) } ?: StartView.DAY,
            )
        }

        fun save(context: Context, value: TimetablePrefs) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString("layout", value.layout.name)
                putString("start", value.start.name)
            }
        }
    }
}
