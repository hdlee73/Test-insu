package io.github.hdlee73.insu.ui.review

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import io.github.hdlee73.insu.domain.PolicyForm
import io.github.hdlee73.insu.domain.parser.FieldHit
import io.github.hdlee73.insu.domain.parser.FieldKey
import io.github.hdlee73.insu.domain.parser.TextRow

/** 검수(시안 C)·수정 화면 공용 상태. */
@Immutable
data class ReviewState(
    val form: PolicyForm,
    /** 인식 직후 값. 사용자가 고치지 않은 칸만 '확인 필요'로 칠한다. */
    val original: PolicyForm = form,
    val hits: Map<FieldKey, FieldHit> = emptyMap(),
    val rows: List<TextRow> = emptyList(),
    val previews: List<ImageBitmap> = emptyList(),
    val usedOcr: Boolean = false,
    val textFound: Boolean = true,
    val page: Int = 0,
    val highlight: TextRow? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** 저장 전 검증 결과. 인스턴스마다 한 번만 계산한다. */
    val issues: List<PolicyForm.Issue> = form.validate()

    fun hit(field: PolicyForm.Field): FieldHit? = hits[FieldKey.valueOf(field.name)]

    fun isUntouched(field: PolicyForm.Field): Boolean = form.valueOf(field) == original.valueOf(field)

    /** 필드를 누르면 그 값을 찾은 원본 위치를 표시한다. */
    fun focus(field: PolicyForm.Field?): ReviewState {
        val row = field?.let { hit(it)?.rowIndex }?.let { rows.getOrNull(it) }
        return if (row != null && row.hasBox && row.page < previews.size) copy(highlight = row, page = row.page) else copy(highlight = null)
    }
}
