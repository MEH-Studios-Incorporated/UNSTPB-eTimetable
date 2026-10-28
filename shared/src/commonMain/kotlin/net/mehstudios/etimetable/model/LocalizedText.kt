// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import kotlinx.serialization.Serializable

/** Text shown to people, in both languages the app supports. */
@Serializable
data class LocalizedText(val ro: String, val en: String) {
    /** [language] is an ISO 639-1 code; anything but "ro" gets English. */
    fun get(language: String): String = if (language == "ro") ro else en
}
