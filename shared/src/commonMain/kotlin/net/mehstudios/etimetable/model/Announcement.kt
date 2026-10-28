// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Announcement(
    val id: String,
    val title: String,
    /** Markdown. */
    val body: String,
    val publishedAt: Instant,
    val author: String,
    val pinned: Boolean = false,
)
