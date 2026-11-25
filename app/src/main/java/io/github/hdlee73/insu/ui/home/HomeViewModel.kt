package io.github.hdlee73.insu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.domain.model.Policy
import io.github.hdlee73.insu.domain.model.ReviewStatus
import io.github.hdlee73.insu.ui.importer.ImportRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUi(
    val loading: Boolean = true,
    val total: Int = 0,
    val needsReview: Int = 0,
    val expiringSoon: Int = 0,
    val recent: List<Policy> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val ui: StateFlow<HomeUi> = container.policies.observeAll()
        .map { list ->
            val today = LocalDate.now().toString()
            val limit = LocalDate.now().plusDays(30).toString()
            HomeUi(
                loading = false,
                total = list.size,
                needsReview = list.count { it.status == ReviewStatus.NEEDS_REVIEW },
                expiringSoon = list.count { p -> p.endDate?.let { it in today..limit } == true },
                recent = list.take(5),
            )
        }
        .catch { e ->
            CrashLogger.logError("Home", e)
            emit(HomeUi(loading = false, error = "저장된 증권을 불러오지 못했습니다"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    /** 홈에서 고른 파일을 가져오기 화면으로 넘긴다. */
    fun queueImport(request: ImportRequest) {
        container.pendingImport.value = request
    }
}
