package io.github.hdlee73.insu.ui.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.data.export.ExportFormat
import io.github.hdlee73.insu.domain.model.Policy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListUi(
    val loading: Boolean = true,
    val items: List<Policy> = emptyList(),
    val query: String = "",
    val error: String? = null,
)

/** 내 증권 목록 + 시안 D의 체크박스 선택·일괄 내보내기. */
class PolicyListViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val maskDefault: Boolean get() = container.settings.state.value.maskIdsOnExport

    val ui: StateFlow<ListUi> = combine(container.policies.observeAll(), query) { list, q ->
        val t = q.trim()
        val filtered = if (t.isEmpty()) list else list.filter { p ->
            listOf(p.contractor.name, p.insured.name, p.insurer, p.productName, p.policyNumber).any { it.contains(t, ignoreCase = true) }
        }
        // 지워진 증권은 선택에서도 뺀다.
        val ids = list.mapTo(HashSet()) { it.id }
        _selection.value = _selection.value.filterTo(HashSet()) { it in ids }
        ListUi(loading = false, items = filtered, query = q)
    }.catch { e ->
        CrashLogger.logError("PolicyList", e)
        emit(ListUi(loading = false, error = "증권 목록을 불러오지 못했습니다"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUi())

    fun setQuery(q: String) { query.value = q }

    fun toggle(id: Long) {
        _selection.value = _selection.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    fun selectAllVisible() {
        _selection.value = ui.value.items.mapTo(HashSet()) { it.id }
    }

    fun clearSelection() { _selection.value = emptySet() }

    fun deleteSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { container.policies.delete(ids) }
                .onSuccess { _selection.value = emptySet(); _messages.send("${ids.size}건을 삭제했습니다") }
                .onFailure { CrashLogger.logError("Delete", it); _messages.send("삭제하지 못했습니다") }
        }
    }

    fun export(format: ExportFormat, mask: Boolean, uri: Uri) {
        val ids = _selection.value
        if (ids.isEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val policies = container.policies.getByIds(ids)
                container.exporter.export(policies, format, mask, uri)
                    .onSuccess { _messages.send("${policies.size}건을 ${format.label}로 저장했습니다") }
                    .onFailure { _messages.send("저장하지 못했습니다: ${it.message ?: "알 수 없는 오류"}") }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                CrashLogger.logError("ExportList", e)
                _messages.send("저장하지 못했습니다")
            } finally {
                _busy.value = false
            }
        }
    }
}
