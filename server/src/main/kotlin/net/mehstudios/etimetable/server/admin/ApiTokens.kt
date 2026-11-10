// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/** Lets a token replace a semester's timetable. The only scope so far. */
const val SCOPE_TIMETABLE_WRITE = "timetable:write"

data class ApiToken(
    val id: UUID,
    val name: String,
    val scope: String,
    val createdAt: Instant,
    val createdBy: UUID?,
    val createdByEmail: String,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
) {
    fun usable(now: Instant) = revokedAt == null && (expiresAt == null || expiresAt > now)
}

/** Tokens for programs that call the admin API. The database keeps only their SHA-256. */
class ApiTokens(private val db: DataSource) {
    private val random = SecureRandom()

    /** The new token and its secret, which is shown once and never stored. */
    suspend fun create(name: String, scope: String, expiresAt: Instant?, admin: Admin): Pair<ApiToken, String> {
        val bytes = ByteArray(32).also(random::nextBytes)
        val secret = "ett_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val token = db.transaction { c ->
            c.query(
                """
                INSERT INTO api_token (name, token_hash, scope, created_by, created_by_email, expires_at)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING $COLUMNS
                """.trimIndent(),
                name,
                hash(secret),
                scope,
                admin.id,
                admin.email,
                expiresAt?.let { OffsetDateTime.ofInstant(it.toJavaInstant(), ZoneOffset.UTC) },
            ) { it.token() }.single()
        }
        return token to secret
    }

    /** Newest first. */
    suspend fun list(): List<ApiToken> = db.transaction { c ->
        c.query("SELECT $COLUMNS FROM api_token ORDER BY created_at DESC") { it.token() }
    }

    /** The token, if it was still in use. */
    suspend fun revoke(id: UUID): ApiToken? = db.transaction { c ->
        c.query(
            "UPDATE api_token SET revoked_at = now() WHERE id = ? AND revoked_at IS NULL RETURNING $COLUMNS",
            id,
        ) { it.token() }.singleOrNull()
    }

    /** The token [secret] belongs to, when it is neither revoked nor expired; marks it as used. */
    suspend fun authenticate(secret: String): ApiToken? = db.transaction { c ->
        c.query(
            """
            UPDATE api_token SET last_used_at = now()
            WHERE token_hash = ? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now())
            RETURNING $COLUMNS
            """.trimIndent(),
            hash(secret),
        ) { it.token() }.singleOrNull()
    }

    private fun hash(secret: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())

    private companion object {
        const val COLUMNS =
            "id, name, scope, created_at, created_by, created_by_email, expires_at, last_used_at, revoked_at"

        fun ResultSet.instant(column: String): Instant? =
            getObject(column, OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant()

        fun ResultSet.token() = ApiToken(
            id = getObject("id", UUID::class.java),
            name = getString("name"),
            scope = getString("scope"),
            createdAt = instant("created_at")!!,
            createdBy = getObject("created_by", UUID::class.java),
            createdByEmail = getString("created_by_email"),
            expiresAt = instant("expires_at"),
            lastUsedAt = instant("last_used_at"),
            revokedAt = instant("revoked_at"),
        )
    }
}
