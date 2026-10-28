// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.dataset

import kotlinx.serialization.KSerializer
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Building
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Group
import net.mehstudios.etimetable.model.HalfGroup
import net.mehstudios.etimetable.model.Professor
import net.mehstudios.etimetable.model.Programme
import net.mehstudios.etimetable.model.Room
import net.mehstudios.etimetable.model.Semester
import net.mehstudios.etimetable.model.Series
import net.mehstudios.etimetable.model.Subject
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.sql.Connection
import javax.sql.DataSource

/** The current semester's dataset as served, with the JSON encoded once per version. */
class DatasetSnapshot(val semesterId: String, val version: Long, val dataset: Dataset) {
    val json: String by lazy { AppJson.encodeToString(dataset) }
    val etag: String get() = "\"$semesterId-$version\""
}

/** A row of the dashboard's semester list. */
data class SemesterSummary(val semester: Semester, val isCurrent: Boolean, val activityCount: Int)

class DatasetRepository(private val db: DataSource) {
    @Volatile
    private var cache: DatasetSnapshot? = null

    /** The current semester, or null before an admin has made one current. */
    suspend fun current(): DatasetSnapshot? = db.transaction { c ->
        val (id, version) = c.query("SELECT id, version FROM semester WHERE is_current") {
            it.getString(1) to it.getLong(2)
        }.singleOrNull() ?: return@transaction null
        cache?.takeIf { it.semesterId == id && it.version == version }
            ?: DatasetSnapshot(id, version, load(c, id)).also { cache = it }
    }

    /** Stores [dataset] in place of everything its semester had. */
    suspend fun replace(dataset: Dataset, makeCurrent: Boolean) = db.transaction { c ->
        val semesterId = dataset.semester.id
        c.update(
            """
            INSERT INTO semester (id, data) VALUES (?, ?::jsonb)
            ON CONFLICT (id) DO UPDATE SET data = excluded.data, version = semester.version + 1
            """.trimIndent(),
            semesterId,
            AppJson.encodeToString(Semester.serializer(), dataset.semester),
        )
        c.update("DELETE FROM dataset_item WHERE semester_id = ?", semesterId)
        c.prepareStatement(
            "INSERT INTO dataset_item (semester_id, kind, id, position, data) VALUES (?, ?, ?, ?, ?::jsonb)",
        ).use { st ->
            for (kind in Kind.entries) {
                for ((position, item) in kind.encode(dataset).withIndex()) {
                    st.setString(1, semesterId)
                    st.setString(2, kind.key)
                    st.setString(3, item.first)
                    st.setInt(4, position)
                    st.setString(5, item.second)
                    st.addBatch()
                }
            }
            st.executeBatch()
        }
        if (makeCurrent) {
            c.update("UPDATE semester SET is_current = FALSE WHERE is_current AND id <> ?", semesterId)
            c.update("UPDATE semester SET is_current = TRUE WHERE id = ?", semesterId)
        }
    }

    suspend fun semesters(): List<SemesterSummary> = db.transaction { c ->
        c.query(
            """
            SELECT s.data, s.is_current,
                   (SELECT count(*) FROM dataset_item i WHERE i.semester_id = s.id AND i.kind = 'activity')
            FROM semester s ORDER BY s.data ->> 'firstDay' DESC
            """.trimIndent(),
        ) {
            SemesterSummary(
                AppJson.decodeFromString(Semester.serializer(), it.getString(1)),
                it.getBoolean(2),
                it.getInt(3),
            )
        }
    }

    /** Any semester, current or not. */
    suspend fun dataset(semesterId: String): Dataset? = db.transaction { c ->
        if (c.query("SELECT 1 FROM semester WHERE id = ?", semesterId) { 1 }.isEmpty()) null else load(c, semesterId)
    }

    /** Changes a semester's calendar and keeps its items. */
    suspend fun saveSemester(semester: Semester) {
        db.transaction { c ->
            c.update(
                "UPDATE semester SET data = ?::jsonb, version = version + 1 WHERE id = ?",
                AppJson.encodeToString(Semester.serializer(), semester),
                semester.id,
            )
        }
    }

    suspend fun makeCurrent(semesterId: String) {
        db.transaction { c ->
            c.update("UPDATE semester SET is_current = FALSE WHERE is_current AND id <> ?", semesterId)
            c.update("UPDATE semester SET is_current = TRUE WHERE id = ?", semesterId)
        }
    }

    private fun load(c: Connection, semesterId: String): Dataset {
        val semester = c.query("SELECT data FROM semester WHERE id = ?", semesterId) {
            AppJson.decodeFromString(Semester.serializer(), it.getString(1))
        }.single()
        val rows = c.query(
            "SELECT kind, data FROM dataset_item WHERE semester_id = ? ORDER BY kind, position",
            semesterId,
        ) {
            it.getString(1) to it.getString(2)
        }.groupBy({ it.first }, { it.second })

        fun <T> list(kind: Kind, serializer: KSerializer<T>): List<T> =
            rows[kind.key].orEmpty().map { AppJson.decodeFromString(serializer, it) }

        return Dataset(
            semester = semester,
            programmes = list(Kind.PROGRAMME, Programme.serializer()),
            series = list(Kind.SERIES, Series.serializer()),
            groups = list(Kind.GROUP, Group.serializer()),
            halfGroups = list(Kind.HALF_GROUP, HalfGroup.serializer()),
            subjects = list(Kind.SUBJECT, Subject.serializer()),
            professors = list(Kind.PROFESSOR, Professor.serializer()),
            buildings = list(Kind.BUILDING, Building.serializer()),
            rooms = list(Kind.ROOM, Room.serializer()),
            activities = list(Kind.ACTIVITY, Activity.serializer()),
        )
    }

    private enum class Kind(val key: String, val encode: (Dataset) -> List<Pair<String, String>>) {
        PROGRAMME("programme", { d ->
            d.programmes.map { it.id to AppJson.encodeToString(Programme.serializer(), it) }
        }),
        SERIES("series", { d -> d.series.map { it.id to AppJson.encodeToString(Series.serializer(), it) } }),
        GROUP("group", { d -> d.groups.map { it.id to AppJson.encodeToString(Group.serializer(), it) } }),
        HALF_GROUP("half_group", { d ->
            d.halfGroups.map { it.id to AppJson.encodeToString(HalfGroup.serializer(), it) }
        }),
        SUBJECT("subject", { d -> d.subjects.map { it.id to AppJson.encodeToString(Subject.serializer(), it) } }),
        PROFESSOR("professor", { d ->
            d.professors.map { it.id to AppJson.encodeToString(Professor.serializer(), it) }
        }),
        BUILDING("building", { d -> d.buildings.map { it.id to AppJson.encodeToString(Building.serializer(), it) } }),
        ROOM("room", { d -> d.rooms.map { it.id to AppJson.encodeToString(Room.serializer(), it) } }),
        ACTIVITY("activity", { d -> d.activities.map { it.id to AppJson.encodeToString(Activity.serializer(), it) } }),
    }
}
