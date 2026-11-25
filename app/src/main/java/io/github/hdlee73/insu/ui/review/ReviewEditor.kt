package io.github.hdlee73.insu.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hdlee73.insu.domain.PolicyForm
import io.github.hdlee73.insu.domain.PolicyForm.Field
import io.github.hdlee73.insu.domain.model.PartyType
import io.github.hdlee73.insu.domain.model.PaymentCycle
import io.github.hdlee73.insu.domain.parser.FieldFlag
import io.github.hdlee73.insu.domain.parser.TextRow
import io.github.hdlee73.insu.ui.components.SectionCard
import io.github.hdlee73.insu.ui.components.SectionTitle
import io.github.hdlee73.insu.ui.components.formatWon
import io.github.hdlee73.insu.ui.theme.LocalExtraColors

/**
 * 시안 C: 위쪽 원본 미리보기 + 아래쪽 추출 필드.
 * 필드를 누르면 원본에서 그 값을 찾은 줄을 주황 상자로 표시한다.
 */
@Composable
fun ReviewEditor(
    state: ReviewState,
    onChange: (PolicyForm) -> Unit,
    onFocus: (Field?) -> Unit,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val issues = remember(state.issues) { state.issues.groupBy { it.field } }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.previews.isNotEmpty()) {
            SourcePreview(state.previews, state.page, state.highlight, onSelectPage)
            if (!state.textFound) {
                Notice("글자를 찾지 못했습니다. 더 밝고 반듯하게 찍은 사진으로 다시 시도하거나 직접 입력하세요.")
            } else {
                Notice(
                    (if (state.usedOcr) "사진·스캔본을 문자 인식으로 읽었습니다." else "PDF의 글자를 그대로 읽었습니다.") +
                        " 주황색 칸은 꼭 확인하세요. 칸을 누르면 원본 위치가 표시됩니다.",
                )
            }
        }
        state.error?.let { Notice(it, warning = true) }

        PartySection(
            title = "보험계약자",
            type = form.contractorType,
            name = form.contractorName,
            id = form.contractorId,
            nameField = Field.CONTRACTOR_NAME,
            idField = Field.CONTRACTOR_ID,
            state = state,
            issues = issues,
            onType = { onChange(form.copy(contractorType = it)) },
            onName = { onChange(form.copy(contractorName = it)) },
            onId = { onChange(form.copy(contractorId = it)) },
            onFocus = onFocus,
        )
        PartySection(
            title = "피보험자",
            type = form.insuredType,
            name = form.insuredName,
            id = form.insuredId,
            nameField = Field.INSURED_NAME,
            idField = Field.INSURED_ID,
            state = state,
            issues = issues,
            onType = { onChange(form.copy(insuredType = it)) },
            onName = { onChange(form.copy(insuredName = it)) },
            onId = { onChange(form.copy(insuredId = it)) },
            onFocus = onFocus,
            action = { TextButton(onClick = { onChange(form.copyContractorToInsured()) }) { Text("계약자와 같음") } },
        )

        SectionCard {
            SectionTitle("금액")
            FormField("보험가입금액(원)", form.sumInsured, Field.SUM_INSURED, state, issues, onFocus, KeyboardType.Number,
                hint = amountHint(form.sumInsured)) { onChange(form.copy(sumInsured = it)) }
            FormField("보험료(원)", form.premium, Field.PREMIUM, state, issues, onFocus, KeyboardType.Number,
                hint = amountHint(form.premium)) { onChange(form.copy(premium = it)) }
            Text("납입주기", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaymentCycle.entries.forEach { c ->
                    FilterChip(selected = form.paymentCycle == c, onClick = { onChange(form.copy(paymentCycle = c)) }, label = { Text(c.label) })
                }
            }
            if (form.coverages.isNotEmpty()) {
                Text(
                    "담보 ${form.coverages.size}건 (가입금액은 합계 기준)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                form.coverages.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(c.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(formatWon(c.amount), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        SectionCard {
            SectionTitle("보험계약기간")
            FormField("시작일 (YYYY-MM-DD)", form.startDate, Field.START_DATE, state, issues, onFocus, KeyboardType.Number) {
                onChange(form.copy(startDate = it))
            }
            FormField("종료일 (YYYY-MM-DD)", form.endDate, Field.END_DATE, state, issues, onFocus, KeyboardType.Number) {
                onChange(form.copy(endDate = it))
            }
        }

        SectionCard {
            SectionTitle("추가 정보 (선택)")
            PlainField("보험사", form.insurer) { onChange(form.copy(insurer = it)) }
            PlainField("상품명", form.productName) { onChange(form.copy(productName = it)) }
            PlainField("증권번호", form.policyNumber) { onChange(form.copy(policyNumber = it)) }
        }
        Spacer(Modifier.height(80.dp))
    }
}

private fun amountHint(raw: String): String? =
    raw.filter { it.isDigit() }.toLongOrNull()?.let { "= ${formatWon(it)}" }

@Composable
private fun PartySection(
    title: String,
    type: PartyType,
    name: String,
    id: String,
    nameField: Field,
    idField: Field,
    state: ReviewState,
    issues: Map<Field, List<PolicyForm.Issue>>,
    onType: (PartyType) -> Unit,
    onName: (String) -> Unit,
    onId: (String) -> Unit,
    onFocus: (Field?) -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title, Modifier.weight(1f))
            action?.invoke()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == PartyType.INDIVIDUAL, onClick = { onType(PartyType.INDIVIDUAL) }, label = { Text("개인") })
            FilterChip(selected = type == PartyType.CORPORATION, onClick = { onType(PartyType.CORPORATION) }, label = { Text("법인") })
        }
        FormField(if (type == PartyType.CORPORATION) "법인명" else "성명", name, nameField, state, issues, onFocus, KeyboardType.Text, onValue = onName)
        val idLabel = when (type) {
            PartyType.CORPORATION -> "사업자등록번호 (숫자 10자리)"
            PartyType.INDIVIDUAL -> "주민등록번호 (숫자 13자리)"
            PartyType.UNKNOWN -> "등록번호"
        }
        FormField(idLabel, id, idField, state, issues, onFocus, KeyboardType.Number, onValue = onId)
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    field: Field,
    state: ReviewState,
    issues: Map<Field, List<PolicyForm.Issue>>,
    onFocus: (Field?) -> Unit,
    keyboard: KeyboardType,
    hint: String? = null,
    onValue: (String) -> Unit,
) {
    val extra = LocalExtraColors.current
    val issue = issues[field]?.firstOrNull()
    val hit = state.hit(field)
    val needsCheck = hit != null && hit.flag != FieldFlag.OK && state.isUntouched(field)
    val showError = issue != null && value.isNotBlank() && !needsCheck
    val warnColor = extra.warning
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        isError = showError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = {
            val text = when {
                needsCheck -> hit?.note ?: if (hit?.flag == FieldFlag.MISSING) "찾지 못했습니다. 직접 입력하세요" else "확인이 필요합니다"
                showError -> issue?.message
                value.isBlank() -> "필수 항목"
                else -> hint ?: hit?.note
            }
            if (text != null) Text(text, color = if (needsCheck) warnColor else Color.Unspecified)
        },
        colors = if (needsCheck) {
            OutlinedTextFieldDefaults.colors(unfocusedBorderColor = warnColor, focusedBorderColor = warnColor, unfocusedLabelColor = warnColor)
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .onFocusChanged { if (it.isFocused) onFocus(field) },
    )
}

@Composable
private fun PlainField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun Notice(text: String, warning: Boolean = false) {
    val extra = LocalExtraColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) extra.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (warning) extra.warningContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
    )
}

@Composable
private fun SourcePreview(pages: List<ImageBitmap>, page: Int, highlight: TextRow?, onSelectPage: (Int) -> Unit) {
    val bitmap = pages[page.coerceIn(0, pages.size - 1)]
    var scale by remember(page) { mutableFloatStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val highlightColor = LocalExtraColors.current.warning
    SectionCard(contentPadding = PaddingValues(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White)
                .clipToBounds()
                .pointerInput(page) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                .transformable(transform, canPan = { scale > 1f }),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            ) {
                Image(bitmap, contentDescription = "증권 원본 ${page + 1}쪽", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                if (highlight != null && highlight.page == page) {
                    Canvas(Modifier.fillMaxSize()) {
                        val s = minOf(size.width / bitmap.width, size.height / bitmap.height)
                        val w = bitmap.width * s
                        val h = bitmap.height * s
                        val dx = (size.width - w) / 2f
                        val dy = (size.height - h) / 2f
                        val pad = 3.dp.toPx()
                        val topLeft = Offset(dx + highlight.left * w - pad, dy + highlight.top * h - pad)
                        val rect = Size((highlight.right - highlight.left) * w + pad * 2, (highlight.bottom - highlight.top) * h + pad * 2)
                        drawRect(highlightColor.copy(alpha = 0.16f), topLeft, rect)
                        drawRect(highlightColor, topLeft, rect, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pages.indices.forEach { i ->
                    FilterChip(selected = i == page, onClick = { onSelectPage(i) }, label = { Text("${i + 1}쪽") })
                }
            }
        }
        Text(
            "두 손가락으로 확대, 두 번 눌러 원래 크기",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
}
