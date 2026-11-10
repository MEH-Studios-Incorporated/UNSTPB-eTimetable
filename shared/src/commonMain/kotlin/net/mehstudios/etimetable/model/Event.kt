// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Something the faculty organises: a card with a picture in the app, every student sees it. */
@Serializable
data class Event(
    val id: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant? = null,
    val place: String? = null,
    /** Markdown, empty when the card says it all. */
    val body: String = "",
    val link: String? = null,
    /** Path of the picture on the server, `/api/v1/images/<sha-256>.jpg`; a new picture is a new path. */
    val image: String,
    /** Width divided by height, so a card keeps its size while the picture loads. */
    val imageRatio: Float,
    val publishedAt: Instant,
) {
    /** When it is over: the end, or the start for an event without one. */
    val over: Instant get() = endsAt ?: startsAt
}
