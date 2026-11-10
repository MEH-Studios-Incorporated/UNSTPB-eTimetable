// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import kotlinx.serialization.Serializable
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.server.timetable.ColumnMatch
import net.mehstudios.etimetable.server.timetable.TimetableSheet

enum class ChangeKind(val key: String, val label: String) {
    ADDED("added", "Adăugată"),
    CHANGED("changed", "Modificată"),
    REMOVED("removed", "Eliminată"),
}

/** The fields of a class that an import compares, with their names in the dashboard. */
internal val CLASS_FIELDS = linkedMapOf(
    "subject" to "Materie",
    "type" to "Tip",
    "time" to "Zi și oră",
    "weeks" to "Săptămâni",
    "audience" to "Grupe",
    "professor" to "Profesor",
    "room" to "Sală",
    "erasmus" to "Erasmus",
)

/**
 * One class an import adds, changes or removes. [before] and [after] hold the fields as
 * text; [before] is null for an added class, [after] for a removed one.
 */
class ClassChange(
    val kind: ChangeKind,
    val activityId: String,
    val line: Int?,
    val before: Map<String, String>?,
    val after: Map<String, String>?,
) {
    val changedFields: List<String>
        get() = CLASS_FIELDS.keys.filter { before?.get(it) != after?.get(it) }

    fun value(field: String): String = (after ?: before)?.get(field).orEmpty()
}

/** What an import would do to a semester's classes; [unchanged] counts the ones it keeps as they are. */
class TimetableDiff(val changes: List<ClassChange>, val unchanged: Int) {
    fun count(kind: ChangeKind) = changes.count { it.kind == kind }
}

/**
 * Compares the classes of [old] and [new] by id. [lines] gives the file line of each new
 * class. Classes in [unreadable] had a row in the file that could not be read, so they do
 * not count as removed.
 */
fun diffTimetables(
    old: Dataset,
    new: Dataset,
    lines: Map<String, Int> = emptyMap(),
    unreadable: Set<String> = emptySet(),
): TimetableDiff {
    val oldById = old.activities.associateBy { it.id }
    val newIds = new.activities.map { it.id }.toSet()
    val changes = mutableListOf<ClassChange>()
    var unchanged = 0
    for (activity in new.activities) {
        val after = describeClass(new, activity)
        val previous = oldById[activity.id]
        if (previous == null) {
            changes += ClassChange(ChangeKind.ADDED, activity.id, lines[activity.id], null, after)
            continue
        }
        val before = describeClass(old, previous)
        if (before == after) {
            unchanged++
        } else {
            changes += ClassChange(ChangeKind.CHANGED, activity.id, lines[activity.id], before, after)
        }
    }
    val removed = old.activities.filter { it.id !in newIds && it.id !in unreadable }.map {
        ClassChange(ChangeKind.REMOVED, it.id, null, describeClass(old, it), null)
    }
    return TimetableDiff(changes.sortedBy { it.line ?: Int.MAX_VALUE } + removed, unchanged)
}

/** A class as the import preview shows it, each field as text. */
internal fun describeClass(dataset: Dataset, a: Activity): Map<String, String> = mapOf(
    "subject" to (dataset.subjectsById[a.subjectId]?.name?.ro ?: a.subjectId),
    "type" to TYPE_NAMES.getValue(a.type),
    "time" to "${DAY_NAMES[a.slot.isoDay - 1]} ${a.slot.start} - ${a.slot.end}",
    "weeks" to (weeksLabel(a.slot.weeks) ?: "toate săptămânile"),
    "audience" to audienceText(dataset, a),
    "professor" to dataset.professorsById[a.professorId]?.displayName.orEmpty(),
    "room" to dataset.roomsById[a.roomId]?.code.orEmpty(),
    "erasmus" to if (a.openToErasmus) "da" else "nu",
)

private fun audienceText(dataset: Dataset, a: Activity): String {
    val series = a.audience.seriesIds.map { id ->
        val s = dataset.series.firstOrNull { it.id == id }
        val programme = dataset.programmes.firstOrNull { it.id == s?.programmeId }
        if (s == null) id else "${programme?.code ?: s.programmeId}, anul ${s.year}, seria ${s.name}"
    }
    val groups = a.audience.groupIds.map { dataset.groupsById[it]?.code ?: it }
    val halves = a.audience.halfGroupIds.map { id ->
        val half = dataset.halfGroupsById[id] ?: return@map id
        "${dataset.groupsById[half.groupId]?.code ?: half.groupId}/${half.suffix}"
    }
    return (series.sorted() + (groups + halves).sorted()).joinToString(" ")
}

/** A file read with a column mapping and compared with the semester it would replace. */
class ImportCheck(val result: TimetableSheet.ImportResult, val diff: TimetableDiff, val rowCount: Int) {
    val errors get() = result.rowErrors
    val canSave get() = result.dataset != null
}

fun checkImport(existing: Dataset, rows: List<List<String>>, mapping: Map<String, Int>): ImportCheck {
    val result = TimetableSheet.import(ColumnMatch.apply(rows, mapping), existing.semester)
    val unreadable = result.rowErrors.mapNotNull { it.activityId }.toSet()
    val diff = result.partial?.let { diffTimetables(existing, it, result.lines, unreadable) }
        ?: TimetableDiff(emptyList(), 0)
    return ImportCheck(result, diff, (rows.size - 1).coerceAtLeast(0))
}

/** The audit sentence's counts: "3 adăugate, 1 modificată, 0 eliminate". */
internal fun diffSummary(diff: TimetableDiff): String = listOf(
    diff.count(ChangeKind.ADDED) to ("adăugată" to "adăugate"),
    diff.count(ChangeKind.CHANGED) to ("modificată" to "modificate"),
    diff.count(ChangeKind.REMOVED) to ("eliminată" to "eliminate"),
).joinToString(", ") { (n, words) -> "$n ${if (n == 1) words.first else words.second}" }

/** The body of `POST /api/admin/v1/semesters/{id}/timetable`. Field names are stable. */
@Serializable
data class TimetableImportReport(
    val semesterId: String,
    val dryRun: Boolean,
    val saved: Boolean,
    /** The semester's version after the call; it changes with every save. */
    val version: Long,
    val counts: ImportCounts,
    val changes: List<ImportChange>,
    val errors: List<ImportRowError>,
    val warnings: List<String>,
)

@Serializable
data class ImportCounts(val added: Int, val changed: Int, val removed: Int, val unchanged: Int, val errors: Int)

@Serializable
data class ImportChange(
    /** added, changed or removed */
    val kind: String,
    val activityId: String,
    val line: Int?,
    val fields: List<ImportFieldChange>,
)

@Serializable
data class ImportFieldChange(val field: String, val old: String?, val new: String?)

@Serializable
data class ImportRowError(val line: Int?, val message: String)

internal fun ImportCheck.report(semesterId: String, dryRun: Boolean, saved: Boolean, version: Long) =
    TimetableImportReport(
        semesterId = semesterId,
        dryRun = dryRun,
        saved = saved,
        version = version,
        counts = ImportCounts(
            added = diff.count(ChangeKind.ADDED),
            changed = diff.count(ChangeKind.CHANGED),
            removed = diff.count(ChangeKind.REMOVED),
            unchanged = diff.unchanged,
            errors = errors.size,
        ),
        changes = diff.changes.map { c ->
            ImportChange(
                kind = c.kind.key,
                activityId = c.activityId,
                line = c.line,
                fields = c.changedFields.map { ImportFieldChange(it, c.before?.get(it), c.after?.get(it)) },
            )
        },
        errors = errors.map { ImportRowError(it.line, it.message) },
        warnings = result.warnings,
    )
