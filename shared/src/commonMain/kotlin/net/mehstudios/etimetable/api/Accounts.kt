// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

/** A session token, sent back as `Authorization: Bearer <token>`. */
@Serializable
data class SessionResponse(val token: String)

/** Body of every 4xx and 5xx answer. [code] is stable; [message] is for logs, not for people. */
@Serializable
data class ApiError(val code: String, val message: String = "", val details: List<String> = emptyList())

/** The [ApiError] in an error response's body, or null when it is not one. */
fun decodeApiError(body: String): ApiError? =
    runCatching { AppJson.decodeFromString(ApiError.serializer(), body) }.getOrNull()

/** Values of [ApiError.code]. */
object ErrorCodes {
    const val BAD_REQUEST = "bad_request"
    const val NO_SEMESTER = "no_semester"
    const val EMAIL_TAKEN = "email_taken"
    const val INVALID_EMAIL = "invalid_email"
    const val WEAK_PASSWORD = "weak_password"
    const val MISSING_NAME = "missing_name"
    const val INVALID_PHONE = "invalid_phone"
    const val WRONG_CREDENTIALS = "wrong_credentials"
    const val UNAUTHORIZED = "unauthorized"
    const val UNKNOWN_ACTIVITY = "unknown_activity"
    const val UNKNOWN_GROUP = "unknown_group"

    /** [ApiError.details] holds the ids of the two clashing activities. */
    const val CLASH = "clash"
    const val TOO_MANY_REQUESTS = "too_many_requests"
    const val INTERNAL = "internal"
}

const val MIN_PASSWORD_LENGTH = 8

/**
 * A phone number as people type it: digits with spaces, dashes, dots or
 * parentheses, optionally starting with +, 9 to 15 digits in all.
 */
fun isValidPhone(phone: String): Boolean {
    val text = phone.trim()
    if (!Regex("^\\+?[0-9 ().-]+\$").matches(text)) return false
    return text.count { it.isDigit() } in 9..15
}
