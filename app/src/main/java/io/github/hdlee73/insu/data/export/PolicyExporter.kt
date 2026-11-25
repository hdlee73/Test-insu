package io.github.hdlee73.insu.data.export

import io.github.hdlee73.insu.domain.Masking
import io.github.hdlee73.insu.domain.model.Policy
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "엑셀(xlsx)"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "워드(docx)"),
}

/**
 * 증권 목록을 파일로 쓴다.
 * xlsx: 증권 1건 = 1행(시트 '증권'), 담보는 시트 '담보'에 따로.
 * docx: 증권 1건 = 1쪽 표.
 * 등록번호는 '-' 없이 숫자만, [maskIds]면 뒷자리를 '*'로 가린다.
 */
object PolicyExporter {

    private val won = NumberFormat.getNumberInstance(Locale.KOREA)

    fun write(format: ExportFormat, policies: List<Policy>, maskIds: Boolean, out: OutputStream, generatedAt: String) {
        when (format) {
            ExportFormat.XLSX -> writeXlsx(policies, maskIds, out)
            ExportFormat.DOCX -> writeDocx(policies, maskIds, out, generatedAt)
        }
    }

    private fun writeXlsx(policies: List<Policy>, maskIds: Boolean, out: OutputStream) {
        val T = XlsxWriter.CellType.TEXT
        val N = XlsxWriter.CellType.NUMBER
        val main = XlsxWriter.Sheet(
            name = "증권",
            columns = listOf(
                XlsxWriter.Column("번호", 6, N),
                XlsxWriter.Column("계약자 구분", 10, T),
                XlsxWriter.Column("계약자", 22, T),
                XlsxWriter.Column("계약자 등록번호", 18, T),
                XlsxWriter.Column("피보험자 구분", 11, T),
                XlsxWriter.Column("피보험자", 22, T),
                XlsxWriter.Column("피보험자 등록번호", 18, T),
                XlsxWriter.Column("보험가입금액(원)", 18, N),
                XlsxWriter.Column("보험료(원)", 14, N),
                XlsxWriter.Column("납입주기", 9, T),
                XlsxWriter.Column("계약 시작일", 12, T),
                XlsxWriter.Column("계약 종료일", 12, T),
                XlsxWriter.Column("보험사", 14, T),
                XlsxWriter.Column("상품명", 28, T),
                XlsxWriter.Column("증권번호", 18, T),
            ),
            rows = policies.mapIndexed { i, p ->
                listOf(
                    i + 1,
                    p.contractor.type.label, p.contractor.name, Masking.export(p.contractor, maskIds),
                    p.insured.type.label, p.insured.name, Masking.export(p.insured, maskIds),
                    p.sumInsured, p.premium, p.paymentCycle.label,
                    p.startDate.orEmpty(), p.endDate.orEmpty(),
                    p.insurer, p.productName, p.policyNumber,
                )
            },
        )
        val coverageRows = policies.flatMapIndexed { i, p -> p.coverages.map { c -> listOf(i + 1, p.displayName, c.name, c.amount) } }
        val sheets = buildList {
            add(main)
            if (coverageRows.isNotEmpty()) {
                add(
                    XlsxWriter.Sheet(
                        name = "담보",
                        columns = listOf(
                            XlsxWriter.Column("증권 번호", 9, N),
                            XlsxWriter.Column("계약자", 22, T),
                            XlsxWriter.Column("담보명", 36, T),
                            XlsxWriter.Column("가입금액(원)", 18, N),
                        ),
                        rows = coverageRows,
                    ),
                )
            }
        }
        XlsxWriter.write(out, sheets)
    }

    private fun writeDocx(policies: List<Policy>, maskIds: Boolean, out: OutputStream, generatedAt: String) {
        val doc = DocxBuilder()
        policies.forEachIndexed { i, p ->
            if (i > 0) doc.pageBreak()
            doc.heading("보험증권 요약 ${i + 1}")
            doc.paragraph(listOf(p.insurer, p.productName).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { p.sourceName })
            doc.table(
                widthsTwips = listOf(2600, 7100),
                labelColumn = true,
                rows = listOf(
                    listOf("계약자", "${p.contractor.name} (${p.contractor.type.label})"),
                    listOf(idLabel(p.contractor.idNumber), Masking.export(p.contractor, maskIds).ifBlank { "-" }),
                    listOf("피보험자", "${p.insured.name} (${p.insured.type.label})"),
                    listOf(idLabel(p.insured.idNumber), Masking.export(p.insured, maskIds).ifBlank { "-" }),
                    listOf("보험가입금액", amount(p.sumInsured)),
                    listOf("보험료", amount(p.premium) + if (p.paymentCycle.name != "UNKNOWN") " (${p.paymentCycle.label})" else ""),
                    listOf("보험계약기간", "${p.startDate ?: "-"} ~ ${p.endDate ?: "-"}"),
                    listOf("증권번호", p.policyNumber.ifBlank { "-" }),
                ),
            )
            if (p.coverages.isNotEmpty()) {
                doc.paragraph("담보별 가입금액", 22, "1D2B3F")
                doc.table(
                    widthsTwips = listOf(6700, 3000),
                    header = listOf("담보명", "가입금액"),
                    rows = p.coverages.map { listOf(it.name, amount(it.amount)) },
                )
            }
            doc.paragraph("증권노트에서 $generatedAt 생성", 16, "8A94A6")
        }
        doc.write(out)
    }

    private fun idLabel(id: String) = when (id.length) {
        10 -> "사업자등록번호"
        13 -> "주민등록번호"
        else -> "등록번호"
    }

    private fun amount(v: Long?): String = v?.let { won.format(it) + "원" } ?: "-"
}
