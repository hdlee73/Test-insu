package io.github.hdlee73.insu.data.export

import android.content.Context
import android.net.Uri
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.domain.model.Policy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 사용자가 고른 저장 위치(SAF Uri)에 파일을 쓴다. */
class ExportService(private val context: Context) {

    suspend fun export(policies: List<Policy>, format: ExportFormat, maskIds: Boolean, target: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(policies.isNotEmpty()) { "내보낼 증권이 없습니다" }
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                val stream = context.contentResolver.openOutputStream(target, "wt")
                    ?: error("저장 위치를 열 수 없습니다")
                stream.buffered().use { PolicyExporter.write(format, policies, maskIds, it, stamp) }
            }.onFailure { CrashLogger.logError("Export", it) }
        }
}
