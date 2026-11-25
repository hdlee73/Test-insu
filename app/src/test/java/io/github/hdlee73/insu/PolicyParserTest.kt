package io.github.hdlee73.insu

import io.github.hdlee73.insu.domain.model.PartyType
import io.github.hdlee73.insu.domain.model.PaymentCycle
import io.github.hdlee73.insu.domain.parser.FieldFlag
import io.github.hdlee73.insu.domain.parser.FieldKey
import io.github.hdlee73.insu.domain.parser.PolicyParser
import io.github.hdlee73.insu.domain.parser.RowBuilder
import io.github.hdlee73.insu.domain.parser.TextFragment
import io.github.hdlee73.insu.domain.parser.TextRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 예시 증권 문구는 모두 가상의 값이다. 사업자번호 1234567891은 검증식을 통과하는 예시 번호다. */
class PolicyParserTest {

    private fun rows(text: String) = text.trimIndent().lines().map { TextRow(it, 0) }

    @Test
    fun corporatePropertyPolicy() {
        val r = PolicyParser.parse(
            rows(
                """
                재산종합보험 보험증권
                증권번호  P2026-0012345
                보험계약자  주식회사 한빛물산   사업자등록번호 123-45-67891
                피보험자  주식회사 한빛물산   사업자등록번호 123-45-67891
                보험기간  2026년 1월 1일 16:00부터 2027년 1월 1일 16:00까지
                보험가입금액  5,000,000,000원
                합계보험료  3,200,000원   납입방법 일시납
                삼성화재해상보험주식회사
                """,
            ),
        )
        val p = r.policy
        assertEquals("주식회사 한빛물산", p.contractor.name)
        assertEquals(PartyType.CORPORATION, p.contractor.type)
        assertEquals("1234567891", p.contractor.idNumber)
        assertEquals("1234567891", p.insured.idNumber)
        assertEquals(5_000_000_000L, p.sumInsured)
        assertEquals(3_200_000L, p.premium)
        assertEquals(PaymentCycle.LUMP_SUM, p.paymentCycle)
        assertEquals("2026-01-01", p.startDate)
        assertEquals("2027-01-01", p.endDate)
        assertEquals("삼성화재", p.insurer)
        assertEquals("P2026-0012345", p.policyNumber)
        assertEquals(FieldFlag.OK, r.hits[FieldKey.CONTRACTOR_ID]?.flag)
    }

    @Test
    fun individualLifePolicyWithMaskedIdAndSameInsured() {
        val r = PolicyParser.parse(
            rows(
                """
                무배당 종신보험 증권
                계약자 홍길동님 (900101-1******)
                피보험자 계약자와 동일
                보험기간 2025.03.15 ~ 2095.03.15
                주계약 가입금액 1억원
                월 보험료 123,400원 (월납, 20년납)
                """,
            ),
        )
        val p = r.policy
        assertEquals("홍길동", p.contractor.name)
        assertEquals(PartyType.INDIVIDUAL, p.contractor.type)
        assertEquals("9001011******", p.contractor.idNumber)
        assertTrue(p.contractor.isIdMasked)
        assertEquals(FieldFlag.CHECK, r.hits[FieldKey.CONTRACTOR_ID]?.flag)
        assertEquals("홍길동", p.insured.name)
        assertEquals(100_000_000L, p.sumInsured)
        assertEquals(123_400L, p.premium)
        assertEquals(PaymentCycle.MONTHLY, p.paymentCycle)
        assertEquals("2025-03-15", p.startDate)
        assertEquals("2095-03-15", p.endDate)
    }

    @Test
    fun tableLayoutWithIdsOnSeparateRowAndCoverageSum() {
        val r = PolicyParser.parse(
            rows(
                """
                계약자  김서연    피보험자  김민준
                주민등록번호  950505-2234567    주민등록번호  200303-3234567
                보험계약기간 : 2026-05-01 ~ 2027-05-01
                담보명   가입금액   보험료
                1. 일반상해사망  10,000,000원  1,200원
                2. 질병입원일당  30,000원  2,300원
                3. 상해수술비  1,000,000원  500원
                합계보험료  18,900원
                납입주기 : 월
                """,
            ),
        )
        val p = r.policy
        assertEquals("김서연", p.contractor.name)
        assertEquals("9505052234567", p.contractor.idNumber)
        assertEquals("김민준", p.insured.name)
        assertEquals("2003033234567", p.insured.idNumber)
        assertEquals(3, p.coverages.size)
        assertEquals("일반상해사망", p.coverages[0].name)
        assertEquals(11_030_000L, p.sumInsured)
        assertEquals(FieldFlag.CHECK, r.hits[FieldKey.SUM_INSURED]?.flag)
        assertEquals(18_900L, p.premium)
        assertEquals(PaymentCycle.MONTHLY, p.paymentCycle)
        assertEquals("2026-05-01", p.startDate)
    }

    @Test
    fun corporateRegistrationNumberIsNotTakenAsResidentNumber() {
        val r = PolicyParser.parse(
            rows(
                """
                계약자 (주)대한정밀 법인등록번호 110111-1234567 사업자번호 1234567891
                피보험자 (주)대한정밀
                보험기간 2026.07.01 - 2027.07.01
                보험가입금액 2억 5천만원
                보험료 940,000원 연납
                """,
            ),
        )
        val p = r.policy
        assertEquals("(주)대한정밀", p.contractor.name)
        assertEquals("1234567891", p.contractor.idNumber)
        assertEquals(PartyType.CORPORATION, p.insured.type)
        assertEquals(250_000_000L, p.sumInsured)
        assertEquals(940_000L, p.premium)
        assertEquals(PaymentCycle.YEARLY, p.paymentCycle)
    }

    @Test
    fun ocrDigitConfusionAndFullWidthCharacters() {
        val r = PolicyParser.parse(
            rows(
                """
                보험계약자 : 이민호   주민등록번호 85O7I2-1234567
                피보험자 : 이민호
                보험기간 ２０２４년 １１월 ０１일 ～ ２０４４년 １１월 ０１일
                보험가입금액 100,000천원
                보험료 67,000원
                """,
            ),
        )
        val p = r.policy
        assertEquals("8507121234567", p.contractor.idNumber)
        assertEquals("8507121234567", p.insured.idNumber)
        assertEquals("2024-11-01", p.startDate)
        assertEquals("2044-11-01", p.endDate)
        assertEquals(100_000_000L, p.sumInsured)
    }

    @Test
    fun sentencesInFinePrintAreNotLabels() {
        val r = PolicyParser.parse(
            rows(
                """
                계약자는 보험료를 납입할 의무가 있습니다.
                보험계약자  박지훈
                """,
            ),
        )
        assertEquals("박지훈", r.policy.contractor.name)
        assertNull(r.policy.premium)
        assertEquals(FieldFlag.MISSING, r.hits[FieldKey.PREMIUM]?.flag)
    }

    @Test
    fun rowBuilderGroupsColumnsBySpatialPosition() {
        // OCR은 열 단위로 결과를 돌려주는 일이 많다: 라벨 열이 먼저, 값 열이 나중에.
        val fragments = listOf(
            TextFragment("계약자", 0, 0.05f, 0.10f, 0.15f, 0.12f),
            TextFragment("피보험자", 0, 0.05f, 0.14f, 0.17f, 0.16f),
            TextFragment("홍길동", 0, 0.40f, 0.101f, 0.50f, 0.121f),
            TextFragment("김영희", 0, 0.40f, 0.141f, 0.50f, 0.161f),
        )
        val rows = RowBuilder.build(fragments)
        assertEquals(2, rows.size)
        assertEquals("계약자  홍길동", rows[0].text)
        assertEquals("피보험자  김영희", rows[1].text)
        val p = PolicyParser.parse(rows).policy
        assertEquals("홍길동", p.contractor.name)
        assertEquals("김영희", p.insured.name)
    }
}
