// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.push

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Tries after the first one for a 429 or 5xx; more would hold up the rest of the phones. */
private const val RETRIES = 2

/** The wait before a retry when FCM gives no Retry-After, doubled for the second one. */
private val FIRST_BACKOFF = 1.seconds

/** A longer Retry-After is cut to this, so one announcement cannot stall for minutes. */
private val LONGEST_WAIT = 30.seconds

/** FCM's error codes for a token that will never work again. */
private val GONE_CODES = setOf("UNREGISTERED", "SENDER_ID_MISMATCH")

/**
 * Android phones through Firebase Cloud Messaging, HTTP v1 API. Messages carry only data,
 * so the app builds the notification itself and can open the announcement from it.
 * [wait] is how retries pause; tests pass one that does not.
 */
class FcmSender(
    private val key: ServiceAccountKey,
    private val http: HttpPost = JdkHttpPost(),
    private val wait: suspend (Duration) -> Unit = { delay(it) },
    private val tokens: GoogleAccessTokens = GoogleAccessTokens(key, http, wait = wait),
) : PushSender {
    private val log = LoggerFactory.getLogger(FcmSender::class.java)
    private val url = "https://fcm.googleapis.com/v1/projects/${key.projectId}/messages:send"

    val projectId: String get() = key.projectId

    override suspend fun send(token: String, data: Map<String, String>): PushResult {
        var attempt = 0
        while (true) {
            val reply = post(token, data)
            when {
                reply.status == 200 -> return PushResult.SENT
                isGone(reply) -> return PushResult.GONE
                isTemporary(reply.status) && attempt < RETRIES -> {
                    wait(backoff(reply, attempt))
                    attempt++
                }
                else -> {
                    // a 401 means the access token was revoked early; the next send gets a new one
                    if (reply.status == 401) tokens.reset()
                    log.warn("FCM answered {}: {}", reply.status, reply.body.take(300))
                    return PushResult.FAILED
                }
            }
        }
    }

    private suspend fun post(token: String, data: Map<String, String>): HttpReply = http.post(
        url,
        mapOf(
            "Authorization" to "Bearer ${tokens.token()}",
            "Content-Type" to "application/json; charset=UTF-8",
        ),
        messageJson(token, data),
    )

    private fun messageJson(token: String, data: Map<String, String>): String = buildJsonObject {
        putJsonObject("message") {
            put("token", token)
            putJsonObject("data") { data.forEach { (name, value) -> put(name, value) } }
            // high priority wakes a phone in Doze, which an announcement is worth
            putJsonObject("android") { put("priority", JsonPrimitive("high")) }
        }
    }.toString()

    private fun isTemporary(status: Int) = status == 429 || status in 500..599

    /** Retry-After in seconds when FCM sent one (it does not send dates), else 1 s, then 2 s. */
    private fun backoff(reply: HttpReply, attempt: Int): Duration {
        val asked = reply.retryAfter?.trim()?.toLongOrNull()?.seconds
        return (asked ?: (FIRST_BACKOFF * (1 shl attempt))).coerceIn(Duration.ZERO, LONGEST_WAIT)
    }

    /**
     * Gone when FCM's error details say UNREGISTERED (the app was removed) or
     * SENDER_ID_MISMATCH (the token belongs to another Firebase project), or when an
     * INVALID_ARGUMENT names the token, which was then never valid. Other errors keep it.
     */
    private fun isGone(reply: HttpReply): Boolean {
        val error = errorOf(reply.body) ?: return false
        val codes = (error["details"] as? JsonArray).orEmpty().mapNotNull { detail ->
            (detail as? JsonObject)?.get("errorCode")?.jsonPrimitive?.contentOrNull
        }
        if (codes.any { it in GONE_CODES }) return true
        val message = (error["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return reply.status == 400 && "INVALID_ARGUMENT" in codes && "registration token" in message
    }

    private fun errorOf(body: String): JsonObject? =
        runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()?.get("error") as? JsonObject
}
