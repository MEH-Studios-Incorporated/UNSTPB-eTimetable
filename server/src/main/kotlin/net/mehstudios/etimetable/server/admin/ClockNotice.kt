// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import kotlin.time.Duration

private const val MINUTES_PER_HOUR = 60L

/**
 * What the dashboard says after publishing when this server's clock is too far off for push:
 * the announcement is saved, but no phone was notified. [drift] is positive when ahead.
 */
internal fun clockNotice(drift: Duration): String {
    val direction = if (drift.isPositive()) "înainte" else "în urmă"
    return "Anunțul a fost publicat și apare în aplicație la următoarea sincronizare, dar " +
        "notificările nu au plecat: ceasul serverului este cu ${amount(drift)} $direction. " +
        "Pornește sincronizarea orei pe calculatorul serverului (sudo timedatectl " +
        "set-ntp true), apoi notificările merg din nou."
}

/** The drift to the minute: "2 h", "2 h 5 min", "40 min". */
private fun amount(drift: Duration): String {
    val minutes = drift.absoluteValue.inWholeMinutes
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0L -> "$rest min"
        rest == 0L -> "$hours h"
        else -> "$hours h $rest min"
    }
}
