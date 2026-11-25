package io.github.hdlee73.insu.domain.model

/** 계약 당사자 구분. 법인은 사업자등록번호(10자리), 개인은 주민등록번호(13자리)를 쓴다. */
enum class PartyType(val label: String) {
    INDIVIDUAL("개인"),
    CORPORATION("법인"),
    UNKNOWN("미확인"),
}

/**
 * 계약자 또는 피보험자.
 * [idNumber]는 '-' 없이 숫자만 저장한다. 증권에 가려져 인쇄된 자리는 '*'로 남긴다.
 */
data class Party(
    val type: PartyType = PartyType.UNKNOWN,
    val name: String = "",
    val idNumber: String = "",
) {
    val isIdMasked: Boolean get() = idNumber.contains('*')
    val isEmpty: Boolean get() = name.isBlank() && idNumber.isBlank()
}

enum class PaymentCycle(val label: String) {
    MONTHLY("월납"),
    QUARTERLY("3개월납"),
    SEMIANNUAL("6개월납"),
    YEARLY("연납"),
    LUMP_SUM("일시납"),
    UNKNOWN("미확인"),
}

/** 담보(보장) 한 줄: 담보명과 가입금액(원). */
data class Coverage(val name: String, val amount: Long)

enum class ReviewStatus { NEEDS_REVIEW, DONE }

data class Policy(
    val id: Long = 0,
    val productName: String = "",
    val insurer: String = "",
    val policyNumber: String = "",
    val contractor: Party = Party(),
    val insured: Party = Party(),
    /** 보험가입금액(원). 담보가 여러 개면 합계. */
    val sumInsured: Long? = null,
    /** 1회 보험료(원). */
    val premium: Long? = null,
    val paymentCycle: PaymentCycle = PaymentCycle.UNKNOWN,
    /** YYYY-MM-DD */
    val startDate: String? = null,
    /** YYYY-MM-DD */
    val endDate: String? = null,
    val coverages: List<Coverage> = emptyList(),
    val status: ReviewStatus = ReviewStatus.NEEDS_REVIEW,
    val sourceName: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** 목록에서 보일 대표 이름: 계약자 → 피보험자 → 상품명 순. */
    val displayName: String
        get() = contractor.name.ifBlank { insured.name.ifBlank { productName.ifBlank { "이름 없는 증권" } } }
}
