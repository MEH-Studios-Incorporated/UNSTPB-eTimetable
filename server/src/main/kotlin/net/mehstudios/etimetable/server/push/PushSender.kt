// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.push

/** What a push gateway made of one message. */
enum class PushResult {
    SENT,

    /** The gateway no longer knows the token, so the app was uninstalled or signed out. */
    GONE,

    /** Anything else; the token stays and the next announcement tries again. */
    FAILED,
}

/** Sends a data message to one phone through a push gateway. */
fun interface PushSender {
    /** [data] reaches the app as is; Firebase and Apple both limit a message to 4 KB. */
    suspend fun send(token: String, data: Map<String, String>): PushResult
}
