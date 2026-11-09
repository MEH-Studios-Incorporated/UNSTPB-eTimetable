// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.push

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
private const val JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer"
private const val TOKEN_LIFETIME_SECONDS = 3600L

/** Where Google hands out access tokens; its clock is the one the server's must match. */
const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"

/**
 * Google refused to hand out an access token: the key was deleted, disabled or belongs to
 * another project. Nothing can be sent until the deployment changes the key.
 */
class ServiceAccountRefused(val status: Int, body: String) :
    IllegalStateException("Google refused the service account ($status): ${body.take(300)}")

/** A token is renewed this long before Google says it expires. */
private val RENEW_EARLY = 5.minutes

/** After a refusal Google is not asked again for this long; the answer would not change. */
private val REFUSAL_HOLD = 1.minutes

private val lenientJson = Json { ignoreUnknownKeys = true }

/** The fields of a Firebase service account key file that sending needs. */
@Serializable
data class ServiceAccountKey(
    @SerialName("project_id") val projectId: String,
    @SerialName("client_email") val clientEmail: String,
    @SerialName("private_key") val privateKey: String,
    @SerialName("token_uri") val tokenUri: String = GOOGLE_TOKEN_URL,
) {
    companion object {
        /** The key file's JSON, as downloaded from the Firebase console. */
        fun parse(json: String) = lenientJson.decodeFromString(serializer(), json)
    }
}

@Serializable
private data class TokenReply(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

/**
 * OAuth access tokens for the FCM API, signed with the service account's key the way
 * Google's own libraries do it (a JWT bearer grant), and kept until they near expiry.
 */
class GoogleAccessTokens(
    private val key: ServiceAccountKey,
    private val http: HttpPost,
    private val clock: Clock = Clock.System,
) {
    private val signingKey: PrivateKey = readPkcs8(key.privateKey)
    private val lock = Mutex()
    private var current: Pair<String, Instant>? = null
    private var refused: Pair<ServiceAccountRefused, Instant>? = null

    suspend fun token(): String = lock.withLock {
        val now = clock.now()
        current?.let { (token, expires) -> if (now < expires - RENEW_EARLY) return token }
        refused?.let { (error, at) -> if (now < at + REFUSAL_HOLD) throw error }
        val form = "grant_type=${encode(JWT_BEARER)}&assertion=${encode(assertion(now))}"
        val reply = http.post(
            key.tokenUri,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            form,
        )
        if (reply.status != 200) {
            val error = ServiceAccountRefused(reply.status, reply.body)
            refused = error to now
            throw error
        }
        refused = null
        val parsed = lenientJson.decodeFromString(TokenReply.serializer(), reply.body)
        current = parsed.accessToken to now + parsed.expiresIn.seconds
        parsed.accessToken
    }

    /** Forgets the token, after FCM said it is no longer valid. */
    suspend fun reset() {
        lock.withLock { current = null }
    }

    private fun assertion(now: Instant): String {
        val issued = now.epochSeconds
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = buildJsonObject {
            put("iss", key.clientEmail)
            put("scope", FCM_SCOPE)
            put("aud", key.tokenUri)
            put("iat", issued)
            put("exp", issued + TOKEN_LIFETIME_SECONDS)
        }.toString()
        val unsigned = "${base64Url(header.toByteArray())}.${base64Url(claims.toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(signingKey)
            update(unsigned.toByteArray())
            sign()
        }
        return "$unsigned.${base64Url(signature)}"
    }
}

/** A PEM private key ("-----BEGIN PRIVATE KEY-----" and base64 lines) as an RSA key. */
fun readPkcs8(pem: String): PrivateKey {
    val base64 = pem.lines().filterNot { it.startsWith("-----") }.joinToString("").trim()
    val spec = PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64))
    return KeyFactory.getInstance("RSA").generatePrivate(spec)
}

private val base64UrlEncoder = Base64.getUrlEncoder().withoutPadding()

private fun base64Url(bytes: ByteArray) = base64UrlEncoder.encodeToString(bytes)

private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
