package io.github.hdlee73.insu.domain.parser

import io.github.hdlee73.insu.domain.model.Coverage
import io.github.hdlee73.insu.domain.model.Party
import io.github.hdlee73.insu.domain.model.PartyType
import io.github.hdlee73.insu.domain.model.PaymentCycle
import io.github.hdlee73.insu.domain.model.Policy
import io.github.hdlee73.insu.domain.model.ReviewStatus

enum class FieldKey {
    CONTRACTOR_NAME, CONTRACTOR_ID, INSURED_NAME, INSURED_ID,
    SUM_INSURED, PREMIUM, PAYMENT_CYCLE, START_DATE, END_DATE,
}

/** OK: 라벨 옆에서 찾고 검증 통과, CHECK: 찾았지만 확인 필요, MISSING: 못 찾음. */
enum class FieldFlag { OK, CHECK, MISSING }

data class FieldHit(val flag: FieldFlag, val rowIndex: Int? = null, val note: String? = null)

data class ExtractionResult(
    val policy: Policy,
    val hits: Map<FieldKey, FieldHit>,
    val rows: List<TextRow>,
)

/**
 * 규칙 기반 증권 파서. 라벨(계약자, 피보험자, 보험기간 …)을 앵커로 삼아 값을 찾고,
 * 못 찾으면 문서 전체에서 형식으로 추정한 뒤 CHECK 표시를 남긴다.
 * 최종 판단은 사람이 검수 화면에서 한다.
 */
object PolicyParser {

    private val contractorLabel = label("보험계약자", "계약자명", "계약자", "청약자")
    private val insuredLabel = label("주피보험자", "피보험자명", "피보험자", "피보험인")
    private val periodLabel = label("보험계약기간", "보험기간", "계약기간", "보장기간")
    private val cycleLabel = label("납입주기", "납입방법", "납입방식", "납입형태")
    private val productLabel = label("보험상품명", "상품명", "보험종목", "보험상품")
    private val policyNoLabel = label("증권번호", "계약번호")

    private val premiumLabels = listOf(
        "합계보험료", "총보험료", "실납입보험료", "납입보험료", "영수보험료", "1회보험료", "월보험료", "보험료",
    ).map { it to label(it) }
    private val totalSumLabels = listOf("총보험가입금액", "보험가입금액합계", "가입금액합계", "총가입금액").map { label(it) }
    private val generalSumLabels = listOf("보험가입금액", "가입금액", "보험금액", "보상한도액", "보장금액").map { label(it) }
    private val coverageHeader = Regex("""담\s*보\s*명|보\s*장\s*명|가\s*입\s*담\s*보|담\s*보\s*내\s*용|보\s*장\s*내\s*용|특\s*약\s*명|담\s*보\s*사\s*항""")

    private val sameAsContractor = Regex("""^[\s:|(]*(?:계약자\s*(?:와|과)?\s*(?:동일|같음)|상\s*동|동\s*일|본\s*인)""")
    private val josaAfterLabel = Regex("""^(?:는|가|의|에|를|은|및|와|과|이|에게|로|란)""")

    private val rrnRegex = Regex("""(?<![\d*])(\d{6})\s*-\s*([\d*]{7})(?![\d*])|(?<![\d-])(\d{13})(?!\d)""")
    private val brnRegex = Regex("""(?<![\d-])(\d{3})\s*-\s*(\d{2})\s*-\s*(\d{5})(?!\d)|(?<![\d-])(\d{10})(?!\d)""")
    private val idExclusionBefore = Regex("""법인\s*등록|법인\s*번호|증권\s*번호|계약\s*번호|전화|연락처|휴대|핸드폰|팩스|FAX|계좌|카드""")

    private val corpPrefix = Regex(
        """(주식회사|유한회사|유한책임회사|합자회사|합명회사|사단법인|재단법인|의료법인|학교법인|사회복지법인|농업회사법인|영농조합법인|\(주\)|㈜|\(유\)|\(사\)|\(재\))\s?([가-힣A-Za-z0-9&·.]+)"""
    )
    private val corpSuffix = Regex("""([가-힣A-Za-z0-9&·.]{2,})\s?(주식회사|유한회사|\(주\)|㈜|\(유\))""")
    private val personToken = Regex("""(?<![가-힣])[가-힣]{2,4}(?![가-힣])""")
    private val honorific = Regex("""([가-힣]{2,4})(님|귀하)(?![가-힣])""")

    private val nameStopWords = setOf(
        "구분", "성명", "상호", "법인명", "이름", "주민등록번호", "사업자등록번호", "등록번호", "생년월일", "연락처",
        "주소", "직업", "관계", "본인", "성별", "나이", "보험나이", "남", "여", "남자", "여자", "개인", "법인",
        "보험", "계약", "주민", "등록", "번호", "사업자", "고객", "고객명", "정보", "내용", "해당", "없음", "동일",
        "상동", "증권", "기간", "보험기간", "피보험자", "계약자", "보험계약자", "수익자", "만기", "사망", "상해",
        "질병", "주계약", "특약", "외국인", "내국인", "회사", "대표", "대표자", "명의", "직장", "운전", "차량",
    )

    val knownInsurers = listOf(
        "삼성화재", "현대해상", "DB손해보험", "KB손해보험", "메리츠화재", "한화손해보험", "롯데손해보험", "흥국화재",
        "NH농협손해보험", "MG손해보험", "하나손해보험", "AXA손해보험", "캐롯손해보험", "카카오페이손해보험", "서울보증보험",
        "삼성생명", "한화생명", "교보생명", "신한라이프", "NH농협생명", "동양생명", "미래에셋생명", "흥국생명", "KB라이프",
        "ABL생명", "AIA생명", "메트라이프생명", "라이나생명", "처브라이프", "DB생명", "푸본현대생명", "하나생명",
        "IBK연금보험", "KDB생명", "iM라이프", "BNP파리바카디프생명", "교보라이프플래닛",
    ).sortedByDescending { it.length }

    fun parse(rawRows: List<TextRow>, sourceName: String = ""): ExtractionResult {
        val rows = rawRows.map { it.copy(text = Normalizers.normalizeText(it.text)) }
        val doc = Doc(rows)
        val hits = HashMap<FieldKey, FieldHit>()

        // ---- 계약자 / 피보험자 ----
        val contractorOcc = partyOccurrences(doc, contractorLabel)
            .filterNot { doc.text.substring(it.last + 1).trimStart().let { rest -> rest.startsWith("와 동일") || rest.startsWith("과 동일") } }
        val insuredOcc = partyOccurrences(doc, insuredLabel)
        val allLabelStarts = (contractorOcc + insuredOcc).map { it.first }.sorted()

        val usedIds = HashSet<Int>()
        val globalIds = collectIds(doc)

        val contractorFound = contractorOcc.asSequence()
            .map { occ -> occ to parsePartySection(doc, sectionOf(doc, occ, allLabelStarts)) }
            .firstOrNull { !it.second.party.isEmpty }
        var contractor = contractorFound?.second?.party ?: Party()
        val contractorRow = contractorFound?.first?.let { doc.rowAt(it.first) }
        contractorFound?.second?.idStart?.let { usedIds += it }

        var insuredSame = false
        var insuredFound: Pair<IntRange, PartyParse>? = null
        for (occ in insuredOcc) {
            val after = doc.text.substring(occ.last + 1, minOf(doc.text.length, occ.last + 30))
            if (sameAsContractor.containsMatchIn(after)) { insuredSame = true; insuredFound = occ to PartyParse(Party(), null); break }
            val parsed = parsePartySection(doc, sectionOf(doc, occ, allLabelStarts))
            if (!parsed.party.isEmpty) { insuredFound = occ to parsed; break }
        }
        var insured = insuredFound?.second?.party ?: Party()
        val insuredRow = insuredFound?.first?.let { doc.rowAt(it.first) }
        insuredFound?.second?.idStart?.let { usedIds += it }

        // 등록번호가 이름과 다른 행에 있는 표 양식: 문서 순서대로 남은 번호를 배정한다.
        var contractorIdGuessed = false
        var insuredIdGuessed = false
        if (contractor.idNumber.isEmpty() && !contractor.isEmpty) {
            pickGlobalId(globalIds, usedIds, contractor.type)?.let { g ->
                contractor = withId(contractor, g); usedIds += g.start; contractorIdGuessed = true
            }
        }
        if (insuredSame) {
            insured = contractor
        } else if (insured.idNumber.isEmpty() && !insured.isEmpty) {
            val g = pickGlobalId(globalIds, usedIds, insured.type)
            if (g != null) {
                insured = withId(insured, g); usedIds += g.start; insuredIdGuessed = true
            } else if (insured.name == contractor.name && contractor.idNumber.isNotEmpty()) {
                insured = contractor; insuredIdGuessed = true
            }
        }

        hits[FieldKey.CONTRACTOR_NAME] = nameHit(contractor, contractorRow)
        hits[FieldKey.CONTRACTOR_ID] = idHit(contractor, contractorRow, contractorIdGuessed)
        hits[FieldKey.INSURED_NAME] = if (insuredSame) FieldHit(FieldFlag.OK, insuredRow, "계약자와 동일") else nameHit(insured, insuredRow)
        hits[FieldKey.INSURED_ID] = if (insuredSame) idHit(insured, insuredRow, false) else idHit(insured, insuredRow, insuredIdGuessed)

        // ---- 금액 ----
        val coverageHeaderRow = rows.indexOfFirst { coverageHeader.containsMatchIn(it.text) }.takeIf { it >= 0 }
        val coverages = coverageHeaderRow?.let { collectCoverages(rows, it) } ?: emptyList()
        val total = findLabeledAmount(doc, totalSumLabels, emptySet())
        val general = findLabeledAmount(doc, generalSumLabels, setOfNotNull(coverageHeaderRow))
        val coverageSum = coverages.takeIf { it.size >= 2 }?.sumOf { it.amount }
        val sumInsured: Long?
        when {
            total != null -> { sumInsured = total.first; hits[FieldKey.SUM_INSURED] = FieldHit(FieldFlag.OK, total.second, "합계 금액") }
            coverageSum != null -> { sumInsured = coverageSum; hits[FieldKey.SUM_INSURED] = FieldHit(FieldFlag.CHECK, coverageHeaderRow, "담보 ${coverages.size}건 합계") }
            general != null -> { sumInsured = general.first; hits[FieldKey.SUM_INSURED] = FieldHit(FieldFlag.OK, general.second) }
            coverages.size == 1 -> { sumInsured = coverages[0].amount; hits[FieldKey.SUM_INSURED] = FieldHit(FieldFlag.CHECK, coverageHeaderRow, "담보 금액") }
            else -> { sumInsured = null; hits[FieldKey.SUM_INSURED] = FieldHit(FieldFlag.MISSING) }
        }

        val premiumHit = findPremium(doc)
        hits[FieldKey.PREMIUM] = premiumHit?.let { FieldHit(FieldFlag.OK, it.second) } ?: FieldHit(FieldFlag.MISSING)

        val cycle = findCycle(doc, premiumHit?.third)
        hits[FieldKey.PAYMENT_CYCLE] = if (cycle.first == PaymentCycle.UNKNOWN) FieldHit(FieldFlag.MISSING) else FieldHit(FieldFlag.OK, cycle.second)

        // ---- 기간 ----
        val period = findPeriod(doc)
        val startDate = period?.start
        val endDate = period?.end
        val periodOk = startDate != null && endDate != null && endDate > startDate
        hits[FieldKey.START_DATE] = when {
            startDate == null -> FieldHit(FieldFlag.MISSING)
            periodOk && period.labeled -> FieldHit(FieldFlag.OK, period.row)
            else -> FieldHit(FieldFlag.CHECK, period.row, if (period.labeled) null else "위치 추정")
        }
        hits[FieldKey.END_DATE] = when {
            endDate == null -> FieldHit(FieldFlag.MISSING)
            periodOk && period.labeled -> FieldHit(FieldFlag.OK, period.row)
            !periodOk -> FieldHit(FieldFlag.CHECK, period.row, "종료일이 시작일보다 빠름")
            else -> FieldHit(FieldFlag.CHECK, period.row, "위치 추정")
        }

        val policy = Policy(
            productName = findRest(doc, productLabel)?.take(40).orEmpty(),
            insurer = knownInsurers.firstOrNull { doc.text.replace(" ", "").contains(it) }.orEmpty(),
            policyNumber = findRest(doc, policyNoLabel)?.let { Regex("""[A-Za-z0-9][A-Za-z0-9\-]{5,}""").find(it)?.value }.orEmpty(),
            contractor = contractor,
            insured = insured,
            sumInsured = sumInsured,
            premium = premiumHit?.first,
            paymentCycle = cycle.first,
            startDate = startDate,
            endDate = endDate,
            coverages = coverages,
            status = ReviewStatus.NEEDS_REVIEW,
            sourceName = sourceName,
        )
        return ExtractionResult(policy, hits, rows)
    }

    // =====================================================================

    private class Doc(val rows: List<TextRow>) {
        val text: String
        private val starts: IntArray

        init {
            val sb = StringBuilder()
            starts = IntArray(rows.size)
            rows.forEachIndexed { i, r ->
                starts[i] = sb.length
                sb.append(r.text).append('\n')
            }
            text = sb.toString()
        }

        fun rowAt(offset: Int): Int {
            if (starts.isEmpty()) return 0
            var idx = java.util.Arrays.binarySearch(starts, offset)
            if (idx < 0) idx = -idx - 2
            return idx.coerceIn(0, starts.size - 1)
        }

        fun rowEnd(row: Int): Int = if (row + 1 < starts.size) starts[row + 1] - 1 else text.length
    }

    private class PartyParse(val party: Party, val idStart: Int?)

    private data class IdMatch(val start: Int, val digits: String, val isBusiness: Boolean)

    private fun label(vararg words: String): Regex = Regex(
        words.sortedByDescending { it.length }.joinToString("|") { w ->
            w.map { c -> if (c in "()[].*+?^$|\\{}") "\\$c" else c.toString() }.joinToString("""\s*""")
        }
    )

    /** 뒤에 조사가 붙은 본문 문장('계약자는 …')은 라벨로 보지 않는다. */
    private fun partyOccurrences(doc: Doc, regex: Regex): List<IntRange> =
        regex.findAll(doc.text).map { it.range }.filter { r ->
            val rest = doc.text.substring(r.last + 1, minOf(doc.text.length, r.last + 4))
            !josaAfterLabel.containsMatchIn(rest)
        }.toList()

    private fun sectionOf(doc: Doc, occ: IntRange, labelStarts: List<Int>): IntRange {
        val begin = occ.last + 1
        val nextLabel = labelStarts.firstOrNull { it > occ.first && it >= begin } ?: Int.MAX_VALUE
        val row = doc.rowAt(occ.first)
        // 한 행에 계약자·피보험자 라벨이 같이 있는 표 양식이면 그 행 안에서만 찾는다.
        // 다른 행의 번호는 문서 순서 배정(pickGlobalId)에 맡긴다.
        val rowStart = if (row < doc.rows.size) doc.rowEnd(row) - doc.rows[row].text.length else 0
        val sharedRow = labelStarts.any { it != occ.first && it >= rowStart && it < doc.rowEnd(row) }
        val rowLimit = doc.rowEnd(if (sharedRow) row else minOf(row + 2, doc.rows.size - 1))
        val end = minOf(nextLabel, begin + 150, rowLimit, doc.text.length)
        return begin until maxOf(begin, end)
    }

    private fun parsePartySection(doc: Doc, range: IntRange): PartyParse {
        if (range.isEmpty()) return PartyParse(Party(), null)
        val raw = doc.text.substring(range.first, range.last + 1)
        val id = findIdIn(doc, range)
        val nameArea = raw.replace(Regex("""[\d*][\d*\s\-]{5,}[\d*]"""), " ")
        val cleaned = honorific.replace(nameArea.replace('\n', ' '), "$1 ")
            .replace(Regex("""^[\s:|\[\]]+"""), "")

        val corpName = findCorporateName(cleaned)
        val type = when {
            corpName != null -> PartyType.CORPORATION
            id?.isBusiness == true -> PartyType.CORPORATION
            id != null -> PartyType.INDIVIDUAL
            else -> PartyType.UNKNOWN
        }
        val name = corpName ?: when (type) {
            PartyType.CORPORATION -> firstToken(cleaned, Regex("""[가-힣A-Za-z0-9&·()]{2,20}"""))
            else -> firstToken(cleaned, personToken)
        }
        val finalType = if (type == PartyType.UNKNOWN && name != null && personToken.matches(name)) PartyType.INDIVIDUAL else type
        return PartyParse(Party(finalType, name.orEmpty(), id?.digits.orEmpty()), id?.start)
    }

    private fun firstToken(text: String, regex: Regex): String? =
        regex.findAll(text).map { it.value }.firstOrNull { tok ->
            tok !in nameStopWords && !tok.endsWith("번호") && !tok.endsWith("등록") && !tok.endsWith("보험") && tok.any { it in '가'..'힣' || it.isLetter() }
        }

    private fun findCorporateName(text: String): String? {
        val p = corpPrefix.find(text)?.takeIf { it.groupValues[2] !in nameStopWords && !it.groupValues[2].startsWith("사업자") }
        val s = corpSuffix.find(text)?.takeIf { it.groupValues[1] !in nameStopWords }
        val pick = listOfNotNull(p, s).minByOrNull { it.range.first } ?: return null
        return pick.value.trim().replace(Regex("""\s+"""), " ")
    }

    private fun isExcludedId(doc: Doc, start: Int): Boolean {
        val before = doc.text.substring(maxOf(0, start - 12), start)
        return idExclusionBefore.containsMatchIn(before)
    }

    private fun findIdIn(doc: Doc, range: IntRange): IdMatch? {
        val sub = doc.text.substring(range.first, range.last + 1)
        val candidates = ArrayList<IdMatch>()
        rrnRegex.findAll(sub).forEach { m ->
            val digits = if (m.groupValues[3].isNotEmpty()) m.groupValues[3] else m.groupValues[1] + m.groupValues[2]
            candidates += IdMatch(range.first + m.range.first, digits, false)
        }
        brnRegex.findAll(sub).forEach { m ->
            val digits = if (m.groupValues[4].isNotEmpty()) m.groupValues[4] else m.groupValues[1] + m.groupValues[2] + m.groupValues[3]
            val dashed = m.groupValues[4].isEmpty()
            if (dashed || IdValidators.isValidBusinessNumber(digits)) candidates += IdMatch(range.first + m.range.first, digits, true)
        }
        return candidates.filterNot { isExcludedId(doc, it.start) }.minByOrNull { it.start }
    }

    private fun collectIds(doc: Doc): List<IdMatch> = findAllIds(doc).sortedBy { it.start }

    private fun findAllIds(doc: Doc): List<IdMatch> =
        if (doc.text.isEmpty()) emptyList() else {
            val out = ArrayList<IdMatch>()
            var from = 0
            while (from < doc.text.length) {
                val m = findIdIn(doc, from until doc.text.length) ?: break
                out += m
                from = m.start + m.digits.length
            }
            out
        }

    private fun pickGlobalId(ids: List<IdMatch>, used: Set<Int>, type: PartyType): IdMatch? =
        ids.firstOrNull { it.start !in used && (type == PartyType.UNKNOWN || it.isBusiness == (type == PartyType.CORPORATION)) }

    private fun withId(p: Party, id: IdMatch): Party = p.copy(
        idNumber = id.digits,
        type = if (p.type == PartyType.UNKNOWN) (if (id.isBusiness) PartyType.CORPORATION else PartyType.INDIVIDUAL) else p.type,
    )

    private fun nameHit(p: Party, row: Int?): FieldHit =
        if (p.name.isBlank()) FieldHit(FieldFlag.MISSING) else FieldHit(FieldFlag.OK, row)

    private fun idHit(p: Party, row: Int?, guessed: Boolean): FieldHit {
        val id = p.idNumber
        if (id.isBlank()) return FieldHit(FieldFlag.MISSING, row)
        if (p.isIdMasked) return FieldHit(FieldFlag.CHECK, row, "증권에 일부가 가려져 있어 직접 입력이 필요합니다")
        val valid = when (id.length) {
            10 -> IdValidators.isValidBusinessNumber(id)
            13 -> IdValidators.isPlausibleResidentNumber(id)
            else -> false
        }
        return when {
            !valid -> FieldHit(FieldFlag.CHECK, row, "번호 형식 확인 필요")
            guessed -> FieldHit(FieldFlag.CHECK, row, "위치 추정")
            else -> FieldHit(FieldFlag.OK, row)
        }
    }

    // ---------- 금액 ----------

    private val premiumSkipAfter = Regex("""^\s*(?:납입\s*기간|납입\s*주기|납입\s*방법|납입\s*최고|할인|산출|환급|적립|안내|변경|미납|면제)""")
    private val premiumSkipBefore = Regex("""(?:할인\s*전|기본|적립|환급|보장|추가|미납|최초)\s*$""")

    /** (금액, 행 번호, 라벨 문자열) */
    private fun findPremium(doc: Doc): Triple<Long, Int, String>? {
        for ((name, regex) in premiumLabels) {
            for (m in regex.findAll(doc.text)) {
                val after = doc.text.substring(m.range.last + 1, minOf(doc.text.length, m.range.last + 12))
                val before = doc.text.substring(maxOf(0, m.range.first - 6), m.range.first)
                if (premiumSkipAfter.containsMatchIn(after) || premiumSkipBefore.containsMatchIn(before)) continue
                amountAfter(doc, m.range)?.let { return Triple(it.first, it.second, name) }
            }
        }
        return null
    }

    private fun findLabeledAmount(doc: Doc, labels: List<Regex>, skipRows: Set<Int>): Pair<Long, Int>? {
        for (regex in labels) {
            for (m in regex.findAll(doc.text)) {
                if (doc.rowAt(m.range.first) in skipRows) continue
                amountAfter(doc, m.range)?.let { return it }
            }
        }
        return null
    }

    /** 라벨 뒤 같은 행의 첫 금액. 같은 행에 다른 글이 없을 때만 다음 행을 본다. */
    private fun amountAfter(doc: Doc, range: IntRange): Pair<Long, Int>? {
        val row = doc.rowAt(range.first)
        val rest = doc.text.substring(range.last + 1, doc.rowEnd(row).coerceAtLeast(range.last + 1))
        Normalizers.findAmount(rest)?.let { return it to row }
        val trimmed = rest.replace(Regex("""[\s:|()원\[\]]"""), "")
        if (trimmed.isEmpty() && row + 1 < doc.rows.size) {
            Normalizers.findAmount(doc.rows[row + 1].text)?.let { return it to row + 1 }
        }
        return null
    }

    private val coverageNoise = Regex("""^\s*(?:\d{1,2}[.)]|[-•·▶■□○●※])\s*""")

    private fun collectCoverages(rows: List<TextRow>, header: Int): List<Coverage> {
        val out = ArrayList<Coverage>()
        var misses = 0
        for (i in header + 1 until minOf(rows.size, header + 60)) {
            val t = rows[i].text
            if (Regex("""합\s*계|총\s*계|소\s*계""").containsMatchIn(t)) break
            val amounts = Normalizers.findAmounts(t)
            if (amounts.isEmpty()) {
                if (++misses >= 3 && out.isNotEmpty()) break
                continue
            }
            misses = 0
            val (pos, amount) = amounts.first()
            val name = coverageNoise.replace(t.substring(0, pos), "").trim().replace(Regex("""\s{2,}"""), " ")
            if (name.count { it in '가'..'힣' } < 2) continue
            if (Regex("""보험료|납입|기간""").containsMatchIn(name)) continue
            out += Coverage(name.take(60), amount)
            if (out.size >= 50) break
        }
        return out
    }

    // ---------- 납입주기 ----------

    private fun cycleFrom(text: String): PaymentCycle? = when {
        Regex("""일\s*시\s*납|일\s*시""").containsMatchIn(text) -> PaymentCycle.LUMP_SUM
        Regex("""3\s*개\s*월|분\s*기""").containsMatchIn(text) -> PaymentCycle.QUARTERLY
        Regex("""6\s*개\s*월|반\s*기""").containsMatchIn(text) -> PaymentCycle.SEMIANNUAL
        Regex("""월\s*납|매\s*월|(?<![0-9개])월(?![가-힣])""").containsMatchIn(text) -> PaymentCycle.MONTHLY
        Regex("""연\s*납|매\s*년|(?<![0-9])[연년](?![가-힣])""").containsMatchIn(text) -> PaymentCycle.YEARLY
        else -> null
    }

    private fun findCycle(doc: Doc, premiumLabelName: String?): Pair<PaymentCycle, Int?> {
        for (m in cycleLabel.findAll(doc.text)) {
            val sec = doc.text.substring(m.range.last + 1, minOf(doc.rowEnd(doc.rowAt(m.range.first)), m.range.last + 20).coerceAtLeast(m.range.last + 1))
            cycleFrom(sec)?.let { return it to doc.rowAt(m.range.first) }
        }
        if (premiumLabelName == "월보험료") return PaymentCycle.MONTHLY to null
        val global = listOf(
            Regex("""일\s*시\s*납""") to PaymentCycle.LUMP_SUM,
            Regex("""3\s*개\s*월\s*납""") to PaymentCycle.QUARTERLY,
            Regex("""6\s*개\s*월\s*납""") to PaymentCycle.SEMIANNUAL,
            Regex("""월\s*납""") to PaymentCycle.MONTHLY,
            Regex("""연\s*납""") to PaymentCycle.YEARLY,
        ).mapNotNull { (r, c) -> r.find(doc.text)?.let { Triple(it.range.first, c, doc.rowAt(it.range.first)) } }
            .minByOrNull { it.first }
        return global?.let { it.second to it.third } ?: (PaymentCycle.UNKNOWN to null)
    }

    // ---------- 기간 ----------

    private class Period(val start: String?, val end: String?, val row: Int?, val labeled: Boolean)

    private fun findPeriod(doc: Doc): Period? {
        for (m in periodLabel.findAll(doc.text)) {
            val sec = doc.text.substring(m.range.last + 1, minOf(doc.text.length, m.range.last + 90))
            val dates = Normalizers.findDates(sec)
            if (dates.size >= 2) return Period(dates[0].iso, dates[1].iso, doc.rowAt(m.range.first), true)
        }
        val all = Normalizers.findDates(doc.text)
        for (i in 0 until all.size - 1) {
            val between = doc.text.substring(all[i].end, all[i + 1].start)
            if (between.length <= 25 && Regex("""~|부터|-|까지""").containsMatchIn(between)) {
                return Period(all[i].iso, all[i + 1].iso, doc.rowAt(all[i].start), false)
            }
        }
        for (m in periodLabel.findAll(doc.text)) {
            val sec = doc.text.substring(m.range.last + 1, minOf(doc.text.length, m.range.last + 90))
            Normalizers.findDates(sec).firstOrNull()?.let { return Period(it.iso, null, doc.rowAt(m.range.first), true) }
        }
        return null
    }

    private fun findRest(doc: Doc, regex: Regex): String? {
        val m = regex.find(doc.text) ?: return null
        val rowEnd = doc.rowEnd(doc.rowAt(m.range.first))
        val rest = doc.text.substring(m.range.last + 1, rowEnd.coerceAtLeast(m.range.last + 1))
            .replace(Regex("""^[\s:|]+"""), "")
        return rest.split(Regex("""\s{2,}""")).firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
