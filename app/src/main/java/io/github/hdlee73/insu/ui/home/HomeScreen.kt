package io.github.hdlee73.insu.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hdlee73.insu.ui.components.HeroBox
import io.github.hdlee73.insu.ui.components.PolicyCard
import io.github.hdlee73.insu.ui.components.SectionCard
import io.github.hdlee73.insu.ui.components.findActivity
import io.github.hdlee73.insu.ui.components.rememberImportLaunchers
import io.github.hdlee73.insu.ui.theme.LocalExtraColors

/** 시안 B: 가져오기 우선 대시보드. */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenDrawer: () -> Unit,
    onStartImport: () -> Unit,
    onOpenPolicy: (Long) -> Unit,
    onOpenList: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launchers = rememberImportLaunchers { request ->
        vm.queueImport(request)
        onStartImport()
    }

    // 홈은 시작 화면이므로 뒤로가기 한 번에 바로 닫히지 않게 두 번 눌러 종료한다.
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2_000) {
            context.findActivity()?.finish()
        } else {
            lastBack = now
            Toast.makeText(context, "한 번 더 누르면 종료합니다", Toast.LENGTH_SHORT).show()
        }
    }

    val extra = LocalExtraColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "hero") {
            HeroBox {
                Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, contentDescription = "메뉴", tint = extra.onHero) }
                    Text("증권노트", color = extra.onHero, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 52.dp)) {
                    Text("증권을 불러오세요", color = extra.onHero, style = MaterialTheme.typography.headlineMedium)
                    Text("휴대폰 안에서만 읽고 암호화해 저장합니다", color = extra.onHero.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "actions") {
            // 머리말 아래로 살짝 겹치는 입력 방식 카드
            SectionCard(Modifier.padding(horizontal = 16.dp).overlapHero()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile("PDF", "파일 선택", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary, Modifier.weight(1f), launchers.pickPdf)
                    ActionTile("사진", "앨범에서", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary, Modifier.weight(1f), launchers.pickImages)
                    ActionTile("스캔", "카메라", extra.warningContainer, extra.warning, Modifier.weight(1f), launchers.scan)
                }
            }
        }
        item(key = "summary") {
            SectionCard(Modifier.padding(horizontal = 16.dp).overlapHero()) {
                Row(Modifier.fillMaxWidth()) {
                    Stat("저장된 증권", ui.total.toString(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    Stat("검수 대기", ui.needsReview.toString(), if (ui.needsReview > 0) extra.warning else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    Stat("30일 내 만기", ui.expiringSoon.toString(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
        }
        item(key = "recentTitle") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).overlapHero(), verticalAlignment = Alignment.CenterVertically) {
                Text("최근 증권", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (ui.total > 0) TextButton(onClick = onOpenList) { Text("전체 보기") }
            }
        }
        when {
            ui.error != null -> item(key = "error") {
                Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            }
            !ui.loading && ui.recent.isEmpty() -> item(key = "empty") {
                SectionCard(Modifier.padding(horizontal = 16.dp).overlapHero()) {
                    Text("아직 저장된 증권이 없습니다", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "위의 PDF·사진·스캔 중 하나로 첫 증권을 불러오세요. 읽은 내용은 저장 전에 직접 확인할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            else -> items(ui.recent, key = { it.id }) { p ->
                PolicyCard(p, onClick = { onOpenPolicy(p.id) }, modifier = Modifier.padding(horizontal = 16.dp).overlapHero())
            }
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
    }
}

/** 카드들을 머리말 쪽으로 끌어올려 겹쳐 보이게 한다(시안 B). offset은 측정에 영향을 주지 않아 가볍다. */
private fun Modifier.overlapHero(): Modifier = this.offset(y = (-36).dp)

@Composable
private fun ActionTile(title: String, subtitle: String, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
    }
}
