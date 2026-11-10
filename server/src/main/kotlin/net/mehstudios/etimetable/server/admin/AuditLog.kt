// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/** What an entry is about, for the log's filter. */
enum class AuditCategory(val label: String) {
    ACCOUNTS("Conturi și securitate"),
    TIMETABLE("Orar"),
    ANNOUNCEMENTS("Anunțuri"),
    EVENTS("Evenimente"),
    REPORTS("Probleme de orar"),
    STUDENTS("Studenți"),
}

data class AuditEntry(val at: Instant, val adminEmail: String, val category: AuditCategory, val summary: String)

/**
 * Who changed what in the dashboard. Entries are only added; the dashboard has no way to
 * edit or delete them.
 */
class AuditLog(private val db: DataSource) {
    suspend fun record(admin: Admin, category: AuditCategory, summary: String) {
        record(admin.id, admin.email, category, summary)
    }

    suspend fun record(adminId: UUID?, adminEmail: String, category: AuditCategory, summary: String) {
        db.transaction { c ->
            c.update(
                "INSERT INTO audit_log (admin_id, admin_email, category, summary) VALUES (?, ?, ?, ?)",
                adminId,
                adminEmail,
                category.name.lowercase(),
                summary,
            )
        }
    }

    suspend fun count(filter: AuditFilter): Int = db.transaction { c ->
        val (where, args) = filter.sql()
        c.query("SELECT count(*) FROM audit_log WHERE $where", *args.toTypedArray()) { it.getInt(1) }.single()
    }

    /** Newest first. */
    suspend fun page(filter: AuditFilter, offset: Int, limit: Int): List<AuditEntry> = db.transaction { c ->
        val (where, args) = filter.sql()
        c.query(
            """
            SELECT at, admin_email, category, summary FROM audit_log WHERE $where
            ORDER BY at DESC, id DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + listOf(limit, offset)).toTypedArray(),
        ) {
            AuditEntry(
                it.getObject(1, OffsetDateTime::class.java).toInstant().toKotlinInstant(),
                it.getString(2),
                AuditCategory.valueOf(it.getString(3).uppercase()),
                it.getString(4),
            )
        }
    }

    /** The emails that appear in the log, for the filter. */
    suspend fun authors(): List<String> = db.transaction { c ->
        c.query("SELECT DISTINCT admin_email FROM audit_log ORDER BY admin_email") { it.getString(1) }
    }
}

data class AuditFilter(val category: AuditCategory? = null, val adminEmail: String? = null, val text: String = "") {
    fun sql(): Pair<String, List<Any>> {
        val parts = mutableListOf("true")
        val args = mutableListOf<Any>()
        if (category != null) {
            parts += "category = ?"
            args += category.name.lowercase()
        }
        if (!adminEmail.isNullOrBlank()) {
            parts += "admin_email = ?"
            args += adminEmail
        }
        if (text.isNotBlank()) {
            parts += "lower(summary) LIKE ?"
            args += "%" + text.trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        return parts.joinToString(" AND ") to args
    }
}
