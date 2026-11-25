package io.github.hdlee73.insu.core

import android.content.Context
import io.github.hdlee73.insu.data.PolicyRepository
import io.github.hdlee73.insu.data.db.DatabaseProvider
import io.github.hdlee73.insu.data.export.ExportService
import io.github.hdlee73.insu.data.settings.SettingsRepository
import io.github.hdlee73.insu.data.source.DocumentReader
import io.github.hdlee73.insu.data.update.UpdateRepository
import io.github.hdlee73.insu.ui.importer.ImportRequest
import kotlinx.coroutines.flow.MutableStateFlow

/** 수동 DI 컨테이너. 앱 수명 동안 하나씩만 만든다. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings by lazy { SettingsRepository(appContext) }
    val policies by lazy { PolicyRepository(DatabaseProvider(appContext)) }
    val documentReader by lazy { DocumentReader(appContext) }
    val updates by lazy { UpdateRepository() }
    val exporter by lazy { ExportService(appContext) }

    /** 홈에서 고른 파일을 가져오기 화면으로 넘기는 통로. 한 번 읽으면 비운다. */
    val pendingImport = MutableStateFlow<ImportRequest?>(null)
}
