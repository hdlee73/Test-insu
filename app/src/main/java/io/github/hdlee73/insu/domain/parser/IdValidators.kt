package io.github.hdlee73.insu.domain.parser

import java.time.LocalDate

/** 사업자등록번호·주민등록번호 형식 검증. */
object IdValidators {

    /** 국세청 사업자등록번호 검증식(가중치 1,3,7,1,3,7,1,3,5). */
    fun isValidBusinessNumber(digits: String): Boolean {
        if (digits.length != 10 || !digits.all { it.isDigit() }) return false
        val d = digits.map { it - '0' }
        val w = intArrayOf(1, 3, 7, 1, 3, 7, 1, 3, 5)
        var sum = 0
        for (i in 0 until 9) sum += d[i] * w[i]
        sum += (d[8] * 5) / 10
        return (10 - sum % 10) % 10 == d[9]
    }

    /**
     * 주민등록번호 앞자리 생년월일과 성별 자리 검사.
     * 2020년 10월 이후 발급 번호는 끝자리 검증식이 없어 체크섬은 보지 않는다.
     * 뒷자리가 가려진('*') 번호도 앞 7자리로 판단한다.
     */
    fun isPlausibleResidentNumber(value: String): Boolean {
        if (value.length != 13) return false
        if (!value.take(7).all { it.isDigit() }) return false
        if (!value.drop(7).all { it.isDigit() || it == '*' }) return false
        val g = value[6] - '0'
        val century = when (g) {
            1, 2, 5, 6 -> 1900
            3, 4, 7, 8 -> 2000
            9, 0 -> 1800
            else -> return false
        }
        val yy = value.substring(0, 2).toInt()
        val mm = value.substring(2, 4).toInt()
        val dd = value.substring(4, 6).toInt()
        return runCatching { LocalDate.of(century + yy, mm, dd) }.isSuccess
    }
}
