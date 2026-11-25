package io.github.hdlee73.insu.domain

import io.github.hdlee73.insu.domain.model.Party
import io.github.hdlee73.insu.domain.model.PartyType

/** 등록번호 표시·내보내기용 가림 처리. 순수 Kotlin이라 단위 테스트가 가능하다. */
object Masking {

    /** 화면 표시용: 주민번호 900101-1******, 사업자번호 123-45-*****. */
    fun display(party: Party, reveal: Boolean): String {
        val id = party.idNumber
        if (id.isBlank()) return "-"
        return when {
            id.length == 13 && party.type != PartyType.CORPORATION ->
                if (reveal) "${id.substring(0, 6)}-${id.substring(6)}" else "${id.substring(0, 6)}-${id[6]}******"
            id.length == 10 ->
                if (reveal) "${id.substring(0, 3)}-${id.substring(3, 5)}-${id.substring(5)}" else "${id.substring(0, 3)}-${id.substring(3, 5)}-*****"
            reveal -> id
            else -> maskTail(id)
        }
    }

    /** 내보내기용: '-' 없이 숫자만. mask=true면 주민번호는 앞 7자리, 사업자번호는 앞 5자리만 남긴다. */
    fun export(party: Party, mask: Boolean): String {
        val id = party.idNumber
        if (id.isBlank() || !mask) return id
        return when (id.length) {
            13 -> id.substring(0, 7) + "*".repeat(6)
            10 -> id.substring(0, 5) + "*".repeat(5)
            else -> maskTail(id)
        }
    }

    private fun maskTail(id: String): String {
        val keep = (id.length / 2).coerceAtMost(7)
        return id.take(keep) + "*".repeat(id.length - keep)
    }

    private val sensitivePatterns = listOf(
        Regex("""\d{6}\s*-\s*[\d*]{7}"""),
        Regex("""\d{3}\s*-\s*\d{2}\s*-\s*[\d*]{5}"""),
        Regex("""(?<!\d)\d{10,13}(?!\d)"""),
    )

    /** 로그 파일에 주민번호·사업자번호가 남지 않도록 지운다. */
    fun scrub(text: String): String =
        sensitivePatterns.fold(text) { acc, r -> r.replace(acc, "[번호 제거]") }
}
