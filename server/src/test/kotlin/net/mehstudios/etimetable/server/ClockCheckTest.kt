// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.server.admin.clockNotice
import net.mehstudios.etimetable.server.push.ClockCheck
import net.mehstudios.etimetable.server.push.RemoteClock
import net.mehstudios.etimetable.server.push.parseHttpDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ClockCheckTest {
    private val googleNow = Instant.parse("2026-11-09T10:15:35Z")

    /** This server's clock; tests move it on to get past the recheck time. */
    private class ServerClock(var now: Instant) : Clock {
        override fun now() = now
    }

    /** Answers with Google's time, or null to act unreachable, and counts the questions. */
    private class FakeGoogle(private val time: Instant?) : RemoteClock {
        var asked = 0

        override suspend fun now(): Instant? {
            asked++
            return time
        }
    }

    private fun checkWith(ahead: Duration, google: FakeGoogle = FakeGoogle(googleNow)) =
        ClockCheck(google, ServerClock(googleNow + ahead))

    @Test
    fun reportsAClockTwoHoursAhead() = runBlocking {
        assertEquals(2.hours, checkWith(2.hours).drift())
    }

    @Test
    fun reportsAClockBehind() = runBlocking {
        assertEquals(-10.minutes, checkWith(-10.minutes).drift())
    }

    @Test
    fun acceptsAClockAFewSecondsOff() = runBlocking {
        assertNull(checkWith(30.seconds).drift())
    }

    @Test
    fun saysNothingWhenGoogleCannotBeReached() = runBlocking {
        assertNull(checkWith(2.hours, FakeGoogle(time = null)).drift())
    }

    @Test
    fun asksGoogleOnceAMinute() = runBlocking {
        val google = FakeGoogle(googleNow)
        val server = ServerClock(googleNow + 2.hours)
        val check = ClockCheck(google, server)

        check.drift()
        check.drift()
        assertEquals(1, google.asked)

        server.now += 2.minutes
        check.drift()
        assertEquals(2, google.asked)
    }

    @Test
    fun readsGooglesDateHeader() {
        assertEquals(googleNow, parseHttpDate("Mon, 09 Nov 2026 10:15:35 GMT"))
    }

    @Test
    fun theNoticeSaysHowFarAndWhichWay() {
        val ahead = clockNotice(2.hours)
        assertTrue("este cu 2 h înainte" in ahead, ahead)
        assertTrue("timedatectl set-ntp true" in ahead, ahead)
        assertTrue("2 h 5 min în urmă" in clockNotice(-(2.hours + 5.minutes)))
        assertTrue("40 min înainte" in clockNotice(40.minutes))
    }
}
