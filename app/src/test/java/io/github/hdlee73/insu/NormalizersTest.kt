package io.github.hdlee73.insu

import io.github.hdlee73.insu.domain.Masking
import io.github.hdlee73.insu.domain.PolicyForm
import io.github.hdlee73.insu.domain.model.Party
import io.github.hdlee73.insu.domain.model.PartyType
import io.github.hdlee73.insu.domain.model.ReviewStatus
import io.github.hdlee73.insu.domain.parser.IdValidators
import io.github.hdlee73.insu.domain.parser.Normalizers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizersTest {

    @Test
    fun koreanAmounts() {
        assertEquals(100_000_000L, Normalizers.parseAmount("1억원"))
        assertEquals(120_000_000L, Normalizers.parseAmount("1억 2,000만원"))
        assertEquals(120_000_000L, Normalizers.parseAmount("1억2천만원"))
        assertEquals(50_000_000L, Normalizers.parseAmount("5천만원"))
        assertEquals(100_000_000L, Normalizers.parseAmount("100,000천원"))
        assertEquals(2_000L, Normalizers.parseAmount("2천원"))
        assertEquals(123_400L, Normalizers.findAmount("월 보험료 123,400원 (20년납)"))
        assertNull(Normalizers.findAmount("보험기간 2025.03.15 ~ 2095.03.15"))
        assertNull(Normalizers.findAmount("20년납 80세만기"))
    }

    @Test
    fun dates() {
        assertEquals("2026-01-01", Normalizers.findDates("2026년 1월 1일 16시부터")[0].iso)
        assertEquals("2025-03-15", Normalizers.findDates("2025. 3. 15.")[0].iso)
        assertTrue(Normalizers.findDates("2026.02.30").isEmpty())
        assertEquals("2026-01-05", Normalizers.normalizeUserDate("20260105"))
        assertEquals("2026-01-05", Normalizers.normalizeUserDate("2026.1.5"))
        assertNull(Normalizers.normalizeUserDate("내일"))
    }

    @Test
    fun idValidation() {
        assertTrue(IdValidators.isValidBusinessNumber("1234567891"))
        assertFalse(IdValidators.isValidBusinessNumber("1234567890"))
        assertTrue(IdValidators.isPlausibleResidentNumber("9001011******"))
        assertTrue(IdValidators.isPlausibleResidentNumber("2003033234567"))
        assertFalse(IdValidators.isPlausibleResidentNumber("9013011234567"))
    }

    @Test
    fun masking() {
        val person = Party(PartyType.INDIVIDUAL, "홍길동", "9001011234567")
        assertEquals("900101-1******", Masking.display(person, reveal = false))
        assertEquals("900101-1234567", Masking.display(person, reveal = true))
        assertEquals("9001011******", Masking.export(person, mask = true))
        assertEquals("9001011234567", Masking.export(person, mask = false))
        val corp = Party(PartyType.CORPORATION, "주식회사 한빛물산", "1234567891")
        assertEquals("123-45-*****", Masking.display(corp, reveal = false))
        assertEquals("12345*****", Masking.export(corp, mask = true))
        val log = Masking.scrub("rrn=900101-1234567 brn=123-45-67891 raw=9001011234567 ok=2026")
        assertFalse(log.contains("1234567"))
        assertTrue(log.contains("ok=2026"))
    }

    @Test
    fun formValidationAndConversion() {
        val form = PolicyForm(
            contractorType = PartyType.CORPORATION, contractorName = "주식회사 한빛물산", contractorId = "123-45-67891",
            insuredType = PartyType.INDIVIDUAL, insuredName = "홍길동", insuredId = "900101-1234567",
            sumInsured = "100,000,000", premium = "12,000", startDate = "2026.1.1", endDate = "2027-01-01",
        )
        assertTrue(form.validate().isEmpty())
        val p = form.toPolicy(now = 1000L)
        assertEquals("1234567891", p.contractor.idNumber)
        assertEquals("9001011234567", p.insured.idNumber)
        assertEquals(100_000_000L, p.sumInsured)
        assertEquals("2026-01-01", p.startDate)
        assertEquals(ReviewStatus.DONE, p.status)

        val bad = form.copy(insuredId = "900101-1******", endDate = "2025-12-31")
        val issues = bad.validate().map { it.field }
        assertTrue(PolicyForm.Field.INSURED_ID in issues)
        assertTrue(PolicyForm.Field.END_DATE in issues)
        assertEquals(ReviewStatus.NEEDS_REVIEW, bad.toPolicy(1L).status)
    }
}
