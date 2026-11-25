package io.github.hdlee73.insu.data.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * docx·xlsx는 XML 파일 몇 개를 zip으로 묶은 형식이다.
 * Apache POI는 안드로이드에서 수 MB가 늘고 느려서, 필요한 최소 구조만 직접 쓴다.
 * 순수 Kotlin(java.util.zip)이라 JVM 단위 테스트로 결과 파일을 검증할 수 있다.
 */

internal object Xml {
    private val invalid = Regex("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]")

    fun esc(s: String): String {
        val clean = invalid.replace(s, "")
        val sb = StringBuilder(clean.length + 16)
        for (c in clean) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}

internal fun writeZip(out: OutputStream, entries: List<Pair<String, String>>) {
    val zip = ZipOutputStream(out)
    for ((name, content) in entries) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    zip.finish()
    zip.flush()
}

private const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

// =====================================================================
// XLSX
// =====================================================================

object XlsxWriter {

    enum class CellType { TEXT, NUMBER }

    data class Column(val title: String, val width: Int, val type: CellType = CellType.TEXT)

    data class Sheet(val name: String, val columns: List<Column>, val rows: List<List<Any?>>)

    fun write(out: OutputStream, sheets: List<Sheet>) {
        require(sheets.isNotEmpty())
        val entries = ArrayList<Pair<String, String>>()
        entries += "[Content_Types].xml" to contentTypes(sheets.size)
        entries += "_rels/.rels" to """$XML_HEAD<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        entries += "xl/workbook.xml" to workbook(sheets)
        entries += "xl/_rels/workbook.xml.rels" to workbookRels(sheets.size)
        entries += "xl/styles.xml" to STYLES
        sheets.forEachIndexed { i, s -> entries += "xl/worksheets/sheet${i + 1}.xml" to sheetXml(s) }
        writeZip(out, entries)
    }

    private fun contentTypes(n: Int) = buildString {
        append(XML_HEAD)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private fun workbook(sheets: List<Sheet>) = buildString {
        append(XML_HEAD)
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s ->
            val name = Xml.esc(s.name.replace(Regex("""[\\/?*\[\]:]"""), " ").take(31))
            append("""<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append(XML_HEAD)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    /** 스타일: 0 기본, 1 머리글(굵게·하늘색), 2 숫자(#,##0), 3 텍스트(@) */
    private val STYLES = XML_HEAD +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="2"><font><sz val="11"/><name val="맑은 고딕"/></font><font><b/><sz val="11"/><name val="맑은 고딕"/></font></fonts>""" +
        """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FFDCEBFC"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
        """<xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun sheetXml(s: Sheet) = buildString {
        append(XML_HEAD)
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        s.columns.forEachIndexed { i, c -> append("""<col min="${i + 1}" max="${i + 1}" width="${c.width}" customWidth="1"/>""") }
        append("</cols><sheetData>")
        append("""<row r="1">""")
        s.columns.forEachIndexed { i, c -> append(textCell("${columnName(i)}1", c.title, 1)) }
        append("</row>")
        s.rows.forEachIndexed { ri, row ->
            val r = ri + 2
            append("""<row r="$r">""")
            s.columns.forEachIndexed { ci, col ->
                val ref = "${columnName(ci)}$r"
                when (val v = row.getOrNull(ci)) {
                    null -> Unit
                    is Number -> if (col.type == CellType.NUMBER) append("""<c r="$ref" s="2"><v>$v</v></c>""") else append(textCell(ref, v.toString(), 3))
                    else -> append(textCell(ref, v.toString(), 3))
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun textCell(ref: String, text: String, style: Int) =
        """<c r="$ref" t="inlineStr" s="$style"><is><t xml:space="preserve">${Xml.esc(text)}</t></is></c>"""
}

// =====================================================================
// DOCX
// =====================================================================

/** 문단·표·쪽 나눔만 지원하는 최소 docx 작성기. */
class DocxBuilder {
    private val body = StringBuilder()

    fun heading(text: String, sizeHalfPt: Int = 32, color: String = "1A5FB4") = apply {
        body.append("""<w:p><w:pPr><w:spacing w:after="120"/></w:pPr><w:r><w:rPr><w:b/><w:color w:val="$color"/><w:sz w:val="$sizeHalfPt"/><w:szCs w:val="$sizeHalfPt"/></w:rPr>${run(text)}</w:r></w:p>""")
    }

    fun paragraph(text: String, sizeHalfPt: Int = 20, color: String = "56627A") = apply {
        body.append("""<w:p><w:pPr><w:spacing w:after="80"/></w:pPr><w:r><w:rPr><w:color w:val="$color"/><w:sz w:val="$sizeHalfPt"/><w:szCs w:val="$sizeHalfPt"/></w:rPr>${run(text)}</w:r></w:p>""")
    }

    /** 2열 이상 표. [header]가 있으면 첫 행을 머리글로 칠한다. [labelColumn]이면 첫 열을 라벨처럼 칠한다. */
    fun table(widthsTwips: List<Int>, rows: List<List<String>>, header: List<String>? = null, labelColumn: Boolean = false) = apply {
        val border = """w:val="single" w:sz="4" w:space="0" w:color="A3AAB5""""
        body.append("<w:tbl><w:tblPr>")
        body.append("""<w:tblW w:w="${widthsTwips.sum()}" w:type="dxa"/>""")
        body.append("<w:tblBorders><w:top $border/><w:left $border/><w:bottom $border/><w:right $border/><w:insideH $border/><w:insideV $border/></w:tblBorders>")
        body.append("""<w:tblCellMar><w:top w:w="60" w:type="dxa"/><w:left w:w="100" w:type="dxa"/><w:bottom w:w="60" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tblCellMar>""")
        body.append("</w:tblPr><w:tblGrid>")
        widthsTwips.forEach { body.append("""<w:gridCol w:w="$it"/>""") }
        body.append("</w:tblGrid>")
        header?.let { tableRow(widthsTwips, it) { true } }
        rows.forEach { r -> tableRow(widthsTwips, r) { col -> labelColumn && col == 0 } }
        body.append("</w:tbl>")
        body.append("""<w:p><w:pPr><w:spacing w:after="120"/></w:pPr></w:p>""")
    }

    private fun tableRow(widths: List<Int>, cells: List<String>, shaded: (Int) -> Boolean) {
        body.append("<w:tr>")
        widths.forEachIndexed { i, w ->
            val shade = shaded(i)
            body.append("""<w:tc><w:tcPr><w:tcW w:w="$w" w:type="dxa"/>""")
            if (shade) body.append("""<w:shd w:val="clear" w:color="auto" w:fill="EAF2FB"/>""")
            body.append("</w:tcPr><w:p><w:r>")
            if (shade) body.append("<w:rPr><w:b/></w:rPr>")
            body.append(run(cells.getOrElse(i) { "" }))
            body.append("</w:r></w:p></w:tc>")
        }
        body.append("</w:tr>")
    }

    fun pageBreak() = apply { body.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""") }

    private fun run(text: String): String =
        text.split('\n').joinToString("<w:br/>") { """<w:t xml:space="preserve">${Xml.esc(it)}</w:t>""" }

    fun write(out: OutputStream) {
        val doc = XML_HEAD +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>""" +
            body +
            """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1300" w:right="1100" w:bottom="1300" w:left="1100" w:header="600" w:footer="600" w:gutter="0"/></w:sectPr></w:body></w:document>"""
        writeZip(
            out,
            listOf(
                "[Content_Types].xml" to XML_HEAD +
                    """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                    """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                    """<Default Extension="xml" ContentType="application/xml"/>""" +
                    """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
                    """<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""" +
                    "</Types>",
                "_rels/.rels" to XML_HEAD +
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
                "word/_rels/document.xml.rels" to XML_HEAD +
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
                "word/styles.xml" to XML_HEAD +
                    """<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr>""" +
                    """<w:rFonts w:ascii="Malgun Gothic" w:eastAsia="맑은 고딕" w:hAnsi="Malgun Gothic" w:cs="Malgun Gothic"/><w:sz w:val="20"/><w:szCs w:val="20"/><w:lang w:val="ko-KR" w:eastAsia="ko-KR"/>""" +
                    """</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""" +
                    """<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style></w:styles>""",
                "word/document.xml" to doc,
            ),
        )
    }
}
