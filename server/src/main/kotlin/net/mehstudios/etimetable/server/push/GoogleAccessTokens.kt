// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
private const val JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer"
private const val TOKEN_LIFETIME_SECONDS = 3600L

/** Where Google hands out access tokens; its clock is the one the server's must match. */
const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"

/** No access token, so no message can go out; the run that hit it stops. */
sealed class NoAccessToken(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Google refused to hand out an access token: the key was deleted, disabled or belongs to
 * another project. Nothing can be sent until the deployment changes the key.
 */
class ServiceAccountRefused(val status: Int, body: String) :
    NoAccessToken("Google refused the service account ($status): ${body.take(300)}")

/** Google's token endpoint was busy or out of reach, also after a retry. */
class TokenEndpointUnavailable(reason: String, cause: Throwable? = null) :
    NoAccessToken("Google's token endpoint did not answer: $reason", cause)

/**
 * ETT_FCM_SERVICE_ACCOUNT is not a usable key. [message] says only what is wrong, never
 * the value itself, because it ends up in the startup log.
 */
class InvalidServiceAccountKey(message: String) : IllegalArgumentException(message)

/** A token is renewed this long before Google says it expires. */
private val RENEW_EARLY = 5.minutes

/** After a refusal Google is not asked again for this long; the answer would not change. */
private val REFUSAL_HOLD = 1.minutes

/** The pause before the one retry of a busy or unreachable token endpoint. */
private val TOKEN_RETRY_WAIT = 2.seconds

/** Answers that mean the key itself is refused; anything else may pass on its own. */
private val REFUSED_STATUSES = setOf(400, 401, 403)

private val lenientJson = Json { ignoreUnknownKeys = true }

/** The fields of a Firebase service account key file that sending needs. */
data class ServiceAccountKey(
    val projectId: String,
    val clientEmail: String,
    val privateKey: String,
    val tokenUri: String = GOOGLE_TOKEN_URL,
) {
    companion object {
        /**
         * The key file's JSON, as downloaded from the Firebase console. Throws
         * [InvalidServiceAccountKey] with a message that quotes nothing of [json]: the
         * serialization library's own messages repeat the input, private key included.
         */
        fun parse(json: String): ServiceAccountKey {
            val fields = try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: throw InvalidServiceAccountKey("it is not a JSON object (is the value cut off or quoted wrongly?)")
            fun text(name: String): String? = (fields[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val missing = listOf("project_id", "client_email", "private_key").filter { text(it).isNullOrBlank() }
            if (missing.isNotEmpty()) {
                throw InvalidServiceAccountKey("it has no ${missing.joinToString(", ")}")
            }
            val key = ServiceAccountKey(
                projectId = text("project_id")!!,
                clientEmail = text("client_email")!!,
                privateKey = text("private_key")!!,
                tokenUri = text("token_uri") ?: GOOGLE_TOKEN_URL,
            )
            try {
                readPkcs8(key.privateKey)
            } catch (_: Exception) {
                throw InvalidServiceAccountKey("its private_key is not an RSA key in PEM form")
            }
            return key
        }
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
    private val wait: suspend (Duration) -> Unit = { delay(it) },
) {
    private val signingKey: PrivateKey = readPkcs8(key.privateKey)
    private val lock = Mutex()
    private var current: Pair<String, Instant>? = null
    private var refused: Pair<ServiceAccountRefused, Instant>? = null

    suspend fun token(): String = lock.withLock {
        val now = clock.now()
        current?.let { (token, expires) -> if (now < expires - RENEW_EARLY) return token }
        refused?.let { (error, at) -> if (now < at + REFUSAL_HOLD) throw error }
        val reply = askWithOneRetry(now)
        if (reply.status in REFUSED_STATUSES) {
            val error = ServiceAccountRefused(reply.status, reply.body)
            refused = error to now
            throw error
        }
        if (reply.status != 200) throw TokenEndpointUnavailable("status ${reply.status}")
        refused = null
        val parsed = lenientJson.decodeFromString(TokenReply.serializer(), reply.body)
        current = parsed.accessToken to now + parsed.expiresIn.seconds
        parsed.accessToken
    }

    /** One request, and a second after [TOKEN_RETRY_WAIT] when Google was busy (429, 5xx) or out of reach. */
    private suspend fun askWithOneRetry(now: Instant): HttpReply {
        var lastError: IOException? = null
        repeat(2) { attempt ->
            if (attempt > 0) wait(TOKEN_RETRY_WAIT)
            val form = "grant_type=${encode(JWT_BEARER)}&assertion=${encode(assertion(now))}"
            val reply = try {
                http.post(key.tokenUri, mapOf("Content-Type" to "application/x-www-form-urlencoded"), form)
            } catch (e: IOException) {
                lastError = e
                null
            }
            if (reply != null && (reply.status == 200 || reply.status in REFUSED_STATUSES || attempt == 1)) {
                return reply
            }
        }
        throw TokenEndpointUnavailable(lastError?.javaClass?.simpleName ?: "no answer", lastError)
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
