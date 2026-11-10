// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

enum class DraftKind { ANNOUNCEMENT, EVENT }

/** A compose form saved before publishing; [form] holds the fields by their names in the form. */
class ComposeDraft(
    val id: UUID,
    val kind: DraftKind,
    val form: Map<String, String>,
    val imageHash: String?,
    val createdBy: String,
    val updatedBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val title: String get() = form["title"].orEmpty().trim()
}

private val FORM = MapSerializer(String.serializer(), String.serializer())

/** Drafts of announcements and events. The apps never see them. */
class ComposeDrafts(private val db: DataSource) {
    /**
     * Saves over draft [id], or as a new draft when [id] is null or no longer exists (a
     * colleague published or deleted it). Returns the draft's id and whether it is new.
     */
    suspend fun save(
        id: UUID?,
        kind: DraftKind,
        form: Map<String, String>,
        imageHash: String?,
        by: String,
    ): Pair<UUID, Boolean> = db.transaction { c ->
        val json = AppJson.encodeToString(FORM, form)
        val updated = id != null &&
            c.update(
                """
            UPDATE compose_draft SET form = ?::jsonb, image_hash = ?, updated_by = ?, updated_at = now()
            WHERE id = ? AND kind = ?
                """.trimIndent(),
                json,
                imageHash,
                by,
                id,
                kind.column,
            ) == 1
        if (updated) {
            id!! to false
        } else {
            c.query(
                """
                INSERT INTO compose_draft (kind, form, image_hash, created_by, updated_by)
                VALUES (?, ?::jsonb, ?, ?, ?) RETURNING id
                """.trimIndent(),
                kind.column,
                json,
                imageHash,
                by,
                by,
            ) { it.getObject(1, UUID::class.java) }.single() to true
        }
    }

    suspend fun get(id: UUID, kind: DraftKind): ComposeDraft? = db.transaction { c ->
        c.query("$SELECT WHERE id = ? AND kind = ?", id, kind.column, row = ::toDraft).singleOrNull()
    }

    /** The last changed first. */
    suspend fun list(kind: DraftKind, offset: Int, limit: Int): List<ComposeDraft> = db.transaction { c ->
        c.query(
            "$SELECT WHERE kind = ? ORDER BY updated_at DESC, id OFFSET ? LIMIT ?",
            kind.column,
            offset,
            limit,
            row = ::toDraft,
        )
    }

    suspend fun count(kind: DraftKind): Int = db.transaction { c ->
        c.query("SELECT count(*) FROM compose_draft WHERE kind = ?", kind.column) { it.getInt(1) }.single()
    }

    /** Deletes the draft and returns it, or null when it was already gone. */
    suspend fun delete(id: UUID, kind: DraftKind): ComposeDraft? = db.transaction { c ->
        c.query("DELETE FROM compose_draft WHERE id = ? AND kind = ? RETURNING *", id, kind.column, row = ::toDraft)
            .singleOrNull()
    }

    private fun toDraft(rs: ResultSet) = ComposeDraft(
        id = rs.getObject("id", UUID::class.java),
        kind = DraftKind.valueOf(rs.getString("kind").uppercase()),
        form = AppJson.decodeFromString(FORM, rs.getString("form")),
        imageHash = rs.getString("image_hash"),
        createdBy = rs.getString("created_by"),
        updatedBy = rs.getString("updated_by"),
        createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant().toKotlinInstant(),
        updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java).toInstant().toKotlinInstant(),
    )

    private companion object {
        const val SELECT =
            "SELECT id, kind, form, image_hash, created_by, updated_by, created_at, updated_at FROM compose_draft"
    }
}

private val DraftKind.column: String get() = name.lowercase()
