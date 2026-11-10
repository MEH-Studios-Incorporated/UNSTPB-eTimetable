// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which push gateway reaches a phone: Firebase for Android, Apple's service for iOS. */
@Serializable
enum class PushPlatform {
    @SerialName("android")
    ANDROID,

    @SerialName("ios")
    IOS,
}

/** Body of `PUT /api/v1/me/push-tokens/<token>`. */
@Serializable
data class PushTokenRequest(val platform: PushPlatform)

/** The data fields of an announcement's push message; every value is a string. */
object PushFields {
    const val KIND = "kind"
    const val KIND_ANNOUNCEMENT = "announcement"
    const val ID = "id"
    const val TITLE = "title"
    const val TEXT = "text"
}

/** Longest push token the server accepts; FCM tokens are about 160 characters. */
const val MAX_PUSH_TOKEN = 1024
