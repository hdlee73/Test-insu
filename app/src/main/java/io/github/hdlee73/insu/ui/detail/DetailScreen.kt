package io.github.hdlee73.insu.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hdlee73.insu.data.export.ExportFormat
import io.github.hdlee73.insu.domain.Masking
import io.github.hdlee73.insu.domain.model.Party
import io.github.hdlee73.insu.domain.model.PartyType
import io.github.hdlee73.insu.domain.model.Policy
import io.github.hdlee73.insu.ui.components.SectionCard
import io.github.hdlee73.insu.ui.components.SectionTitle
import io.github.hdlee73.insu.ui.components.StatusPill
import io.github.hdlee73.insu.ui.components.authenticateToReveal
import io.github.hdlee73.insu.ui.components.defaultExportName
import io.github.hdlee73.insu.ui.components.findActivity
import io.github.hdlee73.insu.ui.components.formatPremium
import io.github.hdlee73.insu.ui.components.formatWon
import io.github.hdlee73.insu.ui.components.rememberExportFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: DetailViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val revealed by vm.revealed.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val startExport = rememberExportFlow(
        defaultMask = vm.maskDefault,
        suggestedName = { "보험증권_" + ((ui as? DetailUi.Loaded)?.policy?.displayName ?: "증권").replace(Regex("""[\\/:*?"<>|\s]+"""), "_") },
        onExport = vm::export,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("증권 상세") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = {
                    if (ui is DetailUi.Loaded) {
                        IconButton(onClick = { onEdit(vm.policyId) }) { Icon(Icons.Filled.Edit, contentDescription = "수정") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "삭제") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (ui is DetailUi.Loaded) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { startExport(ExportFormat.DOCX) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("docx로 내보내기") }
                        Button(onClick = { startExport(ExportFormat.XLSX) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("xlsx로 내보내기") }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = ui) {
            DetailUi.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            DetailUi.NotFound -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("증권을 찾을 수 없습니다") }
            is DetailUi.Loaded -> DetailContent(
                policy = s.policy,
                revealed = revealed,
                onReveal = { if (revealed) vm.hide() else authenticateToReveal(context.findActivity()) { vm.reveal() } },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 증권을 삭제할까요?") },
            text = { Text("삭제한 증권은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun DetailContent(policy: Policy, revealed: Boolean, onReveal: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(policy.displayName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, fill = false))
            StatusPill(policy.status)
        }
        val sub = listOf(policy.insurer, policy.productName).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("계약 당사자", Modifier.weight(1f))
                TextButton(onClick = onReveal) { Text(if (revealed) "번호 가리기" else "번호 전체 보기") }
            }
            PartyRows("보험계약자", policy.contractor, revealed)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            PartyRows("피보험자", policy.insured, revealed)
        }
        SectionCard {
            SectionTitle("금액과 기간")
            InfoRow("보험가입금액", formatWon(policy.sumInsured))
            InfoRow("보험료", formatPremium(policy.premium, policy.paymentCycle))
            InfoRow("계약 시작일", policy.startDate ?: "-")
            InfoRow("계약 종료일", policy.endDate ?: "-")
        }
        if (policy.coverages.isNotEmpty()) {
            SectionCard {
                SectionTitle("담보 ${policy.coverages.size}건")
                policy.coverages.forEach { InfoRow(it.name, formatWon(it.amount)) }
            }
        }
        SectionCard {
            SectionTitle("기타")
            InfoRow("증권번호", policy.policyNumber.ifBlank { "-" })
            InfoRow("원본 파일", policy.sourceName.ifBlank { "-" })
        }
    }
}

@Composable
private fun PartyRows(title: String, party: Party, revealed: Boolean) {
    InfoRow(title, "${party.name.ifBlank { "-" }} (${party.type.label})")
    InfoRow(
        if (party.type == PartyType.CORPORATION) "사업자등록번호" else "주민등록번호",
        Masking.display(party, revealed),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f))
    }
}
