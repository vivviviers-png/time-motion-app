package za.co.galvatech.timemotion.export

import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal .xlsx writer (no external library). Numbers are stored as real numbers,
 * so Excel sums and charts them regardless of the PC's regional settings.
 */
class XlsxWriter {
    private val sheets = mutableListOf<Pair<String, List<List<Any?>>>>()

    fun sheet(name: String, rows: List<List<Any?>>) {
        sheets += name.take(31) to rows
    }

    fun write(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { z ->
            fun put(path: String, xml: String) {
                z.putNextEntry(ZipEntry(path))
                z.write(xml.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            val head = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
            put(
                "[Content_Types].xml",
                head + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                    """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                    """<Default Extension="xml" ContentType="application/xml"/>""" +
                    """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                    """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                    sheets.indices.joinToString("") {
                        """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
                    } + "</Types>"
            )
            put(
                "_rels/.rels",
                head + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                    "</Relationships>"
            )
            put(
                "xl/workbook.xml",
                head + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                    sheets.mapIndexed { i, s ->
                        """<sheet name="${esc(s.first)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
                    }.joinToString("") + "</sheets></workbook>"
            )
            put(
                "xl/_rels/workbook.xml.rels",
                head + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    sheets.indices.joinToString("") {
                        """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>"""
                    } +
                    """<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
                    "</Relationships>"
            )
            put(
                "xl/styles.xml",
                head + """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
                    """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
                    """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
                    """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
                    """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
                    """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>""" +
                    "</styleSheet>"
            )
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s.second)) }
        }
    }

    private fun sheetXml(rows: List<List<Any?>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="1" width="24" customWidth="1"/><col min="2" max="12" width="14" customWidth="1"/></cols>""")
        sb.append("<sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, v ->
                val ref = colName(c) + (r + 1)
                when (v) {
                    null -> {}
                    is Bold -> sb.append("""<c r="$ref" t="inlineStr" s="1"><is><t xml:space="preserve">${esc(v.text)}</t></is></c>""")
                    is Int, is Long -> sb.append("""<c r="$ref"><v>$v</v></c>""")
                    is Number -> sb.append("""<c r="$ref"><v>${String.format(Locale.US, "%.2f", v.toDouble())}</v></c>""")
                    else -> sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    data class Bold(val text: String)

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val m = (n - 1) % 26
            sb.insert(0, ('A' + m))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
