// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.timetable

/**
 * Finds the timetable columns in a file whose header row uses other names, such as a
 * sheet the secretariat wrote in Romanian. A mapping goes from a column of
 * [TimetableSheet.COLUMNS] to the position of a column in the file.
 */
object ColumnMatch {
    /** What each column holds, for the admin who matches them. */
    val DESCRIPTIONS = mapOf(
        "id" to "Codul activității",
        "programme" to "Codul specializării",
        "programme_name" to "Numele specializării",
        "year" to "Anul de studiu",
        "series" to "Seria",
        "audience" to "Grupele sau semigrupele",
        "subject_code" to "Codul materiei",
        "subject_ro" to "Numele materiei",
        "subject_en" to "Numele materiei în engleză",
        "type" to "Tipul activității",
        "day" to "Ziua",
        "start" to "Ora de început",
        "end" to "Ora de sfârșit",
        "weeks" to "Săptămânile",
        "professor" to "Profesorul",
        "professor_email" to "Emailul profesorului",
        "room" to "Sala",
        "erasmus" to "Deschisă studenților Erasmus",
    )

    /** Other names a header may use; compared after [normalize]. */
    private val ALIASES = mapOf(
        "id" to listOf("cod activitate", "activitate id"),
        "programme" to listOf("program", "specializare", "specializarea", "cod specializare"),
        "programme_name" to listOf("nume specializare", "specializare nume", "denumire specializare"),
        "year" to listOf("an", "anul", "an de studiu", "an studiu"),
        "series" to listOf("serie", "seria"),
        "audience" to listOf("grupe", "grupa", "grupele", "semigrupa", "public"),
        "subject_code" to listOf("cod materie", "cod disciplina", "subject"),
        "subject_ro" to listOf("materie", "materia", "disciplina", "nume materie", "denumire materie"),
        "subject_en" to listOf("materie engleza", "materia in engleza", "nume materie engleza", "subject name"),
        "type" to listOf("tip", "tipul", "tip activitate"),
        "day" to listOf("zi", "ziua"),
        "start" to listOf("inceput", "ora inceput", "ora de inceput", "de la", "start time"),
        "end" to listOf("sfarsit", "final", "ora sfarsit", "ora de sfarsit", "pana la", "end time"),
        "weeks" to listOf("saptamani", "saptamanile", "paritate"),
        "professor" to listOf("profesor", "profesorul", "cadru didactic", "titular"),
        "professor_email" to listOf("email profesor", "email", "e mail profesor", "mail profesor"),
        "room" to listOf("sala", "sala de curs", "locatie"),
        "erasmus" to listOf("deschis erasmus", "deschisa erasmus"),
    )

    /** "Ora început" and "ora_inceput" become "ora inceput". */
    fun normalize(text: String): String = TimetableSheet.fold(text).replace(Regex("[\\s_.\\-]+"), " ").trim()

    /** Each column of the sheet to the first header cell that names it; each cell is used once. */
    fun detect(header: List<String>): Map<String, Int> {
        val names = header.map(::normalize)
        val used = mutableSetOf<Int>()
        val mapping = linkedMapOf<String, Int>()
        for (column in TimetableSheet.COLUMNS) {
            val wanted = (listOf(column) + ALIASES[column].orEmpty()).map(::normalize).toSet()
            val index = names.indices.firstOrNull { it !in used && names[it] in wanted } ?: continue
            used += index
            mapping[column] = index
        }
        return mapping
    }

    /** The columns of the sheet that a file must have and [mapping] leaves out. */
    fun missing(mapping: Map<String, Int>): List<String> =
        TimetableSheet.COLUMNS.filter { it !in TimetableSheet.OPTIONAL && it !in mapping }

    /** [rows] with the header renamed and the cells reordered as [mapping] says. */
    fun apply(rows: List<List<String>>, mapping: Map<String, Int>): List<List<String>> {
        if (rows.isEmpty()) return rows
        val columns = TimetableSheet.COLUMNS.filter { it in mapping }
        return listOf(columns) + rows.drop(1).map { row -> columns.map { row.getOrElse(mapping.getValue(it)) { "" } } }
    }
}
