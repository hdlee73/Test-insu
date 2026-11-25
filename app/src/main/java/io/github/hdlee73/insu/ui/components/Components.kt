package io.github.hdlee73.insu.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.hdlee73.insu.InsuApp
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.domain.model.PaymentCycle
import io.github.hdlee73.insu.domain.model.ReviewStatus
import io.github.hdlee73.insu.ui.theme.LocalExtraColors
import java.text.NumberFormat
import java.util.Locale

/** 수동 DI로 ViewModel을 만든다. 내비게이션 항목마다 하나씩 생기고 화면을 벗어나면 정리된다. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val app = LocalContext.current.applicationContext as InsuApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container, createSavedStateHandle()) } })
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private val wonFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

fun formatWon(v: Long?): String = v?.let { wonFormat.format(it) + "원" } ?: "-"

fun formatPremium(v: Long?, cycle: PaymentCycle): String =
    formatWon(v) + if (v != null && cycle != PaymentCycle.UNKNOWN) " (${cycle.label})" else ""

/** FSS_insights 스타일 그라데이션 머리말. */
@Composable
fun HeroBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val extra = LocalExtraColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd))),
        content = content,
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun StatusPill(status: ReviewStatus) {
    val extra = LocalExtraColors.current
    when (status) {
        ReviewStatus.NEEDS_REVIEW -> Pill("검수 필요", extra.warningContainer, extra.warning)
        ReviewStatus.DONE -> Pill("완료", extra.successContainer, extra.success)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}
