package io.github.hdlee73.insu.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.domain.PolicyForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 저장된 증권 수정. 검수 화면(시안 C)과 같은 편집기를 쓰되 원본 미리보기는 없다(원본은 보관하지 않음). */
class EditViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val id: Long = handle.get<Long>("id") ?: 0L

    private val _state = MutableStateFlow<ReviewState?>(null)
    val state: StateFlow<ReviewState?> = _state.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val p = container.policies.get(id)
                if (p == null) _missing.value = true else _state.value = ReviewState(form = PolicyForm.from(p))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashLogger.logError("EditLoad", e)
                _missing.value = true
            }
        }
    }

    fun update(form: PolicyForm) = _state.update { it?.copy(form = form, dirty = true, error = null) }

    fun save() {
        val s = _state.value ?: return
        if (s.saving) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            try {
                container.policies.save(s.form.toPolicy(System.currentTimeMillis()))
                _state.update { it?.copy(saving = false, dirty = false) }
                _done.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashLogger.logError("EditSave", e)
                _state.update { it?.copy(saving = false, error = "저장하지 못했습니다: ${e.message ?: "알 수 없는 오류"}") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: EditViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val dirty = state?.dirty == true

    LaunchedEffect(done) { if (done) onBack() }
    BackHandler(enabled = dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("증권 수정") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (dirty) {
                            confirmDiscard = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            state?.let { s ->
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = vm::save,
                        enabled = !s.saving,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp, 10.dp).height(48.dp),
                    ) { Text(if (s.saving) "저장 중…" else "저장") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val s = state
        when {
            missing -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("증권을 찾을 수 없습니다") }
            s == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> ReviewEditor(s, vm::update, onFocus = {}, onSelectPage = {}, modifier = Modifier.padding(padding))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("수정 내용을 버릴까요?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("버리기") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("계속 수정") } },
        )
    }
}
