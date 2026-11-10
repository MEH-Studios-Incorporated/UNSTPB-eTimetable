// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.push

import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaDuration

/**
 * Google refuses the server's sign-in when the two clocks are a few minutes apart ("Invalid
 * JWT: Token must be a short-lived token"), and then no announcement reaches a phone.
 */
private val MAX_DRIFT = 5.minutes

/** One answer is kept this long, so publishing several announcements asks Google once. */
private val RECHECK_AFTER = 1.minutes

/** Publishing waits for Google's answer, so it gives up quickly. */
private val ASK_TIMEOUT = 3.seconds

/** Google's current time, or null when Google could not be reached. */
fun interface RemoteClock {
    suspend fun now(): Instant?
}

/**
 * Compares this server's clock with Google's before announcements are sent, so a wrong clock
 * shows up on the dashboard instead of failing every message.
 */
class ClockCheck(private val google: RemoteClock, private val clock: Clock = Clock.System) {
    private val log = LoggerFactory.getLogger(ClockCheck::class.java)
    private val lock = Mutex()
    private var checked: Pair<Instant, Duration?>? = null

    /**
     * How far this server's clock is ahead of Google's (negative when behind), or null when
     * they agree well enough for push or Google could not be asked.
     */
    suspend fun drift(): Duration? = lock.withLock {
        val now = clock.now()
        checked?.let { (at, drift) -> if (now - at < RECHECK_AFTER) return drift }
        val drift = measure(now)
        checked = now to drift
        drift
    }

    private suspend fun measure(now: Instant): Duration? {
        val remote = google.now() ?: return null
        val drift = now - remote
        if (drift.absoluteValue <= MAX_DRIFT) return null
        log.warn("This server's clock is {} off Google's; push fails until it is fixed", drift)
        return drift
    }
}

/** Google's time from the Date header of a HEAD request to [url], through the JDK's client. */
class HttpDateClock(
    private val url: String,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(ASK_TIMEOUT.toJavaDuration())
        .build(),
) : RemoteClock {
    private val log = LoggerFactory.getLogger(HttpDateClock::class.java)

    override suspend fun now(): Instant? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(ASK_TIMEOUT.toJavaDuration())
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build()
        return try {
            val response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).await()
            response.headers().firstValue("Date").map(::parseHttpDate).orElse(null)
        } catch (e: IOException) {
            log.info("Could not read Google's time: {}", e.toString())
            null
        } catch (e: DateTimeParseException) {
            log.info("Google's Date header was not a date: {}", e.parsedString)
            null
        }
    }
}

/** An HTTP date, "Mon, 09 Nov 2026 10:15:35 GMT". */
fun parseHttpDate(value: String): Instant {
    val parsed = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
    return Instant.fromEpochSeconds(parsed.toEpochSecond())
}
