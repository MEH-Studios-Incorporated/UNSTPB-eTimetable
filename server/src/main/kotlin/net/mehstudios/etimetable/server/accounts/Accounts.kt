// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.accounts

import io.ktor.http.HttpStatusCode
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.LoginRequest
import net.mehstudios.etimetable.api.MIN_PASSWORD_LENGTH
import net.mehstudios.etimetable.api.SessionResponse
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.server.ApiProblem
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.SQLException
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

// Verified against when the email is unknown, so both cases take the same time.
private val DUMMY_HASH = Passwords.hash("not a real password")

class Accounts(private val db: DataSource) {
    private val random = SecureRandom()

    suspend fun signup(request: SignupRequest): SessionResponse {
        val email = request.email.trim()
        if (!EMAIL.matches(email) ||
            email.length > 254
        ) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, ErrorCodes.INVALID_EMAIL)
        }
        if (request.password.length <
            MIN_PASSWORD_LENGTH
        ) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, ErrorCodes.WEAK_PASSWORD)
        }
        val id = UUID.randomUUID()
        val hash = Passwords.hash(request.password)
        val profile = AppJson.encodeToString(Profile.serializer(), Profile(displayName = request.displayName.trim()))
        return db.transaction { c ->
            try {
                c.update(
                    "INSERT INTO student (id, email, password_hash, profile) VALUES (?, ?, ?, ?::jsonb)",
                    id,
                    email,
                    hash,
                    profile,
                )
            } catch (e: SQLException) {
                if (e.sqlState == UNIQUE_VIOLATION) throw ApiProblem(HttpStatusCode.Conflict, ErrorCodes.EMAIL_TAKEN)
                throw e
            }
            SessionResponse(newSession(c, id))
        }
    }

    suspend fun login(request: LoginRequest): SessionResponse = db.transaction { c ->
        val row = c.query("SELECT id, password_hash FROM student WHERE lower(email) = lower(?)", request.email.trim()) {
            it.getObject(1, UUID::class.java) to it.getString(2)
        }.singleOrNull()
        val valid = Passwords.verify(request.password, row?.second ?: DUMMY_HASH) && row != null
        if (!valid) throw ApiProblem(HttpStatusCode.Unauthorized, ErrorCodes.WRONG_CREDENTIALS)
        SessionResponse(newSession(c, row.first))
    }

    /** The student a bearer token belongs to, or null. */
    suspend fun studentFor(token: String): UUID? = db.transaction { c ->
        c.query(
            "UPDATE student_session SET last_used_at = now() WHERE token_hash = ? RETURNING student_id",
            sha256(token),
        ) { it.getObject(1, UUID::class.java) }.singleOrNull()
    }

    suspend fun logout(token: String) {
        db.transaction { c -> c.update("DELETE FROM student_session WHERE token_hash = ?", sha256(token)) }
    }

    suspend fun profile(studentId: UUID): Profile = db.transaction { c ->
        c.query("SELECT profile FROM student WHERE id = ?", studentId) {
            AppJson.decodeFromString(Profile.serializer(), it.getString(1))
        }.single()
    }

    suspend fun saveProfile(studentId: UUID, profile: Profile) {
        db.transaction { c ->
            c.update(
                "UPDATE student SET profile = ?::jsonb WHERE id = ?",
                AppJson.encodeToString(Profile.serializer(), profile),
                studentId,
            )
        }
    }

    /** Deletes the account; its sessions go with it. */
    suspend fun delete(studentId: UUID) {
        db.transaction { c -> c.update("DELETE FROM student WHERE id = ?", studentId) }
    }

    private fun newSession(c: java.sql.Connection, studentId: UUID): String {
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        c.update("INSERT INTO student_session (token_hash, student_id) VALUES (?, ?)", sha256(token), studentId)
        return token
    }

    private fun sha256(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
