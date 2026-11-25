package io.github.hdlee73.insu.domain.parser

import java.time.LocalDate

/** 증권 문구를 파싱하기 쉬운 형태로 바꾸고, 금액·날짜를 정규화한다. */
object Normalizers {

    /** 전각 숫자·여러 종류의 줄표·물결표를 표준 문자로 바꾸고, 숫자 사이 OCR 오인식을 고친다. */
    fun normalizeText(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                in '０'..'９' -> sb.append('0' + (c - '０'))
                '‐', '‑', '‒', '–', '—', '―', '－', '−', 'ㅡ' -> sb.append('-')
                '：' -> sb.append(':')
                '～', '〜', '∼' -> sb.append('~')
                '（' -> sb.append('(')
                '）' -> sb.append(')')
                '，' -> sb.append(',')
                '．' -> sb.append('.')
                '＊', '✱', '∗' -> sb.append('*')
                ' ', '　', '\t' -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return fixNumericTokens(sb.toString())
    }

    private val numericLike = Regex("""[0-9OoIl|SB][0-9OoIl|SB\-.,*]{5,}""")

    /** 숫자가 대부분인 토큰 안의 O→0, I/l/|→1, S→5, B→8 오인식만 고친다. */
    private fun fixNumericTokens(text: String): String = numericLike.replace(text) { m ->
        val token = m.value
        val digits = token.count { it.isDigit() }
        val letters = token.count { it in "OoIl|SB" }
        if (digits >= 4 && letters in 1..2) {
            token.map {
                when (it) {
                    'O', 'o' -> '0'
                    'I', 'l', '|' -> '1'
                    'S' -> '5'
                    'B' -> '8'
                    else -> it
                }
            }.joinToString("")
        } else token
    }

    // ---------- 금액 ----------

    private const val NUM = """\d[\d,]*(?:\.\d+)?"""
    private const val UNIT = """조|억|천만|백만|십만|만|천"""

    /** "1억 2,000만원", "100,000,000원", "100,000천원", "5억" 같은 금액 표현. */
    val amountRegex = Regex(
        """(?<![\d.])((?:$NUM\s*(?:$UNIT)\s*)+(?:$NUM\s*원|원)?|$NUM\s*(?:천원|원)|\d{1,3}(?:,\d{3})+)(?![\d,])"""
    )

    private val partRegex = Regex("""($NUM)\s*($UNIT)?""")

    /** 문장에서 첫 금액을 원 단위로 돌려준다. */
    fun findAmount(text: String): Long? = findAmounts(text).firstOrNull()?.second

    /** 문장의 모든 금액을 (시작 위치, 원 단위 값)으로 돌려준다. */
    fun findAmounts(text: String): List<Pair<Int, Long>> =
        amountRegex.findAll(text).mapNotNull { m -> parseAmount(m.value)?.let { m.range.first to it } }.toList()

    fun parseAmount(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val thousandWon = s.endsWith("천원")
        val body = s.removeSuffix("원").let { if (thousandWon) it.removeSuffix("천") else it }
        var total = 0.0
        var matched = false
        for (p in partRegex.findAll(body)) {
            val n = p.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val mult = when (p.groupValues[2]) {
                "조" -> 1_000_000_000_000.0
                "억" -> 100_000_000.0
                "천만" -> 10_000_000.0
                "백만" -> 1_000_000.0
                "십만" -> 100_000.0
                "만" -> 10_000.0
                "천" -> 1_000.0
                else -> 1.0
            }
            total += n * mult
            matched = true
        }
        if (!matched) return null
        if (thousandWon) total *= 1000.0
        val value = Math.round(total)
        return if (value > 0) value else null
    }

    // ---------- 날짜 ----------

    val dateRegex = Regex(
        """(?<!\d)((?:19|20)\d{2})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})(?!\d)\s*(?:일|\.)?"""
    )

    data class FoundDate(val start: Int, val end: Int, val iso: String)

    fun findDates(text: String): List<FoundDate> = dateRegex.findAll(text).mapNotNull { m ->
        toIso(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { FoundDate(m.range.first, m.range.last + 1, it) }
    }.toList()

    fun toIso(y: String, m: String, d: String): String? = runCatching {
        LocalDate.of(y.toInt(), m.toInt(), d.toInt()).toString()
    }.getOrNull()

    fun isIsoDate(s: String): Boolean =
        Regex("""\d{4}-\d{2}-\d{2}""").matches(s) && runCatching { LocalDate.parse(s) }.isSuccess

    /** 사용자가 입력한 "2026.1.1", "20260101" 같은 값을 YYYY-MM-DD로 바꾼다. 실패하면 null. */
    fun normalizeUserDate(input: String): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        if (isIsoDate(t)) return t
        Regex("""^(\d{4})(\d{2})(\d{2})$""").find(t)?.let { return toIso(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        return findDates(t).firstOrNull()?.iso
    }

    fun digitsOnly(s: String): String = s.filter { it.isDigit() }
}
