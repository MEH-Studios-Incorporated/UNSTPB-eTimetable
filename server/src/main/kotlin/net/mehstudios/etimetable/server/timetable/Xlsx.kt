// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.timetable

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Thrown with a message for the person who uploaded the file, in Romanian. */
class SheetFileError(message: String) : RuntimeException(message)

/**
 * The text of an uploaded timetable: a CSV as it is, or the first sheet of an Excel
 * workbook (.xlsx) written out as CSV, so the rest of the import reads one format.
 */
object SheetFile {
    fun text(bytes: ByteArray): String = if (Xlsx.isXlsx(bytes)) Xlsx.firstSheetAsCsv(bytes) else bytes.decodeToString()
}

/**
 * A small reader for the first worksheet of an .xlsx file, with the JDK only. It reads
 * cell values, shared and inline strings, and turns cells formatted as times or dates
 * into "09:00" and "2026-11-10", as the timetable's CSV writes them.
 */
object Xlsx {
    /** Every part of a timetable workbook together stays far below this. */
    private const val MAX_PART_BYTES = 20L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 60L * 1024 * 1024
    private const val MAX_PARTS = 2000
    private const val MAX_ROWS = 20_000
    private const val MAX_COLUMNS = 200

    /** A zip file starts with "PK" and 3, 4; an .xlsx is one. */
    fun isXlsx(bytes: ByteArray): Boolean {
        val magic = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)
        return bytes.size > 4 && bytes.copyOfRange(0, 4).contentEquals(magic)
    }

    fun firstSheetAsCsv(bytes: ByteArray): String = Csv.write(firstSheet(bytes))

    fun firstSheet(bytes: ByteArray): List<List<String>> {
        val parts = unzip(bytes)
        val sheetPath = firstSheetPath(parts)
        val sheet = parts[sheetPath] ?: throw SheetFileError("Fișierul Excel nu are nicio foaie de calcul.")
        val strings = parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val formats = parts["xl/styles.xml"]?.let(::cellFormats).orEmpty()
        return rows(sheet, strings, formats)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val wanted =
            Regex(
                "xl/(workbook\\.xml|_rels/workbook\\.xml\\.rels|sharedStrings\\.xml|styles\\.xml|worksheets/[^/]+\\.xml)",
            )
        val out = mutableMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_PARTS) throw SheetFileError("Fișierul Excel are prea multe părți.")
                    val name = entry.name.removePrefix("/")
                    if (entry.isDirectory || !wanted.matches(name)) continue
                    val part = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        part.write(buffer, 0, n)
                        total += n
                        // checked while reading, so a small file that unpacks to gigabytes stops early
                        if (part.size() > MAX_PART_BYTES || total > MAX_TOTAL_BYTES) {
                            throw SheetFileError("Fișierul Excel este prea mare după dezarhivare.")
                        }
                    }
                    out[name] = part.toByteArray()
                }
            }
        } catch (e: ZipException) {
            throw SheetFileError("Fișierul nu este un Excel (.xlsx) valid.")
        }
        if (out.isEmpty()) throw SheetFileError("Fișierul nu este un Excel (.xlsx) valid.")
        return out
    }

    /** The first sheet in the workbook's own order, through its relationship id. */
    private fun firstSheetPath(parts: Map<String, ByteArray>): String {
        val workbook = parts["xl/workbook.xml"] ?: return "xl/worksheets/sheet1.xml"
        var relId: String? = null
        read(workbook) { r ->
            if (relId == null && r.localName == "sheet") {
                relId = (0 until r.attributeCount).firstOrNull { r.getAttributeLocalName(it) == "id" }
                    ?.let { r.getAttributeValue(it) }
            }
        }
        val rels = parts["xl/_rels/workbook.xml.rels"] ?: return "xl/worksheets/sheet1.xml"
        var target: String? = null
        read(rels) { r ->
            if (r.localName == "Relationship" && r.getAttributeValue(null, "Id") == relId) {
                target = r.getAttributeValue(null, "Target")
            }
        }
        val path = target ?: return "xl/worksheets/sheet1.xml"
        return if (path.startsWith("/")) path.removePrefix("/") else "xl/" + path.removePrefix("./")
    }

    private fun sharedStrings(xml: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inItem = false
        // phonetic hints (rPh) carry their own <t>, which is not part of the text
        var inPhonetic = false
        readAll(xml) { r, event ->
            if (event == XMLStreamConstants.START_ELEMENT) {
                when (r.localName) {
                    "si" -> {
                        inItem = true
                        current.clear()
                    }
                    "rPh" -> inPhonetic = true
                    "t" -> if (inItem && !inPhonetic) current.append(r.elementText)
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                when (r.localName) {
                    "rPh" -> inPhonetic = false
                    "si" -> {
                        inItem = false
                        out += current.toString()
                    }
                }
            }
        }
        return out
    }

    /** For each cell style index, whether it shows a time ("t"), a date ("d") or neither. */
    private fun cellFormats(xml: ByteArray): List<Char?> {
        val custom = mutableMapOf<Int, String>()
        val xfs = mutableListOf<Int>()
        var inCellXfs = false
        readAll(xml) { r, event ->
            if (event == XMLStreamConstants.START_ELEMENT) {
                when (r.localName) {
                    "numFmt" -> r.getAttributeValue(null, "numFmtId")?.toIntOrNull()?.let {
                        custom[it] = r.getAttributeValue(null, "formatCode").orEmpty()
                    }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) xfs += r.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && r.localName == "cellXfs") {
                inCellXfs = false
            }
        }
        return xfs.map { id -> kindOf(id, custom[id]) }
    }

    private fun kindOf(id: Int, code: String?): Char? {
        if (id in 18..21 || id in 45..47) return 't'
        if (id in 14..17 || id == 22) return 'd'
        // a custom format: drop quoted text and colours, then look for date and time parts
        val clean = code?.lowercase()?.replace(Regex("\"[^\"]*\"|\\[[^]]*]"), "") ?: return null
        val date = 'y' in clean || 'd' in clean
        val time = 'h' in clean || 's' in clean
        return when {
            date -> 'd'
            time -> 't'
            else -> null
        }
    }

    private fun rows(xml: ByteArray, strings: List<String>, formats: List<Char?>): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var rowIndex = 0
        var ref: String? = null
        var type: String? = null
        var style = 0
        var value: String? = null
        var columnInRow = 0
        readAll(xml) { r, event ->
            if (event == XMLStreamConstants.START_ELEMENT) {
                when (r.localName) {
                    "row" -> {
                        rowIndex = r.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowIndex + 1)
                        columnInRow = 0
                        if (rowIndex > MAX_ROWS) throw SheetFileError("Foaia are mai mult de $MAX_ROWS de rânduri.")
                    }
                    "c" -> {
                        ref = r.getAttributeValue(null, "r")
                        type = r.getAttributeValue(null, "t")
                        style = r.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                        value = null
                    }
                    "v" -> value = r.elementText
                    "t" -> if (type == "inlineStr") value = (value ?: "") + r.elementText
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && r.localName == "c") {
                val column = ref?.let(::columnOf) ?: columnInRow
                columnInRow = column + 1
                if (column >= MAX_COLUMNS) throw SheetFileError("Foaia are mai mult de $MAX_COLUMNS de coloane.")
                val text = cellText(value, type, formats.getOrNull(style), strings)
                if (text.isNotEmpty()) rows.getOrPut(rowIndex) { mutableMapOf() }[column] = text
            }
        }
        if (rows.isEmpty()) return emptyList()
        val width = rows.values.maxOf { it.keys.max() } + 1
        // rows in sheet order without gaps: an empty row in the sheet is not a timetable row
        return rows.values.map { cells -> List(width) { cells[it].orEmpty() } }
    }

    private fun cellText(value: String?, type: String?, kind: Char?, strings: List<String>): String {
        val raw = value ?: return ""
        return when (type) {
            "s" -> strings.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty()
            "inlineStr", "str" -> raw
            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
            "e" -> ""
            else -> number(raw, kind)
        }
    }

    /** A number as the person saw it: a time, a date, a whole number, or as written. */
    private fun number(raw: String, kind: Char?): String {
        val n = raw.trim().toDoubleOrNull() ?: return raw.trim()
        return when (kind) {
            't' -> {
                val minutes = ((n - floor(n)) * 24 * 60).roundToLong() % (24 * 60)
                "%02d:%02d".format(minutes / 60, minutes % 60)
            }
            // Excel counts days from 1899-12-30 in the 1900 system
            'd' -> java.time.LocalDate.of(1899, 12, 30).plusDays(floor(n).toLong()).toString()
            else -> if (n == floor(n) && abs(n) < 1e15) n.toLong().toString() else raw.trim()
        }
    }

    /** "B12" to 1, "AA3" to 26. */
    private fun columnOf(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    private val factory: XMLInputFactory = XMLInputFactory.newFactory().apply {
        // no DTDs and no external entities: an uploaded file must not make the server read anything
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }

    private fun read(xml: ByteArray, onStart: (XMLStreamReader) -> Unit) =
        readAll(xml) { r, event -> if (event == XMLStreamConstants.START_ELEMENT) onStart(r) }

    private fun readAll(xml: ByteArray, onEvent: (XMLStreamReader, Int) -> Unit) {
        try {
            val r = factory.createXMLStreamReader(ByteArrayInputStream(xml))
            try {
                while (r.hasNext()) {
                    val event = r.next()
                    if (event == XMLStreamConstants.DTD) throw SheetFileError("Fișierul Excel nu este valid.")
                    onEvent(r, event)
                }
            } finally {
                r.close()
            }
        } catch (e: XMLStreamException) {
            throw SheetFileError("Fișierul Excel nu este valid.")
        }
    }
}
