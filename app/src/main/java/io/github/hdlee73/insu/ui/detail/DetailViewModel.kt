package io.github.hdlee73.insu.ui.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.data.export.ExportFormat
import io.github.hdlee73.insu.domain.model.Policy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUi {
    data object Loading : DetailUi
    data object NotFound : DetailUi
    data class Loaded(val policy: Policy) : DetailUi
}

class DetailViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {

    val policyId: Long = handle.get<Long>("id") ?: 0L

    val ui: StateFlow<DetailUi> = container.policies.observe(policyId)
        .map<Policy?, DetailUi> { p -> if (p == null) DetailUi.NotFound else DetailUi.Loaded(p) }
        .catch { e -> CrashLogger.logError("Detail", e); emit(DetailUi.NotFound) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUi.Loading)

    private val _revealed = MutableStateFlow(false)
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()
    private var revealJob: Job? = null

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val maskDefault: Boolean get() = container.settings.state.value.maskIdsOnExport

    /** 본인 확인 후 10초만 전체 번호를 보여 준다. */
    fun reveal() {
        revealJob?.cancel()
        _revealed.value = true
        revealJob = viewModelScope.launch {
            delay(10_000)
            _revealed.value = false
        }
    }

    fun hide() {
        revealJob?.cancel()
        _revealed.value = false
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { container.policies.delete(listOf(policyId)) }
                .onSuccess { onDone() }
                .onFailure { CrashLogger.logError("DetailDelete", it); _messages.send("삭제하지 못했습니다") }
        }
    }

    fun export(format: ExportFormat, mask: Boolean, uri: Uri) {
        val p = (ui.value as? DetailUi.Loaded)?.policy ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                container.exporter.export(listOf(p), format, mask, uri)
                    .onSuccess { _messages.send("${format.label}로 저장했습니다") }
                    .onFailure { _messages.send("저장하지 못했습니다: ${it.message ?: "알 수 없는 오류"}") }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _busy.value = false
            }
        }
    }
}
