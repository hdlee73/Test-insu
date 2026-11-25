package io.github.hdlee73.insu.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** 내보낼 때 등록번호 뒷자리를 가릴지의 기본값 */
    val maskIdsOnExport: Boolean = true,
    /** 화면 캡처·최근 앱 미리보기 차단 */
    val secureScreen: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        maskIdsOnExport = prefs.getBoolean(KEY_MASK, true),
        secureScreen = prefs.getBoolean(KEY_SECURE, true),
    )

    fun setMaskIdsOnExport(value: Boolean) {
        prefs.edit().putBoolean(KEY_MASK, value).apply()
        _state.value = _state.value.copy(maskIdsOnExport = value)
    }

    fun setSecureScreen(value: Boolean) {
        prefs.edit().putBoolean(KEY_SECURE, value).apply()
        _state.value = _state.value.copy(secureScreen = value)
    }

    private companion object {
        const val KEY_MASK = "mask_ids_on_export"
        const val KEY_SECURE = "secure_screen"
    }
}
