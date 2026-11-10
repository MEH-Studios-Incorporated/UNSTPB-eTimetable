// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mehstudios.etimetable.server.push.FcmSender
import net.mehstudios.etimetable.server.push.HttpPost
import net.mehstudios.etimetable.server.push.HttpReply
import net.mehstudios.etimetable.server.push.PushResult
import net.mehstudios.etimetable.server.push.ServiceAccountKey
import net.mehstudios.etimetable.server.push.ServiceAccountRefused
import net.mehstudios.etimetable.server.push.TokenEndpointUnavailable
import java.io.IOException
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FcmSenderTest {
    private val keys =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" +
        Base64.getMimeEncoder().encodeToString(keys.private.encoded) +
        "\n-----END PRIVATE KEY-----\n"

    // the shape of a key file from the Firebase console, with fields sending does not use
    private val keyFile = Json.encodeToString(
        mapOf(
            "type" to "service_account",
            "project_id" to "etimetable-test",
            "private_key_id" to "abc",
            "private_key" to pem,
            "client_email" to "push@etimetable-test.iam.gserviceaccount.com",
        ),
    )

    /**
     * Plays Google: hands out an access token (or refuses with [oauthStatus]), then answers
     * FCM with [fcm] in turn, repeating the last answer.
     */
    private class FakeGoogle(vararg fcm: HttpReply, oauthStatus: Int = 200) {
        constructor(fcmStatus: Int, fcmBody: String = "{}") : this(HttpReply(fcmStatus, fcmBody))

        val calls = mutableListOf<Triple<String, Map<String, String>, String>>()
        private val answers = ArrayDeque(fcm.toList())
        val http = HttpPost { url, headers, body ->
            calls += Triple(url, headers, body)
            when {
                !url.startsWith("https://oauth2.googleapis.com/") ->
                    if (answers.size > 1) answers.removeFirst() else answers.first()
                oauthStatus == 200 -> HttpReply(200, """{"access_token":"ya29.test","expires_in":3599}""")
                else -> HttpReply(oauthStatus, """{"error":"invalid_grant"}""")
            }
        }
        val fcmCalls get() = calls.count { it.first.startsWith("https://fcm.") }
    }

    private val waits = mutableListOf<Duration>()

    private fun sender(google: FakeGoogle) =
        FcmSender(ServiceAccountKey.parse(keyFile), google.http, wait = { waits += it })

    private fun fcmError(status: Int, code: String, errorCode: String? = null, message: String = "") = HttpReply(
        status,
        """{"error":{"code":$status,"message":"$message","status":"$code","details":[""" +
            (
                errorCode?.let {
                    """{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"$it"}"""
                }
                    ?: ""
                ) +
            "]}}",
    )

    @Test
    fun signsInWithTheServiceAccountAndSendsADataMessage() = runBlocking {
        val google = FakeGoogle(fcmStatus = 200)
        val sender = FcmSender(ServiceAccountKey.parse(keyFile), google.http)

        assertEquals(PushResult.SENT, sender.send("phone-1", mapOf("id" to "a1")))
        assertEquals(PushResult.SENT, sender.send("phone-2", mapOf("id" to "a1")))

        // one access token serves both messages
        assertEquals(3, google.calls.size)
        val (_, _, form) = google.calls[0]
        val assertion = URLDecoder.decode(form.substringAfter("assertion="), Charsets.UTF_8)
        val (header, claims, signature) = assertion.split('.')
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(keys.public)
            update("$header.$claims".toByteArray())
        }
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(signature)), "bad signature")
        val claimText = String(Base64.getUrlDecoder().decode(claims))
        val claimJson = Json.parseToJsonElement(claimText).jsonObject
        assertEquals(
            "push@etimetable-test.iam.gserviceaccount.com",
            claimJson.getValue("iss").jsonPrimitive.content,
        )
        assertEquals(
            "https://www.googleapis.com/auth/firebase.messaging",
            claimJson.getValue("scope").jsonPrimitive.content,
        )

        val (url, headers, body) = google.calls[1]
        assertEquals("https://fcm.googleapis.com/v1/projects/etimetable-test/messages:send", url)
        assertEquals("Bearer ya29.test", headers["Authorization"])
        val message = Json.parseToJsonElement(body).jsonObject.getValue("message").jsonObject
        assertEquals("phone-1", message.getValue("token").jsonPrimitive.content)
        assertEquals("a1", message.getValue("data").jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun unregisteredAndOtherProjectsTokensAreGone() = runBlocking {
        val unregistered = sender(FakeGoogle(fcmError(404, "NOT_FOUND", "UNREGISTERED")))
        assertEquals(PushResult.GONE, unregistered.send("old-phone", mapOf("id" to "a1")))

        val mismatch = sender(FakeGoogle(fcmError(403, "PERMISSION_DENIED", "SENDER_ID_MISMATCH")))
        assertEquals(PushResult.GONE, mismatch.send("other-project", mapOf("id" to "a1")))

        val invalid = fcmError(
            400,
            "INVALID_ARGUMENT",
            "INVALID_ARGUMENT",
            "The registration token is not a valid FCM registration token",
        )
        assertEquals(PushResult.GONE, sender(FakeGoogle(invalid)).send("typo", mapOf("id" to "a1")))
    }

    @Test
    fun aNotFoundWithoutUnregisteredKeepsTheToken() = runBlocking {
        // a 404 for a wrong project id in the URL says nothing about the phone
        val sender = sender(FakeGoogle(fcmError(404, "NOT_FOUND")))

        assertEquals(PushResult.FAILED, sender.send("phone-1", mapOf("id" to "a1")))
    }

    @Test
    fun busyAnswersAreRetriedAfterRetryAfter() = runBlocking {
        val google = FakeGoogle(
            HttpReply(429, "{}", retryAfter = "7"),
            HttpReply(503, "{}"),
            HttpReply(200, "{}"),
        )

        assertEquals(PushResult.SENT, sender(google).send("phone-1", mapOf("id" to "a1")))
        assertEquals(3, google.fcmCalls)
        assertEquals(listOf(7.seconds, 2.seconds), waits)
    }

    @Test
    fun aLongRetryAfterIsCut() = runBlocking {
        val google = FakeGoogle(HttpReply(429, "{}", retryAfter = "3600"), HttpReply(200, "{}"))

        assertEquals(PushResult.SENT, sender(google).send("phone-1", mapOf("id" to "a1")))
        assertEquals(listOf(30.seconds), waits)
    }

    @Test
    fun aGatewayThatStaysBusyFailsAfterTwoRetries() = runBlocking {
        val google = FakeGoogle(500, "{}")

        assertEquals(PushResult.FAILED, sender(google).send("phone-1", mapOf("id" to "a1")))
        assertEquals(3, google.fcmCalls)
    }

    @Test
    fun aRefusedServiceAccountIsAskedForOnlyOnce() = runBlocking {
        val google = FakeGoogle(HttpReply(200, "{}"), oauthStatus = 400)
        val sender = sender(google)

        repeat(3) {
            assertFailsWith<ServiceAccountRefused> { sender.send("phone-$it", mapOf("id" to "a1")) }
        }
        assertEquals(1, google.calls.size)
    }

    @Test
    fun aMalformedKeyIsReportedAtStartup() {
        val broken = Json.encodeToString(
            mapOf(
                "project_id" to "etimetable-test",
                "private_key" to "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n",
                "client_email" to "push@etimetable-test.iam.gserviceaccount.com",
            ),
        )

        val error = assertFailsWith<IllegalStateException> { pushSenders(broken) }
        assertTrue("ETT_FCM_SERVICE_ACCOUNT" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aCutOffKeyIsReportedWithoutQuotingIt() {
        // a value whose closing quote was lost in .env
        val truncated = keyFile.take(keyFile.indexOf("-----END"))
        val secret = pem.lines()[1]

        val error = assertFailsWith<IllegalStateException> { pushSenders(truncated) }
        val message = error.message.orEmpty()
        assertTrue("ETT_FCM_SERVICE_ACCOUNT" in message, message)
        assertFalse("PRIVATE KEY" in message || secret.take(20) in message, message)
        assertFalse(secret.take(20) in error.stackTraceToString(), "the key is in the stack trace")
    }

    @Test
    fun aKeyWithoutAPrivateKeyNamesTheMissingField() {
        val noKey = Json.encodeToString(mapOf("project_id" to "p", "client_email" to "push@p.iam.gserviceaccount.com"))

        val error = assertFailsWith<IllegalStateException> { pushSenders(noKey) }
        assertTrue("private_key" in error.message.orEmpty(), error.message)
    }

    /** Google's token endpoint answering [oauth] in turn (an exception is thrown), FCM always 200. */
    private fun tokenEndpoint(vararg oauth: Any): Pair<HttpPost, () -> Int> {
        val answers = ArrayDeque(oauth.toList())
        var asked = 0
        val http = HttpPost { url, _, _ ->
            if (url.startsWith("https://fcm.")) return@HttpPost HttpReply(200, "{}")
            asked++
            when (val next = answers.removeFirst()) {
                is Exception -> throw next
                is Int -> if (next == 200) {
                    HttpReply(200, """{"access_token":"ya29.test","expires_in":3599}""")
                } else {
                    HttpReply(next, "{}")
                }
                else -> error("bad answer $next")
            }
        }
        return http to { asked }
    }

    @Test
    fun aBusyOrUnreachableTokenEndpointIsAskedOnceMore() = runBlocking {
        for (first in listOf<Any>(503, 429, IOException("connection reset"))) {
            val (http, asked) = tokenEndpoint(first, 200)
            val sender = FcmSender(ServiceAccountKey.parse(keyFile), http, wait = { waits += it })

            assertEquals(PushResult.SENT, sender.send("phone-1", mapOf("id" to "a1")), "after $first")
            assertEquals(2, asked(), "after $first")
        }
    }

    @Test
    fun aTokenEndpointThatStaysDownStopsTheRunButIsNotHeldAsARefusal() = runBlocking {
        val (http, asked) = tokenEndpoint(503, 503, 200)
        val sender = FcmSender(ServiceAccountKey.parse(keyFile), http, wait = { waits += it })

        assertFailsWith<TokenEndpointUnavailable> { sender.send("phone-1", mapOf("id" to "a1")) }
        // the next announcement asks again at once, unlike after a refusal
        assertEquals(PushResult.SENT, sender.send("phone-1", mapOf("id" to "a1")))
        assertEquals(3, asked())
    }

    @Test
    fun onlyBadRequestAndAuthErrorsAreRefusals() = runBlocking {
        for (status in listOf(400, 401, 403)) {
            val (http, asked) = tokenEndpoint(status)
            val sender = FcmSender(ServiceAccountKey.parse(keyFile), http, wait = { waits += it })

            assertFailsWith<ServiceAccountRefused> { sender.send("phone-1", mapOf("id" to "a1")) }
            assertEquals(1, asked(), "status $status")
        }
    }
}
