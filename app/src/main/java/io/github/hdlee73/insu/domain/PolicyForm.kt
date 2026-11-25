package io.github.hdlee73.insu.domain

import io.github.hdlee73.insu.domain.model.Coverage
import io.github.hdlee73.insu.domain.model.Party
import io.github.hdlee73.insu.domain.model.PartyType
import io.github.hdlee73.insu.domain.model.PaymentCycle
import io.github.hdlee73.insu.domain.model.Policy
import io.github.hdlee73.insu.domain.model.ReviewStatus
import io.github.hdlee73.insu.domain.parser.IdValidators
import io.github.hdlee73.insu.domain.parser.Normalizers

/** 검수·수정 화면의 입력값. 사용자가 타이핑하는 그대로 문자열로 들고 있다가 저장할 때 검증·변환한다. */
data class PolicyForm(
    val id: Long = 0,
    val contractorType: PartyType = PartyType.UNKNOWN,
    val contractorName: String = "",
    val contractorId: String = "",
    val insuredType: PartyType = PartyType.UNKNOWN,
    val insuredName: String = "",
    val insuredId: String = "",
    val sumInsured: String = "",
    val premium: String = "",
    val paymentCycle: PaymentCycle = PaymentCycle.UNKNOWN,
    val startDate: String = "",
    val endDate: String = "",
    val productName: String = "",
    val insurer: String = "",
    val policyNumber: String = "",
    val coverages: List<Coverage> = emptyList(),
    val sourceName: String = "",
    val createdAt: Long = 0,
) {
    enum class Field { CONTRACTOR_NAME, CONTRACTOR_ID, INSURED_NAME, INSURED_ID, SUM_INSURED, PREMIUM, START_DATE, END_DATE }

    data class Issue(val field: Field, val message: String)

    fun validate(): List<Issue> {
        val out = ArrayList<Issue>()
        if (contractorName.isBlank()) out += Issue(Field.CONTRACTOR_NAME, "계약자 이름을 입력하세요")
        idIssue(contractorType, contractorId)?.let { out += Issue(Field.CONTRACTOR_ID, it) }
        if (insuredName.isBlank()) out += Issue(Field.INSURED_NAME, "피보험자 이름을 입력하세요")
        idIssue(insuredType, insuredId)?.let { out += Issue(Field.INSURED_ID, it) }
        if (Normalizers.digitsOnly(sumInsured).isEmpty()) out += Issue(Field.SUM_INSURED, "보험가입금액을 입력하세요")
        if (Normalizers.digitsOnly(premium).isEmpty()) out += Issue(Field.PREMIUM, "보험료를 입력하세요")
        val s = Normalizers.normalizeUserDate(startDate)
        val e = Normalizers.normalizeUserDate(endDate)
        if (s == null) out += Issue(Field.START_DATE, "YYYY-MM-DD 형식으로 입력하세요")
        if (e == null) out += Issue(Field.END_DATE, "YYYY-MM-DD 형식으로 입력하세요")
        if (s != null && e != null && e <= s) out += Issue(Field.END_DATE, "종료일이 시작일보다 늦어야 합니다")
        return out
    }

    private fun idIssue(type: PartyType, raw: String): String? {
        val id = cleanId(raw)
        if (id.isEmpty()) return "등록번호를 입력하세요"
        if (id.contains('*')) return "가려진 자리를 채워 주세요"
        return when (type) {
            PartyType.CORPORATION ->
                if (id.length != 10) "사업자등록번호는 숫자 10자리입니다"
                else if (!IdValidators.isValidBusinessNumber(id)) "사업자등록번호 검증에 실패했습니다. 다시 확인하세요"
                else null
            PartyType.INDIVIDUAL ->
                if (id.length != 13) "주민등록번호는 숫자 13자리입니다"
                else if (!IdValidators.isPlausibleResidentNumber(id)) "생년월일 부분을 다시 확인하세요"
                else null
            PartyType.UNKNOWN -> "개인/법인을 선택하세요"
        }
    }

    fun toPolicy(now: Long): Policy {
        val issues = validate()
        return Policy(
            id = id,
            productName = productName.trim(),
            insurer = insurer.trim(),
            policyNumber = policyNumber.trim(),
            contractor = Party(contractorType, contractorName.trim(), cleanId(contractorId)),
            insured = Party(insuredType, insuredName.trim(), cleanId(insuredId)),
            sumInsured = Normalizers.digitsOnly(sumInsured).toLongOrNull(),
            premium = Normalizers.digitsOnly(premium).toLongOrNull(),
            paymentCycle = paymentCycle,
            startDate = Normalizers.normalizeUserDate(startDate),
            endDate = Normalizers.normalizeUserDate(endDate),
            coverages = coverages,
            status = if (issues.isEmpty()) ReviewStatus.DONE else ReviewStatus.NEEDS_REVIEW,
            sourceName = sourceName,
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = now,
        )
    }

    fun valueOf(field: Field): String = when (field) {
        Field.CONTRACTOR_NAME -> contractorName
        Field.CONTRACTOR_ID -> contractorId
        Field.INSURED_NAME -> insuredName
        Field.INSURED_ID -> insuredId
        Field.SUM_INSURED -> sumInsured
        Field.PREMIUM -> premium
        Field.START_DATE -> startDate
        Field.END_DATE -> endDate
    }

    fun copyContractorToInsured(): PolicyForm =
        copy(insuredType = contractorType, insuredName = contractorName, insuredId = contractorId)

    companion object {
        /** 숫자와 가림표(*)만 남긴다. '-'와 공백은 지운다. */
        fun cleanId(raw: String): String = raw.filter { it.isDigit() || it == '*' }

        fun from(policy: Policy): PolicyForm = PolicyForm(
            id = policy.id,
            contractorType = policy.contractor.type,
            contractorName = policy.contractor.name,
            contractorId = policy.contractor.idNumber,
            insuredType = policy.insured.type,
            insuredName = policy.insured.name,
            insuredId = policy.insured.idNumber,
            sumInsured = policy.sumInsured?.toString().orEmpty(),
            premium = policy.premium?.toString().orEmpty(),
            paymentCycle = policy.paymentCycle,
            startDate = policy.startDate.orEmpty(),
            endDate = policy.endDate.orEmpty(),
            productName = policy.productName,
            insurer = policy.insurer,
            policyNumber = policy.policyNumber,
            coverages = policy.coverages,
            sourceName = policy.sourceName,
            createdAt = policy.createdAt,
        )
    }
}
