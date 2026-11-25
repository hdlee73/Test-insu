package io.github.hdlee73.insu.ui.importer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hdlee73.insu.ui.components.ImportLaunchers
import io.github.hdlee73.insu.ui.components.SectionCard
import io.github.hdlee73.insu.ui.components.rememberImportLaunchers
import io.github.hdlee73.insu.ui.review.ReviewEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: ImportViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val launchers = rememberImportLaunchers { vm.start(it) }

    val hasUnsaved = phase is ImportPhase.Review && review?.let { it.dirty || it.previews.isNotEmpty() } == true
    val tryBack: () -> Unit = {
        if (hasUnsaved) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = hasUnsaved) { confirmDiscard = true }

    LaunchedEffect(phase) {
        (phase as? ImportPhase.Saved)?.let { onSaved(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (phase is ImportPhase.Review) "검수" else "증권 가져오기") },
                navigationIcon = {
                    IconButton(onClick = tryBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val r = review
            if (phase is ImportPhase.Review && r != null) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp, 10.dp)) {
                        if (r.issues.isNotEmpty()) {
                            Text(
                                "확인 안 된 항목 ${r.issues.size}개: 저장하면 '검수 필요'로 표시됩니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Button(onClick = vm::save, enabled = !r.saving, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text(if (r.saving) "저장 중…" else "저장")
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val mod = Modifier.padding(padding).fillMaxSize()
        when (val p = phase) {
            ImportPhase.Idle -> PickContent(launchers, vm::startManual, mod)
            is ImportPhase.Working -> WorkingContent(p, mod)
            is ImportPhase.NeedPassword -> PasswordContent(p.wrong, vm::submitPassword, vm::reset, mod)
            is ImportPhase.Error -> ErrorContent(p.message, vm::reset, mod)
            ImportPhase.Review, is ImportPhase.Saved -> review?.let { r ->
                ReviewEditor(r, vm::updateForm, vm::focus, vm::selectPage, mod)
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("저장하지 않고 나갈까요?") },
            text = { Text("인식한 내용과 수정한 값이 사라집니다.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.reset(); onBack() }) { Text("나가기") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("계속 검수") } },
        )
    }
}

@Composable
private fun PickContent(launchers: ImportLaunchers, onManual: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard {
            Text("증권 파일을 고르세요", style = MaterialTheme.typography.titleLarge)
            Text(
                "PDF, 사진, 카메라 스캔을 지원합니다. 모든 처리는 이 휴대폰 안에서만 이루어집니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(onClick = launchers.pickPdf, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("PDF 파일 선택") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = launchers.pickImages, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("사진 선택 (여러 장 가능)") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = launchers.scan, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("카메라로 스캔") }
        }
        TextButton(onClick = onManual, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("파일 없이 직접 입력") }
    }
}

@Composable
private fun WorkingContent(p: ImportPhase.Working, modifier: Modifier) {
    Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        Text(p.message, style = MaterialTheme.typography.titleMedium)
        if (p.total > 0) {
            Text("${p.current} / ${p.total}쪽", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { p.current.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "인터넷으로 보내지 않고 휴대폰 안에서 읽고 있습니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PasswordContent(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    var pw by rememberSaveable { mutableStateOf("") }
    Column(modifier.padding(16.dp)) {
        SectionCard {
            Text("암호가 걸린 PDF입니다", style = MaterialTheme.typography.titleMedium)
            Text(
                if (wrong) "암호가 맞지 않습니다. 다시 입력하세요." else "보험사에서 안내한 열람 암호(보통 생년월일 6자리)를 입력하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                label = { Text("PDF 암호") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("취소") }
                Button(onClick = { onSubmit(pw) }, enabled = pw.isNotEmpty()) { Text("열기") }
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(16.dp)) {
        SectionCard {
            Text("읽지 못했습니다", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = onRetry) { Text("다른 파일 고르기") }
        }
    }
}
