// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.api.PushFields
import net.mehstudios.etimetable.api.PushPlatform
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.server.push.PushDevice
import net.mehstudios.etimetable.server.push.PushNotifier
import net.mehstudios.etimetable.server.push.PushResult
import net.mehstudios.etimetable.server.push.PushSender
import net.mehstudios.etimetable.server.push.ServiceAccountRefused
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushNotifierTest {
    /** Answers GONE for the tokens in [gone], SENT for the rest, and remembers every call. */
    private class FakeGateway(private val gone: Set<String> = emptySet()) : PushSender {
        val sent: MutableList<Pair<String, Map<String, String>>> =
            Collections.synchronizedList(mutableListOf())

        override suspend fun send(token: String, data: Map<String, String>): PushResult {
            sent += token to data
            return if (token in gone) PushResult.GONE else PushResult.SENT
        }
    }

    @Test
    fun sendsEachAnnouncementToEveryPhoneAndForgetsTheGoneOnes() = runBlocking {
        val gateway = FakeGateway(gone = setOf("uninstalled"))
        val senders = mapOf(PushPlatform.ANDROID to gateway)
        val services = Services(TestPostgres.freshDatabase(), senders)
        val session = services.accounts.signup(
            SignupRequest("ana@stud.upb.ro", "parola-buna", "Ana", "Pop", "0712345678"),
        ).token
        val student = services.accounts.studentFor(session)!!
        services.pushTokens.register(student, session, "phone-1", PushPlatform.ANDROID)
        services.pushTokens.register(student, session, "uninstalled", PushPlatform.ANDROID)
        // no APNs credentials in this deployment, so the iPhone is skipped
        services.pushTokens.register(student, session, "iphone", PushPlatform.IOS)

        val announcement = services.announcements.publish("Sesiune", "Orarul **nou**.", "Decanat")
        services.push.announce(announcement).join()

        assertEquals(setOf("phone-1", "uninstalled"), gateway.sent.map { it.first }.toSet())
        val data = gateway.sent.first().second
        assertEquals(PushFields.KIND_ANNOUNCEMENT, data[PushFields.KIND])
        assertEquals(announcement.id, data[PushFields.ID])
        assertEquals("Sesiune", data[PushFields.TITLE])
        assertEquals("Orarul nou.", data[PushFields.TEXT])
        assertEquals(
            setOf(
                PushDevice("phone-1", PushPlatform.ANDROID),
                PushDevice("iphone", PushPlatform.IOS),
            ),
            services.pushTokens.all().toSet(),
        )
    }

    @Test
    fun aRefusedServiceAccountStopsTheRunAndKeepsTheTokens() = runBlocking {
        val attempts = AtomicInteger()
        val refusing = PushSender { _, _ ->
            attempts.incrementAndGet()
            throw ServiceAccountRefused(400, "invalid_grant")
        }
        val services = Services(TestPostgres.freshDatabase(), mapOf(PushPlatform.ANDROID to refusing))
        val session = services.accounts.signup(
            SignupRequest("ana@stud.upb.ro", "parola-buna", "Ana", "Pop", "0712345678"),
        ).token
        val student = services.accounts.studentFor(session)!!
        (1..10).forEach { services.pushTokens.register(student, session, "phone-$it", PushPlatform.ANDROID) }

        val announcement = services.announcements.publish("Sesiune", "Text", "Decanat")
        services.push.announce(announcement).join()

        // at most the sends already in flight; not one per phone
        assertTrue(attempts.get() < 10, "tried ${attempts.get()} phones")
        assertEquals(10, services.pushTokens.all().size)
    }

    @Test
    fun withoutCredentialsPushIsOff() = runBlocking {
        val services = Services(TestPostgres.freshDatabase())
        val announcement = services.announcements.publish("Sesiune", "Text", "Decanat")

        services.push.announce(announcement).join()

        assertEquals(false, services.push.enabled)
    }

    @Test
    fun thePreviewIsPlainTextCutAtAWord() {
        val markdown = "# Examen\n\n- **Sala** [BN212b](https://faima.upb.ro)\n- ora _10_\n"
        assertEquals("Examen Sala BN212b ora 10", PushNotifier.preview(markdown))

        val long = PushNotifier.preview("cuvânt ".repeat(60))
        assertTrue(long.length <= 181, "too long: ${long.length}")
        assertTrue(long.endsWith("cuvânt…"), long)
    }
}
