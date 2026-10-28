// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.Parameters
import kotlinx.datetime.LocalDate
import net.mehstudios.etimetable.model.Break
import net.mehstudios.etimetable.model.LocalizedText
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.ParityOverride
import net.mehstudios.etimetable.model.Semester

/**
 * The semester form. Breaks are one per line, "2026-12-21 2027-01-03 Vacanța de
 * iarnă | Winter break" (the second date may be left out for a single day);
 * parity changes are "2026-11-30 pară".
 */
object CalendarForm {
    fun parse(form: Parameters, id: String): Semester {
        fun field(name: String) = form[name].orEmpty().trim()
        fun date(name: String, label: String) =
            parseDate(field(name)) ?: throw AdminFormError("$label lipsește sau nu este o dată.")

        if (!Regex("^[A-Za-z0-9_-]{1,40}$").matches(id)) {
            throw AdminFormError("Codul semestrului poate avea doar litere, cifre, - și _ (de exemplu 2026-1).")
        }
        val nameRo = field("name_ro").ifEmpty { throw AdminFormError("Numele semestrului lipsește.") }
        val first = date("first_day", "Prima zi")
        val last = date("last_day", "Ultima zi")
        if (last < first) throw AdminFormError("Ultima zi este înaintea primei zile.")

        val breaks = field("breaks").lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val parts = line.split(Regex("\\s+"), limit = 3)
            val start = parseDate(parts[0]) ?: throw AdminFormError("Vacanța \"$line\" nu începe cu o dată.")
            val end = parts.getOrNull(1)?.let(::parseDate)
            val rest = (if (end != null) parts.drop(2) else parts.drop(1)).joinToString(" ")
            val names = rest.split('|').map { it.trim() }
            val ro = names[0]
            val en = names.getOrElse(1) { ro }
            if (ro.isEmpty()) throw AdminFormError("Vacanța \"$line\" nu are nume.")
            Break(LocalizedText(ro, en), start, end ?: start)
        }
        val overrides = field("overrides").lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val parts = line.split(Regex("\\s+"))
            val day = parseDate(parts[0]) ?: throw AdminFormError("Schimbarea \"$line\" nu începe cu o dată.")
            val parity = when (parts.getOrNull(1)?.lowercase()) {
                "pară", "para", "pare", "even" -> Parity.EVEN
                "impară", "impara", "impare", "odd" -> Parity.ODD
                else -> throw AdminFormError("Schimbarea \"$line\" trebuie să spună pară sau impară.")
            }
            ParityOverride(day, parity)
        }
        return Semester(id, LocalizedText(nameRo, field("name_en").ifEmpty { nameRo }), first, last, breaks, overrides)
    }

    fun breaksText(semester: Semester): String = semester.breaks.joinToString("\n") {
        val end = if (it.lastDay == it.firstDay) "" else " ${it.lastDay}"
        "${it.firstDay}$end ${it.name.ro} | ${it.name.en}"
    }

    fun overridesText(semester: Semester): String = semester.parityOverrides.joinToString("\n") {
        "${it.weekOf} ${if (it.parity == Parity.EVEN) "pară" else "impară"}"
    }

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()
}
