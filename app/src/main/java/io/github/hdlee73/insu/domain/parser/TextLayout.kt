package io.github.hdlee73.insu.domain.parser

/**
 * OCR 또는 PDF 글자층에서 나온 글자 조각. 좌표는 페이지 크기로 나눈 0~1 값이다.
 * 좌표를 모르면 [hasBox]가 false다.
 */
data class TextFragment(
    val text: String,
    val page: Int,
    val left: Float = -1f,
    val top: Float = -1f,
    val right: Float = -1f,
    val bottom: Float = -1f,
) {
    val hasBox: Boolean get() = left >= 0f && top >= 0f && right > left && bottom > top
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

/** 같은 줄에 놓인 조각을 왼쪽부터 이어 붙인 한 행. 열 사이 간격은 공백 두 칸으로 표시한다. */
data class TextRow(
    val text: String,
    val page: Int,
    val left: Float = -1f,
    val top: Float = -1f,
    val right: Float = -1f,
    val bottom: Float = -1f,
) {
    val hasBox: Boolean get() = left >= 0f && top >= 0f && right > left && bottom > top
}

/**
 * 표 형태 증권은 OCR 결과 순서가 열 단위로 섞이기 쉽다.
 * 그래서 텍스트 순서 대신 좌표로 같은 높이의 조각을 한 행으로 묶는다.
 */
object RowBuilder {

    fun build(fragments: List<TextFragment>): List<TextRow> {
        val result = ArrayList<TextRow>()
        fragments.groupBy { it.page }.toSortedMap().forEach { (page, items) ->
            val boxed = items.filter { it.hasBox && it.text.isNotBlank() }
            val unboxed = items.filter { !it.hasBox && it.text.isNotBlank() }
            result += groupRows(page, boxed)
            unboxed.forEach { f -> f.text.lines().filter { it.isNotBlank() }.forEach { result += TextRow(it.trim(), page) } }
        }
        return result
    }

    private fun groupRows(page: Int, items: List<TextFragment>): List<TextRow> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedBy { it.centerY }
        val rows = ArrayList<MutableList<TextFragment>>()
        for (f in sorted) {
            val last = rows.lastOrNull()
            if (last != null) {
                val rowCenter = last.map { it.centerY }.average().toFloat()
                val rowHeight = last.map { it.height }.average().toFloat()
                val tolerance = minOf(rowHeight, f.height) * 0.55f
                if (kotlin.math.abs(f.centerY - rowCenter) <= tolerance) {
                    last += f
                    continue
                }
            }
            rows += mutableListOf(f)
        }
        return rows.map { row ->
            val parts = row.sortedBy { it.left }
            val sb = StringBuilder()
            parts.forEachIndexed { i, f ->
                if (i > 0) {
                    val prev = parts[i - 1]
                    val gap = f.left - prev.right
                    val charWidth = (prev.right - prev.left) / prev.text.length.coerceAtLeast(1)
                    sb.append(if (gap > charWidth * 1.5f) "  " else " ")
                }
                sb.append(f.text.trim())
            }
            TextRow(
                text = sb.toString(),
                page = page,
                left = parts.minOf { it.left },
                top = parts.minOf { it.top },
                right = parts.maxOf { it.right },
                bottom = parts.maxOf { it.bottom },
            )
        }
    }
}
