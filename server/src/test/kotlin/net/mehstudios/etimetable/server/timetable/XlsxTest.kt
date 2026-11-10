// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.timetable

import net.mehstudios.etimetable.server.sampleDataset
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Builds small .xlsx files by hand, the way Excel and LibreOffice lay them out. */
internal object TestWorkbook {
    fun zip(parts: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** One sheet of inline strings, one row per list. */
    fun ofRows(rows: List<List<String>>): ByteArray = zip(
        mapOf(
            "xl/workbook.xml" to WORKBOOK,
            "xl/_rels/workbook.xml.rels" to RELS,
            "xl/worksheets/sheet1.xml" to sheet(
                rows.mapIndexed { r, cells ->
                    cells.mapIndexed { c, text ->
                        """<c r="${col(
                            c,
                        )}${r + 1}" t="inlineStr"><is><t xml:space="preserve">${escape(text)}</t></is></c>"""
                    }.joinToString("", "<row r=\"${r + 1}\">", "</row>")
                }.joinToString(""),
            ),
        ),
    )

    fun sheet(rows: String) = """<?xml version="1.0" encoding="UTF-8"?>""" +
        """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        "<sheetData>$rows</sheetData></worksheet>"

    fun col(i: Int): String = if (i < 26) ('A' + i).toString() else col(i / 26 - 1) + ('A' + i % 26)

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    const val WORKBOOK =
        """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets><sheet name="Orar" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    const val RELS =
        """<?xml version="1.0"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
}

class XlsxTest {
    @Test
    fun readsStringsNumbersTimesAndDatesLikeTheSheetShowsThem() {
        val bytes = TestWorkbook.zip(
            mapOf(
                "xl/workbook.xml" to TestWorkbook.WORKBOOK.replace("rId1", "rId7"),
                // the first sheet is not sheet1.xml: the workbook's relationships decide
                "xl/_rels/workbook.xml.rels" to TestWorkbook.RELS.replace("rId1", "rId7").replace("sheet1", "orar"),
                "xl/sharedStrings.xml" to
                    """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
                    """<si><t>Microeconomie</t></si>""" +
                    """<si><r><t>Luni</t></r><r><t xml:space="preserve"> </t></r><rPh><t>x</t></rPh></si></sst>""",
                "xl/styles.xml" to
                    """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
                    """<numFmts><numFmt numFmtId="164" formatCode="hh&quot;h&quot;mm"/></numFmts>""" +
                    """<cellXfs><xf numFmtId="0"/><xf numFmtId="20"/><xf numFmtId="164"/><xf numFmtId="14"/></cellXfs></styleSheet>""",
                "xl/worksheets/orar.xml" to TestWorkbook.sheet(
                    """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>""" +
                        """<row r="3"><c r="A3" s="1"><v>0.375</v></c><c r="B3" s="2"><v>0.5833333333</v></c>""" +
                        """<c r="C3" s="3"><v>46336</v></c><c r="D3"><v>1521</v></c><c r="E3"><v>2.5</v></c>""" +
                        """<c r="F3" t="inlineStr"><is><t>BN 030</t></is></c><c r="G3" t="b"><v>1</v></c></row>""",
                ),
                "xl/worksheets/sheet1.xml" to
                    TestWorkbook.sheet("""<row r="1"><c r="A1" t="inlineStr"><is><t>nu</t></is></c></row>"""),
            ),
        )
        assertEquals(
            listOf(
                listOf("Microeconomie", "", "Luni ", "", "", "", ""),
                listOf("09:00", "14:00", "2026-11-10", "1521", "2.5", "BN 030", "TRUE"),
            ),
            Xlsx.firstSheet(bytes),
        )
    }

    @Test
    fun theExportedTimetableSurvivesAWorkbook() {
        val rows = Csv.parse(TimetableSheet.export(sampleDataset()))
        assertEquals(rows, Xlsx.firstSheet(TestWorkbook.ofRows(rows)))
        assertEquals(rows, Csv.parse(SheetFile.text(TestWorkbook.ofRows(rows))))
        // a CSV passes through unchanged
        assertEquals("a,b\r\n", SheetFile.text("a,b\r\n".toByteArray()))
    }

    @Test
    fun refusesEntitiesBombsAndBrokenFiles() {
        val entity = TestWorkbook.zip(
            mapOf(
                "xl/workbook.xml" to TestWorkbook.WORKBOOK,
                "xl/_rels/workbook.xml.rels" to TestWorkbook.RELS,
                "xl/worksheets/sheet1.xml" to
                    """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>""" +
                    """<worksheet><sheetData><row><c t="inlineStr"><is><t>&e;</t></is></c></row></sheetData></worksheet>""",
            ),
        )
        assertFailsWith<SheetFileError> { Xlsx.firstSheet(entity) }

        // 30 MB of spaces packs into a few kilobytes
        val bomb = TestWorkbook.zip(mapOf("xl/worksheets/sheet1.xml" to " ".repeat(30 * 1024 * 1024)))
        assertTrue(bomb.size < 200_000)
        assertFailsWith<SheetFileError> { Xlsx.firstSheet(bomb) }

        val broken = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 1, 2, 3, 4, 5)
        assertFailsWith<SheetFileError> { SheetFile.text(broken) }
    }
}
