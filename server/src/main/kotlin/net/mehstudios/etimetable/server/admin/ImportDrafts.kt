// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.util.UUID
import javax.sql.DataSource

/** A file in the import wizard: who uploaded it, for which semester, and how its columns map. */
class ImportDraft(
    val id: UUID,
    val semesterId: String,
    val adminId: UUID,
    val fileName: String,
    val text: String,
    val mapping: Map<String, Int>,
)

private val MAPPING = MapSerializer(String.serializer(), Int.serializer())

class ImportDrafts(private val db: DataSource) {
    /** Stores a new draft and deletes the ones older than a day. */
    suspend fun create(
        semesterId: String,
        adminId: UUID,
        fileName: String,
        text: String,
        mapping: Map<String, Int>,
    ): UUID = db.transaction { c ->
        c.update("DELETE FROM import_draft WHERE created_at < now() - interval '24 hours'")
        c.query(
            """
            INSERT INTO import_draft (semester_id, admin_id, file_name, file_text, mapping)
            VALUES (?, ?, ?, ?, ?::jsonb) RETURNING id
            """.trimIndent(),
            semesterId,
            adminId,
            fileName,
            text,
            AppJson.encodeToString(MAPPING, mapping),
        ) { it.getObject(1, UUID::class.java) }.single()
    }

    /** The draft if [adminId] made it for [semesterId] in the last 24 hours. */
    suspend fun get(id: UUID, adminId: UUID, semesterId: String): ImportDraft? = db.transaction { c ->
        c.query(
            """
            SELECT id, semester_id, admin_id, file_name, file_text, mapping FROM import_draft
            WHERE id = ? AND admin_id = ? AND semester_id = ? AND created_at >= now() - interval '24 hours'
            """.trimIndent(),
            id,
            adminId,
            semesterId,
        ) {
            ImportDraft(
                it.getObject(1, UUID::class.java),
                it.getString(2),
                it.getObject(3, UUID::class.java),
                it.getString(4),
                it.getString(5),
                AppJson.decodeFromString(MAPPING, it.getString(6)),
            )
        }.singleOrNull()
    }

    suspend fun saveMapping(id: UUID, mapping: Map<String, Int>) {
        db.transaction { c ->
            c.update(
                "UPDATE import_draft SET mapping = ?::jsonb WHERE id = ?",
                AppJson.encodeToString(MAPPING, mapping),
                id,
            )
        }
    }

    /** Replaces the file's text after a row was corrected in the preview; the file name stays. */
    suspend fun saveText(id: UUID, text: String) {
        db.transaction { c -> c.update("UPDATE import_draft SET file_text = ? WHERE id = ?", text, id) }
    }

    suspend fun delete(id: UUID) {
        db.transaction { c -> c.update("DELETE FROM import_draft WHERE id = ?", id) }
    }
}
