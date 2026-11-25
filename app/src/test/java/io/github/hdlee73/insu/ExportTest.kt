package io.github.hdlee73.insu

import io.github.hdlee73.insu.data.export.ExportFormat
import io.github.hdlee73.insu.data.export.PolicyExporter
import io.github.hdlee73.insu.data.export.XlsxWriter
import io.github.hdlee73.insu.domain.model.Coverage
import io.github.hdlee73.insu.domain.model.Party
import io.github.hdlee73.insu.domain.model.PartyType
import io.github.hdlee73.insu.domain.model.PaymentCycle
import io.github.hdlee73.insu.domain.model.Policy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExportTest {

    private val sample = listOf(
        Policy(
            id = 1, insurer = "삼성화재", productName = "재산종합보험 <특약> & 기타",
            contractor = Party(PartyType.CORPORATION, "주식회사 한빛물산", "1234567891"),
            insured = Party(PartyType.INDIVIDUAL, "홍길동", "9001011234567"),
            sumInsured = 5_000_000_000L, premium = 3_200_000L, paymentCycle = PaymentCycle.YEARLY,
            startDate = "2026-01-01", endDate = "2027-01-01",
            coverages = listOf(Coverage("화재손해", 4_000_000_000L), Coverage("배상책임", 1_000_000_000L)),
        ),
        Policy(id = 2, contractor = Party(PartyType.INDIVIDUAL, "김서연", "9505052234567"), insured = Party(PartyType.INDIVIDUAL, "김서연", "9505052234567")),
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out[e.name] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        return out
    }

    private fun assertWellFormed(xml: String) {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    }

    @Test
    fun xlsxIsWellFormedAndMasksIds() {
        val bos = ByteArrayOutputStream()
        PolicyExporter.write(ExportFormat.XLSX, sample, maskIds = true, out = bos, generatedAt = "2026-11-25")
        val files = unzip(bos.toByteArray())
        assertTrue("[Content_Types].xml" in files)
        files.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.values.forEach(::assertWellFormed)
        val sheet = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("9001011******"))
        assertFalse(sheet.contains("9001011234567"))
        assertTrue(sheet.contains("<v>5000000000</v>"))
        assertTrue(sheet.contains("&lt;특약&gt; &amp; 기타"))
        assertTrue("xl/worksheets/sheet2.xml" in files)
    }

    @Test
    fun docxIsWellFormedWithOnePagePerPolicy() {
        val bos = ByteArrayOutputStream()
        PolicyExporter.write(ExportFormat.DOCX, sample, maskIds = false, out = bos, generatedAt = "2026-11-25")
        val files = unzip(bos.toByteArray())
        files.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.values.forEach(::assertWellFormed)
        val doc = files.getValue("word/document.xml")
        assertEquals(1, Regex("""w:type="page"""").findAll(doc).count())
        assertTrue(doc.contains("9001011234567"))
        assertTrue(doc.contains("5,000,000,000원"))
    }

    @Test
    fun columnNames() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
    }
}
