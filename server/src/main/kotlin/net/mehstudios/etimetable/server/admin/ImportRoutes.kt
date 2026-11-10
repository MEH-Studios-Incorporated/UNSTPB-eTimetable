// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.utils.io.toByteArray
import kotlinx.html.*
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.timetable.ColumnMatch
import net.mehstudios.etimetable.server.timetable.Csv
import net.mehstudios.etimetable.server.timetable.SheetFile
import net.mehstudios.etimetable.server.timetable.SheetFileError
import net.mehstudios.etimetable.server.timetable.TimetableSheet

/** The largest timetable file the dashboard and the API accept. */
internal const val MAX_SHEET_BYTES = 5 * 1024 * 1024

private val STEPS = listOf(
    "Încarcă" to "CSV sau Excel",
    "Coloane" to "Potrivirea coloanelor",
    "Previzualizare" to "Ce se schimbă în orar",
    "Confirmă" to "Nimic nu e salvat încă",
)

private const val PREVIEW_PAGE_SIZE = 25
private const val SAMPLE_ROWS = 5

/**
 * The timetable import in four steps: upload a file, match its columns, see what changes,
 * confirm. Until the confirmation the file is only a draft (see [ImportDrafts]).
 */
internal fun Route.importRoutes(services: Services) {
    page("/semesters/{id}/import", services) { admin ->
        val dataset = services.datasets.dataset(call.parameters["id"].orEmpty())
            ?: return@page call.respond(HttpStatusCode.NotFound)
        call.respondHtml { uploadPage(admin, dataset, call.parameters["err"]) }
    }

    action("/semesters/{id}/import", services) { admin ->
        val semesterId = call.parameters["id"].orEmpty()
        services.datasets.dataset(semesterId) ?: return@action call.respond(HttpStatusCode.NotFound)
        var bytes: ByteArray? = null
        var fileName = "orar.csv"
        call.receiveMultipart(formFieldLimit = MAX_SHEET_BYTES.toLong()).forEachPart { part ->
            if (part is PartData.FileItem && part.name == "sheet") {
                bytes = part.provider().toByteArray()
                part.originalFileName?.takeIf { it.isNotBlank() }?.let { fileName = it.take(200) }
            }
            part.dispose()
        }
        val text = try {
            SheetFile.text(bytes ?: ByteArray(0))
        } catch (e: SheetFileError) {
            val err = e.message.orEmpty().encodeURLParameter()
            return@action call.respondRedirect("/admin/semesters/$semesterId/import?err=$err")
        }
        val rows = Csv.parse(text)
        if (rows.isEmpty()) {
            val err = "Fișierul este gol sau nu este un CSV sau un Excel (.xlsx).".encodeURLParameter()
            return@action call.respondRedirect("/admin/semesters/$semesterId/import?err=$err")
        }
        val id = services.importDrafts.create(
            semesterId,
            admin.id,
            fileName,
            text,
            ColumnMatch.detect(rows.first()),
        )
        call.respondRedirect("/admin/semesters/$semesterId/import/$id/columns")
    }

    page("/semesters/{id}/import/{draft}/columns", services) { admin ->
        val (dataset, draft) = draftOrRedirect(services, admin) ?: return@page
        val rows = Csv.parse(draft.text)
        call.respondHtml {
            wizardPage(admin, dataset, draft, rows, step = 2) {
                notice(call.parameters["err"], error = true)
                columnsStep(draft, rows)
            }
        }
    }

    action("/semesters/{id}/import/{draft}/columns", services) { admin ->
        val (dataset, draft) = draftOrRedirect(services, admin) ?: return@action
        val form = call.receiveParameters()
        val header = Csv.parse(draft.text).firstOrNull().orEmpty()
        val mapping = if (form["keep"] != null) {
            draft.mapping
        } else {
            TimetableSheet.COLUMNS.mapNotNull { column ->
                form["col_$column"]?.toIntOrNull()?.takeIf { it in header.indices }?.let { column to it }
            }.toMap()
        }
        services.importDrafts.saveMapping(draft.id, mapping)
        val base = "/admin/semesters/${dataset.semester.id}/import/${draft.id}"
        val twice = mapping.values.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()
        val missing = ColumnMatch.missing(mapping)
        val err = when {
            twice != null -> "Coloana ${columnLetter(twice)} din fișier este aleasă de mai multe ori."
            missing.isNotEmpty() -> "Alege coloanele obligatorii: ${missing.joinToString(", ")}."
            else -> null
        }
        if (err != null) return@action call.respondRedirect("$base/columns?err=${err.encodeURLParameter()}")
        call.respondRedirect("$base/preview")
    }

    page("/semesters/{id}/import/{draft}/preview", services) { admin ->
        val (_, draft) = draftOrRedirect(services, admin) ?: return@page
        val base = "/admin/semesters/${draft.semesterId}/import/${draft.id}"
        if (ColumnMatch.missing(draft.mapping).isNotEmpty()) return@page call.respondRedirect("$base/columns")
        // the version first: a change made while the file is read makes the confirmation fail, not pass
        val version = services.datasets.version(draft.semesterId) ?: return@page call.respond(HttpStatusCode.NotFound)
        val dataset = services.datasets.dataset(draft.semesterId) ?: return@page call.respond(HttpStatusCode.NotFound)
        val rows = Csv.parse(draft.text)
        val check = checkImport(dataset, rows, draft.mapping)
        call.respondHtml {
            wizardPage(admin, dataset, draft, rows, step = 3) {
                notice(call.parameters["err"], error = true)
                previewStep(admin, draft, check, version, call.parameters)
            }
        }
    }

    page("/semesters/{id}/import/{draft}/confirm", services) { admin ->
        val (dataset, draft) = draftOrRedirect(services, admin) ?: return@page
        val base = "/admin/semesters/${draft.semesterId}/import/${draft.id}"
        val version = call.parameters["version"]?.toLongOrNull() ?: return@page call.respondRedirect("$base/preview")
        val rows = Csv.parse(draft.text)
        val check = checkImport(dataset, rows, draft.mapping)
        if (!check.canSave) return@page call.respondRedirect("$base/preview")
        val stale = services.datasets.version(draft.semesterId) != version
        val isCurrent = services.datasets.current()?.semesterId == draft.semesterId
        call.respondHtml {
            wizardPage(admin, dataset, draft, rows, step = 4) {
                notice(call.parameters["err"], error = true)
                confirmStep(dataset, draft, check, version, stale, isCurrent)
            }
        }
    }

    action("/semesters/{id}/import/{draft}/confirm", services) { admin ->
        val (dataset, draft) = draftOrRedirect(services, admin) ?: return@action
        val base = "/admin/semesters/${draft.semesterId}/import/${draft.id}"
        val form = call.receiveParameters()
        val version = form["version"]?.toLongOrNull() ?: return@action call.respondRedirect("$base/preview")
        fun back(path: String, message: String) =
            "$base/$path${if ('?' in path) "&" else "?"}err=${message.encodeURLParameter()}"
        val check = checkImport(dataset, Csv.parse(draft.text), draft.mapping)
        val imported = check.result.dataset
            ?: return@action call.respondRedirect(back("preview", "Fișierul are erori, așa că nu se poate importa."))
        if (form["checked"] != "on") {
            return@action call.respondRedirect(
                back("confirm?version=$version", "Bifează că ai verificat diferențele."),
            )
        }
        val changed = "Orarul semestrului s-a schimbat după previzualizare. Verifică din nou diferențele."
        if (services.datasets.version(draft.semesterId) != version) {
            return@action call.respondRedirect(back("preview", changed))
        }
        try {
            services.datasets.replace(imported, makeCurrent = false, expectedVersion = version)
        } catch (_: IllegalStateException) {
            return@action call.respondRedirect(back("preview", changed))
        }
        services.importDrafts.delete(draft.id)
        val summary = diffSummary(check.diff)
        services.audit.record(
            admin,
            AuditCategory.TIMETABLE,
            "a importat orarul semestrului ${draft.semesterId} din ${draft.fileName}: $summary",
        )
        val msg = "Orarul a fost importat: $summary.".encodeURLParameter()
        call.respondRedirect("/admin/semesters/${draft.semesterId}?msg=$msg")
    }

    action("/semesters/{id}/import/{draft}/cancel", services) { admin ->
        val semesterId = call.parameters["id"].orEmpty()
        uuid(call.parameters["draft"])?.let { id ->
            if (services.importDrafts.get(id, admin.id, semesterId) != null) services.importDrafts.delete(id)
        }
        val msg = "Importul a fost anulat; orarul a rămas cum era.".encodeURLParameter()
        call.respondRedirect("/admin/semesters/$semesterId?msg=$msg")
    }
}

/** The semester and the draft in the path; a draft that is gone sends the admin back to the upload. */
private suspend fun RoutingContext.draftOrRedirect(services: Services, admin: Admin): Pair<Dataset, ImportDraft>? {
    val semesterId = call.parameters["id"].orEmpty()
    val dataset = services.datasets.dataset(semesterId)
    if (dataset == null) {
        call.respond(HttpStatusCode.NotFound)
        return null
    }
    val draft = uuid(call.parameters["draft"])?.let { services.importDrafts.get(it, admin.id, semesterId) }
    if (draft == null) {
        val err = "Fișierul nu mai este în import (se păstrează 24 de ore). Încarcă-l din nou.".encodeURLParameter()
        call.respondRedirect("/admin/semesters/$semesterId/import?err=$err")
        return null
    }
    return dataset to draft
}

private fun HTML.uploadPage(admin: Admin, dataset: Dataset, err: String?) {
    val semester = dataset.semester
    adminPage(
        "Import orar",
        admin,
        Section.SEMESTERS,
        context = { a(href = "/admin/semesters/${semester.id}") { +semester.name.ro } },
    ) {
        stepper(step = 1, draft = null, base = null)
        notice(err, error = true)
        form(
            action = "/admin/semesters/${semester.id}/import",
            method = FormMethod.post,
            encType = FormEncType.multipartFormData,
            classes = "card",
        ) {
            field("sheet", "Fișierul cu orarul") {
                fileInput(name = "sheet") {
                    id = "sheet"
                    accept = ".csv,text/csv,.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    required = true
                }
            }
            p("hint") {
                +"Un fișier CSV sau Excel (.xlsx, prima foaie) cu un rând pe activitate, "
                +"în formatul din ghidul de administrare. "
                +"Cel mai simplu: descarcă orarul actual, modifică-l și încarcă-l înapoi. "
                +"Înainte de salvare vezi coloanele găsite și tot ce se schimbă în orar."
            }
            div("form-actions") {
                button { +"Încarcă fișierul" }
                a(href = "/admin/semesters/${semester.id}/export.csv", classes = "btn secondary") {
                    +"Descarcă orarul actual (CSV)"
                }
            }
        }
        apiNote(admin)
    }
}

private fun FlowContent.apiNote(admin: Admin) {
    p("hint") {
        +"Importuri automate: un program poate trimite același fișier prin API, cu un token "
        if (admin.role == AdminRole.ADMIN) {
            +"creat la "
            a(href = "/admin/api-tokens") { +"Tokenuri API" }
        } else {
            +"pe care îl creează un administrator"
        }
        +". Trece prin aceeași verificare."
    }
}

/** The frame of steps 2 to 4: the file in the header, the steps, a way out. */
private fun HTML.wizardPage(
    admin: Admin,
    dataset: Dataset,
    draft: ImportDraft,
    rows: List<List<String>>,
    step: Int,
    content: FlowContent.() -> Unit,
) {
    val semester = dataset.semester
    val base = "/admin/semesters/${semester.id}/import/${draft.id}"
    val count = (rows.size - 1).coerceAtLeast(0)
    adminPage(
        "Import orar",
        admin,
        Section.SEMESTERS,
        context = {
            a(href = "/admin/semesters/${semester.id}") { +semester.name.ro }
            +" · ${draft.fileName} · $count ${plural(count, "rând citit", "rânduri citite")}"
        },
        actions = {
            form(action = "$base/cancel", method = FormMethod.post, classes = "inline") {
                button(classes = "secondary") { +"Renunță la import" }
            }
        },
    ) {
        stepper(step, draft, base)
        content()
    }
}

private fun FlowContent.stepper(step: Int, draft: ImportDraft?, base: String?) {
    ol("stepper") {
        attributes["aria-label"] = "Pașii importului"
        STEPS.forEachIndexed { index, (title, defaultHint) ->
            val n = index + 1
            val hint = when {
                n == 1 && draft != null -> "CSV, ${(draft.text.toByteArray().size + 1023) / 1024} KB"
                n == 2 && draft != null -> "${draft.mapping.size} din ${TimetableSheet.COLUMNS.size} potrivite"
                else -> defaultHint
            }
            li(if (n < step) "done" else null) {
                if (n == step) attributes["aria-current"] = "step"
                val href = when {
                    n >= step || base == null -> null
                    n == 1 -> base.substringBeforeLast("/")
                    n == 2 -> "$base/columns"
                    else -> "$base/preview"
                }
                val inner: FlowContent.() -> Unit = {
                    span("n tnum") {
                        if (n < step) unsafe { +svgIcon(ICON_CHECK, 14) } else +n.toString()
                    }
                    span("text") {
                        span("t") { +title }
                        span("s") { +hint }
                    }
                }
                if (href != null) a(href = href) { inner() } else div { inner() }
            }
        }
    }
}

private const val ICON_CHECK = "M5 12l5 5 9-10"

/** A, B, ... Z, AA: the column names spreadsheets show. */
internal fun columnLetter(index: Int): String {
    var n = index + 1
    val out = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        out.insert(0, 'A' + r)
        n = (n - 1) / 26
    }
    return out.toString()
}

private fun FlowContent.columnsStep(draft: ImportDraft, rows: List<List<String>>) {
    val header = rows.firstOrNull().orEmpty()
    val missing = ColumnMatch.missing(draft.mapping)
    val action = "/admin/semesters/${draft.semesterId}/import/${draft.id}/columns"
    if (missing.isEmpty()) {
        div("notice ready") {
            span {
                +"Am găsit după nume ${draft.mapping.size} din ${TimetableSheet.COLUMNS.size} coloane, "
                +"toate cele obligatorii. Dacă primele rânduri de mai jos arată bine, continuă."
            }
            form(action = action, method = FormMethod.post, classes = "inline") {
                hiddenInput(name = "keep") { value = "1" }
                button { +"Continuă la previzualizare" }
            }
        }
    } else {
        notice(
            "Nu am găsit ${plural(missing.size, "coloana obligatorie", "coloanele obligatorii")} " +
                "${missing.joinToString(", ")}. Alege-le din lista coloanelor din fișier.",
            error = true,
        )
    }
    form(action = action, method = FormMethod.post) {
        tableCard {
            table("mapping") {
                thead {
                    tr {
                        th { +"Coloana din orar" }
                        th { +"Ce conține" }
                        th { +"Coloana din fișier" }
                        th { +"Stare" }
                    }
                }
                tbody {
                    TimetableSheet.COLUMNS.forEach { column ->
                        val chosen = draft.mapping[column]
                        val optional = column in TimetableSheet.OPTIONAL
                        tr {
                            td { code { +column } }
                            td { +ColumnMatch.DESCRIPTIONS[column].orEmpty() }
                            td {
                                select {
                                    name = "col_$column"
                                    attributes["aria-label"] = "Coloana din fișier pentru $column"
                                    option {
                                        value = ""
                                        selected = chosen == null
                                        +"(nu este în fișier)"
                                    }
                                    header.forEachIndexed { i, name ->
                                        option {
                                            value = i.toString()
                                            selected = chosen == i
                                            +"${columnLetter(i)}: ${name.ifBlank { "(fără nume)" }}"
                                        }
                                    }
                                }
                            }
                            td {
                                when {
                                    chosen != null -> {
                                        span("dot ok")
                                        +"Potrivită"
                                    }
                                    optional -> {
                                        span("dot")
                                        +"Lipsește, opțională"
                                    }
                                    else -> {
                                        span("dot open")
                                        +"Lipsește, obligatorie"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val ignored = header.indices.filter { it !in draft.mapping.values }
        if (ignored.isNotEmpty()) {
            p("hint") {
                +"Nu se importă: "
                +ignored.joinToString(", ") { "${columnLetter(it)} (${header[it].ifBlank { "fără nume" }})" }
                +"."
            }
        }
        div("form-actions") {
            button { +"Salvează potrivirea și continuă" }
            a(href = "/admin/semesters/${draft.semesterId}/import", classes = "btn secondary") {
                +"Încarcă alt fișier"
            }
        }
    }
    h2 {
        +"Primele rânduri"
        span("count") { +minOf(SAMPLE_ROWS, rows.size - 1).coerceAtLeast(0).toString() }
    }
    val names = draft.mapping.entries.associate { (column, index) -> index to column }
    tableCard {
        table("sample") {
            thead {
                tr {
                    th(classes = "num") { +"Rând" }
                    header.forEachIndexed { i, name -> th { +"${columnLetter(i)}: $name" } }
                }
                tr("mapped") {
                    th { }
                    header.indices.forEach { i -> th { +(names[i] ?: "nu se importă") } }
                }
            }
            tbody {
                rows.drop(1).take(SAMPLE_ROWS).forEachIndexed { r, row ->
                    tr {
                        td("num tnum") { +(r + 2).toString() }
                        header.indices.forEach { i -> td { +row.getOrElse(i) { "" } } }
                    }
                }
            }
        }
    }
}

/** A row of the preview table: a class that changes, or a line of the file that could not be read. */
private sealed interface PreviewRow {
    class Change(val change: ClassChange) : PreviewRow
    class Error(val error: TimetableSheet.RowError) : PreviewRow
}

private val FILTERS = listOf(
    Triple("all", "Toate modificările", ""),
    Triple("added", "Adăugate", "added"),
    Triple("changed", "Modificate", "changed"),
    Triple("removed", "Eliminate", "removed"),
    Triple("errors", "Rânduri cu erori", "error"),
)

private fun FlowContent.previewStep(
    admin: Admin,
    draft: ImportDraft,
    check: ImportCheck,
    version: Long,
    query: Parameters,
) {
    val base = "/admin/semesters/${draft.semesterId}/import/${draft.id}"
    val lineErrors = check.errors.filter { it.line != null }
    check.errors.filter { it.line == null }.forEach { notice(it.message, error = true) }
    val all: List<PreviewRow> =
        (
            check.diff.changes.filter { it.line != null }.map { PreviewRow.Change(it) } +
                lineErrors.map { PreviewRow.Error(it) }
            ).sortedBy {
            when (it) {
                is PreviewRow.Change -> it.change.line
                is PreviewRow.Error -> it.error.line
            }
        } + check.diff.changes.filter { it.line == null }.map { PreviewRow.Change(it) }
    val filter = query["filter"]?.takeIf { f -> FILTERS.any { it.first == f } } ?: "all"
    val shown = all.filter {
        when (filter) {
            "all" -> true
            "errors" -> it is PreviewRow.Error
            else -> it is PreviewRow.Change && it.change.kind.key == filter
        }
    }
    val counts = mapOf(
        "all" to check.diff.changes.size + lineErrors.size,
        "added" to check.diff.count(ChangeKind.ADDED),
        "changed" to check.diff.count(ChangeKind.CHANGED),
        "removed" to check.diff.count(ChangeKind.REMOVED),
        "errors" to lineErrors.size,
    )
    val path = "$base/preview"
    val paging = query.paging(shown.size, PREVIEW_PAGE_SIZE)
    div("import-layout") {
        div("table-card diff") {
            nav("diff-filters") {
                attributes["aria-label"] = "Filtru după modificare"
                FILTERS.forEach { (key, label, dot) ->
                    a(
                        href = linkTo(path, query, mapOf("filter" to key.takeIf { it != "all" }, "page" to null)),
                        classes = if (key == filter) "active" else null,
                    ) {
                        if (key == filter) attributes["aria-current"] = "true"
                        span("label") {
                            span("dot $dot".trim())
                            +label
                        }
                        span("count tnum") { +counts.getValue(key).toString() }
                    }
                }
            }
            if (shown.isEmpty()) {
                emptyState(
                    if (filter == "all") "Fișierul nu schimbă nimic" else "Nimic în această categorie",
                    if (filter == "all") "Orarul importat este la fel cu cel de acum." else null,
                )
            } else {
                div("scroll") { diffTable(paging.slice(shown)) }
                pager(paging, path, query)
            }
        }
        div("import-side") {
            div("card") {
                h2 { +"Înainte să confirmi" }
                ul("todo") {
                    if (lineErrors.isNotEmpty()) {
                        li {
                            span("mark bad")
                            div {
                                +"${lineErrors.size} ${plural(lineErrors.size, "rând are erori", "rânduri au erori")}. "
                                +"Corectează-le în fișier și încarcă-l din nou; "
                                +"până atunci importul nu se poate confirma."
                            }
                        }
                    }
                    val removed = check.diff.count(ChangeKind.REMOVED)
                    if (removed > 0) {
                        li {
                            span("mark")
                            div {
                                +"$removed ${plural(removed, "activitate dispare", "activități dispar")} din orar. "
                                +"Studenții Erasmus care le aveau alese vor trebui să aleagă altele."
                            }
                        }
                    }
                    if (check.result.warnings.isNotEmpty()) {
                        li {
                            span("mark")
                            div {
                                val n = check.result.warnings.size
                                +"$n ${plural(n, "suprapunere", "suprapuneri")} de săli sau profesori; "
                                +"se importă oricum."
                                details {
                                    summary { +"Vezi lista" }
                                    ul("problems") { check.result.warnings.forEach { li { +it } } }
                                }
                            }
                        }
                    }
                    li {
                        span("mark ok")
                        div {
                            val n = check.diff.unchanged
                            +"$n ${plural(n, "activitate rămâne neschimbată", "activități rămân neschimbate")}."
                        }
                    }
                }
                div("form-actions") {
                    a(href = "$base/columns", classes = "btn secondary") { +"Înapoi la coloane" }
                    if (check.canSave) {
                        a(href = "$base/confirm?version=$version", classes = "btn") { +"Continuă la confirmare" }
                    } else {
                        a(href = "/admin/semesters/${draft.semesterId}/import", classes = "btn") {
                            +"Încarcă fișierul corectat"
                        }
                    }
                }
            }
            apiNote(admin)
        }
    }
}

private fun FlowContent.diffTable(rows: List<PreviewRow>) {
    table {
        thead {
            tr {
                th(classes = "num") { +"Rând" }
                th { +"Modificare" }
                th { +"Activitate" }
                th { +"Grupe" }
                th { +"Zi și oră" }
                th { +"Sală" }
            }
        }
        tbody {
            rows.forEach { row ->
                when (row) {
                    is PreviewRow.Error -> tr("error-row") {
                        td("num tnum") { +row.error.line.toString() }
                        td { span("kind error") { +"Eroare" } }
                        td {
                            colSpan = "4"
                            attributes["role"] = "alert"
                            +"Rândul ${row.error.line}: ${row.error.message}"
                        }
                    }
                    is PreviewRow.Change -> changeRow(row.change)
                }
            }
        }
    }
}

private fun TBODY.changeRow(change: ClassChange) {
    tr(change.kind.key) {
        td("num tnum") { +(change.line?.toString() ?: "-") }
        td { span("kind ${change.kind.key}") { +change.kind.label } }
        td {
            div("subject") { fieldValue(change, "subject") }
            div("hint") {
                fieldValue(change, "type")
                +" · "
                fieldValue(change, "weeks")
                +" · "
                fieldValue(change, "professor", empty = "fără profesor")
                val erasmusChanged = change.kind == ChangeKind.CHANGED && "erasmus" in change.changedFields
                if (change.value("erasmus") == "da" || erasmusChanged) {
                    +" · Erasmus: "
                    fieldValue(change, "erasmus")
                }
            }
        }
        td { fieldValue(change, "audience") }
        td("tnum when") { fieldValue(change, "time") }
        td { fieldValue(change, "room", empty = "fără sală") }
    }
}

/** The new value, after the old one struck through when the field changed. */
private fun FlowContent.fieldValue(change: ClassChange, field: String, empty: String = "-") {
    if (change.kind == ChangeKind.CHANGED && field in change.changedFields) {
        s { +(change.before?.get(field).orEmpty().ifEmpty { empty }) }
        +" "
        mark { +(change.after?.get(field).orEmpty().ifEmpty { empty }) }
    } else {
        span("v") { +change.value(field).ifEmpty { empty } }
    }
}

private fun FlowContent.confirmStep(
    dataset: Dataset,
    draft: ImportDraft,
    check: ImportCheck,
    version: Long,
    stale: Boolean,
    isCurrent: Boolean,
) {
    val base = "/admin/semesters/${draft.semesterId}/import/${draft.id}"
    if (stale) {
        div("notice error") {
            +"Orarul semestrului s-a schimbat după ce ai deschis previzualizarea, de exemplu dintr-o editare în panou. "
            +"Verifică din nou diferențele. "
            a(href = "$base/preview") { +"Înapoi la previzualizare" }
        }
        return
    }
    form(action = "$base/confirm", method = FormMethod.post, classes = "card confirm") {
        hiddenInput(name = "version") { value = version.toString() }
        p { +"Importul înlocuiește tot orarul semestrului ${dataset.semester.name.ro}:" }
        val added = check.diff.count(ChangeKind.ADDED)
        val changed = check.diff.count(ChangeKind.CHANGED)
        val removed = check.diff.count(ChangeKind.REMOVED)
        ul("todo") {
            li {
                span("mark ok")
                div { +"$added ${plural(added, "activitate nouă", "activități noi")}" }
            }
            li {
                span("mark")
                div { +"$changed ${plural(changed, "activitate modificată", "activități modificate")}" }
            }
            li {
                span("mark bad")
                div { +"$removed ${plural(removed, "activitate eliminată", "activități eliminate")}" }
            }
            li {
                span("mark ok")
                div {
                    val n = check.diff.unchanged
                    +"$n ${plural(n, "activitate neschimbată", "activități neschimbate")}"
                }
            }
        }
        p("hint") {
            +if (isCurrent) {
                "Aplicațiile primesc orarul nou la următoarea sincronizare."
            } else {
                "Semestrul nu este curent: studenții îl văd după ce îl faci curent."
            }
        }
        label("check") {
            checkBoxInput(name = "checked") { required = true }
            +"Am verificat diferențele"
        }
        div("form-actions") {
            button { +"Importă orarul" }
            a(href = "$base/preview", classes = "btn secondary") { +"Înapoi la previzualizare" }
        }
    }
}

internal val IMPORT_STYLES = """
.stepper { display: flex; flex-wrap: wrap; list-style: none; margin: 0 0 20px; padding: 0; background: var(--surface);
  border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.stepper li { flex: 1 1 180px; border-right: 1px solid var(--border); }
.stepper li:last-child { border-right: 0; }
.stepper li > a, .stepper li > div { display: flex; gap: 10px; align-items: center; padding: 12px 16px; height: 100%;
  color: var(--text); text-decoration: none; }
.stepper li > a:hover { background: var(--alt); }
.stepper li[aria-current] { box-shadow: inset 0 -2px 0 var(--primary); }
.stepper .n { flex: none; display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px;
  border-radius: 50%; border: 1px solid var(--strong); color: var(--muted); font-size: 13px; font-weight: 600; }
.stepper .done .n { background: var(--ok); border-color: var(--ok); color: var(--surface); }
.stepper li[aria-current] .n { background: var(--primary); border-color: var(--primary); color: var(--surface); }
.stepper .t { display: block; font-size: 14px; line-height: 20px; font-weight: 600; }
.stepper .s { display: block; font-size: 12px; line-height: 16px; color: var(--muted); }
.notice.ready { display: flex; flex-wrap: wrap; gap: 10px 16px; align-items: center; justify-content: space-between;
  border-left-color: var(--ok); }
table.mapping td { vertical-align: middle; }
table.mapping select { width: auto; min-width: 220px; padding: 5px 8px; font-size: 13px; }
table.mapping code { font-size: 13px; }
table.sample td, table.sample th { white-space: nowrap; font-size: 13px; }
table.sample tr.mapped th { text-transform: none; letter-spacing: 0; font-weight: 500; color: var(--primary); background: var(--surface); }
.import-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
.import-side .card h2 { font-size: 16px; margin: 0 0 12px; }
.import-side ul.todo details { margin-top: 6px; font-size: 13px; }
.diff-filters { display: flex; flex-wrap: wrap; border-bottom: 1px solid var(--border); }
.diff-filters a { flex: 1 1 120px; display: flex; flex-direction: column; gap: 2px; padding: 12px 18px 10px;
  border-right: 1px solid var(--border); color: var(--text); text-decoration: none; transition: background-color 150ms; }
.diff-filters a:last-child { border-right: 0; }
.diff-filters a:hover { background: var(--alt); }
.diff-filters a.active { background: var(--primary-soft); box-shadow: inset 0 -2px 0 var(--primary); }
.diff-filters .label { font-size: 13px; line-height: 18px; color: var(--muted); }
.diff-filters .count { font-size: 22px; line-height: 28px; font-weight: 600; }
.dot.added { background: var(--ok); } .dot.changed { background: var(--change); }
.dot.removed { background: var(--now); } .dot.error { background: var(--now); border-radius: 2px; }
.kind { display: inline-block; font-size: 12px; line-height: 16px; font-weight: 600; border-radius: 4px; padding: 2px 7px;
  border: 1px solid transparent; white-space: nowrap; }
.kind.added { border-color: var(--ok); color: var(--ok); }
.kind.changed { background: var(--change); border-color: var(--change); color: #292929; }
.kind.removed { border: 1px dashed var(--now); color: var(--now); }
.kind.error { background: var(--now); border-color: var(--now); color: var(--surface); }
.diff .subject { font-weight: 600; }
.diff s { color: var(--muted); }
.diff mark { background: var(--change); color: #292929; border-radius: 4px; padding: 0 5px; font-weight: 600; }
.diff tr.removed .v { text-decoration: line-through; color: var(--muted); }
.diff tr.error-row td { background: var(--error-soft); color: var(--error-text); }
form.confirm > p:first-of-type { margin-top: 0; }
form.confirm ul.todo { margin: 0 0 12px; }
form.confirm label.check { margin-top: 16px; }
.diff td.when { white-space: nowrap; }
pre.usage { background: var(--alt); border: 1px solid var(--border); border-radius: 6px; padding: 12px 14px; margin: 12px 0 0;
  overflow-x: auto; font: 13px/1.5 ui-monospace, Menlo, Consolas, monospace; }
@media (max-width: 1100px) { .import-layout { grid-template-columns: 1fr; } }
"""
