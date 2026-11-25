package io.github.hdlee73.insu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.data.settings.AppSettings
import io.github.hdlee73.insu.ui.components.SectionCard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setMask(v: Boolean) = container.settings.setMaskIdsOnExport(v)
    fun setSecure(v: Boolean) = container.settings.setSecureScreen(v)

    fun deleteAll() {
        viewModelScope.launch {
            runCatching {
                val ids = container.policies.observeAll().first().map { it.id }
                container.policies.delete(ids)
                ids.size
            }.onSuccess { _messages.send("증권 ${it}건을 모두 삭제했습니다") }
                .onFailure { CrashLogger.logError("DeleteAll", it); _messages.send("삭제하지 못했습니다") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onOpenDrawer: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정·보안") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, contentDescription = "메뉴") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                ToggleRow("내보낼 때 등록번호 뒷자리 가림", "내보내기 창에서 기본으로 선택될 값입니다", s.maskIdsOnExport, vm::setMask)
                ToggleRow("화면 캡처 막기", "스크린샷과 최근 앱 미리보기에 증권 내용이 남지 않게 합니다", s.secureScreen, vm::setSecure)
            }
            SectionCard {
                Text("데이터 보호", style = MaterialTheme.typography.titleMedium)
                Text(
                    "증권 정보는 이 휴대폰 안의 암호화된 데이터베이스(SQLCipher)에만 저장됩니다. 암호 키는 Android 보안 저장소(Keystore)에 있어 파일을 복사해도 열 수 없습니다. 원본 PDF·사진은 읽은 뒤 바로 지우며, 클라우드 백업에서도 제외됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                OutlinedButton(onClick = { confirmDeleteAll = true }) { Text("저장된 증권 모두 삭제", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("모든 증권을 삭제할까요?") },
            text = { Text("이 휴대폰에 저장된 증권이 모두 지워지며 되돌릴 수 없습니다.") },
            confirmButton = { TextButton(onClick = { confirmDeleteAll = false; vm.deleteAll() }) { Text("모두 삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
