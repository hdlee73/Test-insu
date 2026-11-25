package io.github.hdlee73.insu.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.hdlee73.insu.BuildConfig
import io.github.hdlee73.insu.R
import io.github.hdlee73.insu.core.AppContainer
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.data.update.UpdateResult
import io.github.hdlee73.insu.ui.components.SectionCard
import io.github.hdlee73.insu.ui.components.SectionTitle
import io.github.hdlee73.insu.ui.theme.LocalExtraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val APP_NAME = "증권노트"
const val AUTHOR = "이현덕 with Claude"
const val AUTHOR_EMAIL = "hdlee73@gmail.com"
const val AUTHOR_HOME = "https://hdlee73.github.io/Home/"

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class Done(val result: UpdateResult) : UpdateUi
}

class AboutViewModel(private val container: AppContainer) : ViewModel() {
    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private val _logs = MutableStateFlow<List<File>>(emptyList())
    val logs: StateFlow<List<File>> = _logs.asStateFlow()

    init {
        refreshLogs()
    }

    fun checkUpdate() {
        if (_update.value == UpdateUi.Checking) return
        _update.value = UpdateUi.Checking
        viewModelScope.launch { _update.value = UpdateUi.Done(container.updates.check()) }
    }

    fun refreshLogs() {
        viewModelScope.launch { _logs.value = withContext(Dispatchers.IO) { CrashLogger.files() } }
    }

    fun clearLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CrashLogger.clear() }
            refreshLogs()
        }
    }
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "열 수 있는 앱이 없습니다: $url", Toast.LENGTH_LONG).show()
    }
}

private fun Context.shareLog(file: File) {
    try {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(AUTHOR_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "[$APP_NAME] 오류 기록 ${BuildConfig.VERSION_NAME}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "오류 기록 보내기"))
    } catch (e: Exception) {
        CrashLogger.logError("ShareLog", e)
        Toast.makeText(this, "오류 기록을 보낼 수 없습니다", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(vm: AboutViewModel, onOpenDrawer: () -> Unit) {
    val update by vm.update.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalExtraColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정보·사용 방법") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, contentDescription = "메뉴") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(MaterialTheme.shapes.large)
                            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd))),
                    ) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(APP_NAME, style = MaterialTheme.typography.titleLarge)
                        Text("버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "보험증권(PDF·사진)에서 계약자·피보험자·가입금액·보험료·계약기간을 읽어 휴대폰에 암호화 저장하고, 고른 증권을 docx·xlsx로 내보냅니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            SectionCard {
                SectionTitle("만든이")
                InfoLine("만든이", AUTHOR)
                InfoLine("이메일", AUTHOR_EMAIL) { context.openUrl("mailto:$AUTHOR_EMAIL") }
                InfoLine("홈페이지", AUTHOR_HOME) { context.openUrl(AUTHOR_HOME) }
            }

            SectionCard {
                SectionTitle("사용 방법")
                listOf(
                    "홈에서 PDF·사진·스캔 중 하나를 눌러 증권을 불러옵니다. 사진은 여러 장(여러 쪽)을 한 번에 고를 수 있습니다.",
                    "앱이 계약자, 피보험자, 등록번호, 보험가입금액, 보험료, 계약기간을 찾아 검수 화면에 채웁니다.",
                    "주황색 칸은 확인이 필요한 값입니다. 칸을 누르면 원본에서 그 값을 찾은 위치가 주황 상자로 표시됩니다.",
                    "증권에 주민등록번호 뒷자리가 가려져 있으면 직접 입력하세요. 등록번호는 '-' 없이 숫자만 저장됩니다.",
                    "저장을 누르면 휴대폰 안 암호화 저장소에 보관됩니다. 확인 안 된 항목이 있으면 '검수 필요'로 표시됩니다.",
                    "내 증권에서 길게 눌러 여러 건을 고른 뒤 docx(1건당 한 쪽 표) 또는 xlsx(1건당 한 행)로 내보냅니다.",
                    "내보낼 때 등록번호 뒷자리를 가릴지 고를 수 있습니다. 기본은 가림이며 설정에서 바꿀 수 있습니다.",
                    "상세 화면의 '번호 전체 보기'는 지문·화면 잠금 확인 후 10초 동안만 보입니다.",
                ).forEachIndexed { i, step ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(width = 24.dp, height = 20.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "인식 결과는 증권 양식과 사진 품질에 따라 틀릴 수 있으니 저장 전에 꼭 확인하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionCard {
                SectionTitle("업데이트")
                when (val u = update) {
                    UpdateUi.Idle -> Text("GitHub에 올라온 최신 버전을 확인합니다.", style = MaterialTheme.typography.bodySmall)
                    UpdateUi.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("확인 중…", modifier = Modifier.padding(start = 8.dp))
                    }
                    is UpdateUi.Done -> UpdateResultView(u.result) { context.openUrl(it) }
                }
                Button(onClick = vm::checkUpdate, enabled = update != UpdateUi.Checking, modifier = Modifier.padding(top = 8.dp)) {
                    Text("업데이트 확인")
                }
            }

            SectionCard {
                SectionTitle("오류 기록")
                Text(
                    if (logs.isEmpty()) "저장된 오류 기록이 없습니다."
                    else "오류 기록 ${logs.size}개가 있습니다. 문제가 생기면 최근 기록을 만든이에게 보내 주세요. 등록번호는 기록에서 자동으로 지워집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (logs.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { context.shareLog(logs.first()) }) { Text("최근 기록 보내기") }
                        TextButton(onClick = vm::clearLogs) { Text("기록 지우기") }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateResultView(result: UpdateResult, open: (String) -> Unit) {
    when (result) {
        is UpdateResult.Available -> Column {
            Text("새 버전 ${result.version}이 있습니다", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (result.notes.isNotBlank()) {
                Text(result.notes.take(400), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
            }
            Text(
                "APK를 내려받아 설치하세요. 처음이면 '출처를 알 수 없는 앱 설치' 허용이 필요합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                result.apkUrl?.let { url -> Button(onClick = { open(url) }) { Text("APK 내려받기") } }
                OutlinedButton(onClick = { open(result.pageUrl) }) { Text("릴리스 페이지") }
            }
        }
        is UpdateResult.UpToDate -> Text("최신 버전(${result.version})을 쓰고 있습니다.", style = MaterialTheme.typography.bodyMedium)
        UpdateResult.NoRelease -> Text("아직 배포된 릴리스가 없습니다.", style = MaterialTheme.typography.bodyMedium)
        is UpdateResult.Failed -> Text(result.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun InfoLine(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(width = 72.dp, height = 20.dp))
        Text(value, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
