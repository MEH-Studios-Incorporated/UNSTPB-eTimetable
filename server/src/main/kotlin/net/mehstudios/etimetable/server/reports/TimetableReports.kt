// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.reports

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.MAX_REPORT_DESCRIPTION_LENGTH
import net.mehstudios.etimetable.api.MIN_REPORT_DESCRIPTION_LENGTH
import net.mehstudios.etimetable.api.TimetableReportReceipt
import net.mehstudios.etimetable.api.TimetableReportRequest
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.server.ApiProblem
import net.mehstudios.etimetable.server.dataset.DatasetSnapshot
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

@Serializable
data class ReportClassSnapshot(
    val semesterName: String,
    val subjectName: String,
    val professorName: String?,
    val room: String?,
    val audience: String,
    val activity: Activity,
)

enum class ReportStatus(val value: String) { OPEN("open"), FIXED("fixed"), DISMISSED("dismissed") }

data class TimetableReport(
    val id: UUID,
    val studentId: UUID,
    val studentName: String,
    val studentEmail: String,
    val semesterId: String,
    val activityId: String,
    val snapshot: ReportClassSnapshot,
    val description: String,
    val status: ReportStatus,
    val createdAt: Instant,
    val reviewedAt: Instant?,
    /**
     * The closing administrator's email, read by [TimetableReports.find] only. Null while
     * open, in [TimetableReports.page] and after that administrator was deleted.
     */
    val reviewedBy: String? = null,
)

class ReportLimitExceeded(val retryAfterSeconds: Int) : RuntimeException()

class TimetableReports(private val db: DataSource) {
    suspend fun submit(
        studentId: UUID,
        request: TimetableReportRequest,
        current: DatasetSnapshot?,
    ): TimetableReportReceipt {
        val description = request.description.trim()
        val length = description.codePointCount(0, description.length)
        val unsupportedControl = description.any { it.isISOControl() && it !in "\n\r\t" }
        if (length !in MIN_REPORT_DESCRIPTION_LENGTH..MAX_REPORT_DESCRIPTION_LENGTH ||
            unsupportedControl ||
            request.semesterId.length !in 1..256 ||
            request.activityId.length !in 1..256
        ) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, ErrorCodes.INVALID_REPORT)
        }
        val dataset = current?.dataset
        val activity = dataset?.activitiesById?.get(request.activityId)
        if (dataset == null || dataset.semester.id != request.semesterId || activity == null) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, ErrorCodes.UNKNOWN_ACTIVITY)
        }
        val snapshot = ReportClassSnapshot(
            dataset.semester.name.ro,
            dataset.subjectsById[activity.subjectId]?.name?.ro ?: activity.subjectId,
            dataset.professorsById[activity.professorId]?.displayName,
            dataset.roomsById[activity.roomId]?.code,
            (
                activity.audience.seriesIds.map { id -> dataset.series.firstOrNull { it.id == id }?.name ?: id } +
                    activity.audience.groupIds.map { dataset.groupsById[it]?.code ?: it } +
                    activity.audience.halfGroupIds.map { id ->
                        dataset.halfGroupsById[id]?.let { half ->
                            "${dataset.groupsById[half.groupId]?.code ?: half.groupId}/${half.suffix}"
                        } ?: id
                    }
                ).joinToString(", "),
            activity,
        )
        return db.transaction { c ->
            // Serialize all sessions for this account, including concurrent submissions.
            if (c.query("SELECT id FROM student WHERE id = ? FOR UPDATE", studentId) { 1 }.isEmpty()) {
                throw ApiProblem(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED)
            }
            val version = c.query(
                "SELECT version FROM semester WHERE id = ? AND is_current FOR SHARE",
                request.semesterId,
            ) { it.getLong(1) }.singleOrNull()
            if (version != current?.version) {
                throw ApiProblem(HttpStatusCode.UnprocessableEntity, ErrorCodes.UNKNOWN_ACTIVITY)
            }
            val duplicate = c.query(
                "SELECT 1 FROM timetable_report WHERE student_id = ? AND semester_id = ? AND activity_id = ? AND status = 'open'",
                studentId,
                request.semesterId,
                request.activityId,
            ) { 1 }.isNotEmpty()
            if (duplicate) throw ApiProblem(HttpStatusCode.Conflict, ErrorCodes.REPORT_ALREADY_OPEN)
            val recent = c.query(
                """
                SELECT count(*), greatest(1, ceil(extract(epoch FROM min(created_at) + interval '1 hour' - clock_timestamp())))
                FROM timetable_report WHERE student_id = ? AND created_at > clock_timestamp() - interval '1 hour'
                """.trimIndent(),
                studentId,
            ) { it.getInt(1) to it.getInt(2) }.single()
            if (recent.first >= REPORTS_PER_HOUR) throw ReportLimitExceeded(recent.second)
            val id = UUID.randomUUID()
            c.update(
                """
                INSERT INTO timetable_report (id, student_id, semester_id, activity_id, snapshot, description)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                """.trimIndent(),
                id,
                studentId,
                request.semesterId,
                request.activityId,
                AppJson.encodeToString(ReportClassSnapshot.serializer(), snapshot),
                description,
            )
            TimetableReportReceipt(id.toString())
        }
    }

    suspend fun openCount(): Int = db.transaction { c ->
        c.query("SELECT count(*) FROM timetable_report WHERE status = 'open'") { it.getInt(1) }.single()
    }

    /** One extra row tells the page whether another page exists. */
    suspend fun page(closed: Boolean, page: Int): List<TimetableReport> = db.transaction { c ->
        // A plain comparison on status, so the planner can use timetable_report_queue.
        val status = if (closed) "r.status <> 'open'" else "r.status = 'open'"
        c.query(
            """
            SELECT r.*, s.email AS student_email, NULL AS reviewer_email,
                trim(concat_ws(' ', s.profile ->> 'firstName', s.profile ->> 'lastName')) AS student_name
            FROM timetable_report r JOIN student s ON s.id = r.student_id
            WHERE $status
            ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?
            """.trimIndent(),
            REPORT_PAGE_SIZE + 1,
            page.coerceIn(0, 1_000_000) * REPORT_PAGE_SIZE,
        ) { it.report() }
    }

    suspend fun find(id: UUID): TimetableReport? = db.transaction { c ->
        c.query(
            """
            SELECT r.*, s.email AS student_email, a.email AS reviewer_email,
                trim(concat_ws(' ', s.profile ->> 'firstName', s.profile ->> 'lastName')) AS student_name
            FROM timetable_report r JOIN student s ON s.id = r.student_id
            LEFT JOIN admin a ON a.id = r.reviewed_by WHERE r.id = ?
            """.trimIndent(),
            id,
        ) { it.report() }.singleOrNull()
    }

    /** An already reviewed report cannot be silently overwritten by another editor. */
    suspend fun review(id: UUID, status: ReportStatus, adminId: UUID): Boolean {
        require(status != ReportStatus.OPEN)
        return db.transaction { c ->
            c.update(
                "UPDATE timetable_report SET status = ?, reviewed_at = clock_timestamp(), reviewed_by = ? WHERE id = ? AND status = 'open'",
                status.value,
                adminId,
                id,
            ) == 1
        }
    }

    private fun ResultSet.report() = TimetableReport(
        getObject("id", UUID::class.java),
        getObject("student_id", UUID::class.java),
        getString("student_name"),
        getString("student_email"),
        getString("semester_id"),
        getString("activity_id"),
        AppJson.decodeFromString(ReportClassSnapshot.serializer(), getString("snapshot")),
        getString("description"),
        ReportStatus.entries.single { it.value == getString("status") },
        getObject("created_at", OffsetDateTime::class.java).toInstant().toKotlinInstant(),
        getObject("reviewed_at", OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant(),
        getString("reviewer_email"),
    )
}

const val REPORTS_PER_HOUR = 5
const val REPORT_PAGE_SIZE = 50
