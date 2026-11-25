package io.github.hdlee73.insu.ui.importer

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.data.source.PasswordRequiredException
import io.github.hdlee73.insu.data.source.ReadResult
import io.github.hdlee73.insu.domain.PolicyForm
import io.github.hdlee73.insu.domain.parser.PolicyParser
import io.github.hdlee73.insu.domain.parser.RowBuilder
import io.github.hdlee73.insu.ui.review.ReviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class ImportKind { PDF, IMAGES }

data class ImportRequest(val kind: ImportKind, val uris: List<Uri>)

sealed interface ImportPhase {
    data object Idle : ImportPhase
    data class Working(val message: String, val current: Int = 0, val total: Int = 0) : ImportPhase
    data class NeedPassword(val wrong: Boolean) : ImportPhase
    data object Review : ImportPhase
    data class Error(val message: String) : ImportPhase
    data class Saved(val id: Long) : ImportPhase
}

class ImportViewModel(private val container: AppContainer) : ViewModel() {

    private val _phase = MutableStateFlow<ImportPhase>(ImportPhase.Idle)
    val phase: StateFlow<ImportPhase> = _phase.asStateFlow()

    private val _review = MutableStateFlow<ReviewState?>(null)
    val review: StateFlow<ReviewState?> = _review.asStateFlow()

    private var job: Job? = null
    private var lastRequest: ImportRequest? = null

    init {
        container.pendingImport.value?.let {
            container.pendingImport.value = null
            start(it)
        }
    }

    fun start(request: ImportRequest, password: String? = null) {
        job?.cancel()
        lastRequest = request
        _review.value = null
        _phase.value = ImportPhase.Working("파일을 여는 중")
        job = viewModelScope.launch {
            try {
                val reader = container.documentReader
                val name = withContext(Dispatchers.IO) { reader.displayName(request.uris.first()) }
                val progress: (Int, Int) -> Unit = { cur, total ->
                    _phase.value = ImportPhase.Working("글자를 읽는 중", cur, total)
                }
                val read: ReadResult = when (request.kind) {
                    ImportKind.PDF -> reader.readPdf(request.uris.first(), password, progress)
                    ImportKind.IMAGES -> reader.readImages(request.uris, progress)
                }
                _phase.value = ImportPhase.Working("항목을 찾는 중")
                val result = withContext(Dispatchers.Default) {
                    PolicyParser.parse(RowBuilder.build(read.fragments), name)
                }
                val previews: List<ImageBitmap> = read.previews.map { it.asImageBitmap() }
                _review.value = ReviewState(
                    form = PolicyForm.from(result.policy),
                    hits = result.hits,
                    rows = result.rows,
                    previews = previews,
                    usedOcr = read.usedOcr,
                    textFound = read.fragments.isNotEmpty(),
                )
                _phase.value = ImportPhase.Review
            } catch (e: CancellationException) {
                throw e
            } catch (e: PasswordRequiredException) {
                _phase.value = ImportPhase.NeedPassword(e.wrongPassword)
            } catch (e: OutOfMemoryError) {
                CrashLogger.logError("Import-OOM", e)
                _phase.value = ImportPhase.Error("파일이 너무 커서 읽지 못했습니다. 쪽 수를 줄이거나 사진으로 찍어 다시 시도하세요.")
            } catch (e: MlKitException) {
                CrashLogger.logError("Import-MLKit", e)
                _phase.value = ImportPhase.Error("문자 인식 모듈을 준비하지 못했습니다. 잠시 후 다시 시도하세요.")
            } catch (e: IOException) {
                CrashLogger.logError("Import-IO", e)
                _phase.value = ImportPhase.Error("파일을 읽을 수 없습니다. 손상되었거나 지원하지 않는 형식일 수 있습니다.")
            } catch (e: SecurityException) {
                CrashLogger.logError("Import-Permission", e)
                _phase.value = ImportPhase.Error("파일 접근 권한이 없습니다. 다시 선택해 주세요.")
            } catch (e: Exception) {
                CrashLogger.logError("Import", e)
                _phase.value = ImportPhase.Error("증권을 읽는 중 문제가 생겼습니다: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun submitPassword(password: String) {
        lastRequest?.let { start(it, password) }
    }

    fun reset() {
        job?.cancel()
        _review.value = null
        _phase.value = ImportPhase.Idle
    }

    fun updateForm(form: PolicyForm) = _review.update { it?.copy(form = form, dirty = true, error = null) }

    fun focus(field: PolicyForm.Field?) = _review.update { it?.focus(field) }

    fun selectPage(page: Int) = _review.update { it?.copy(page = page) }

    /** 수동 입력: 인식 없이 빈 양식으로 시작. */
    fun startManual() {
        job?.cancel()
        _review.value = ReviewState(form = PolicyForm(sourceName = "직접 입력"))
        _phase.value = ImportPhase.Review
    }

    fun save() {
        val state = _review.value ?: return
        if (state.saving) return
        _review.value = state.copy(saving = true)
        viewModelScope.launch {
            try {
                val id = container.policies.save(state.form.toPolicy(System.currentTimeMillis()))
                _review.update { it?.copy(dirty = false, saving = false) }
                _phase.value = ImportPhase.Saved(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashLogger.logError("ImportSave", e)
                // 입력한 내용을 잃지 않도록 검수 화면에 머문 채 알린다.
                _review.update { it?.copy(saving = false, error = "저장하지 못했습니다: ${e.message ?: "알 수 없는 오류"}") }
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
