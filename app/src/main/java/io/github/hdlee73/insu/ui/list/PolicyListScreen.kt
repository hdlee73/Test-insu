package io.github.hdlee73.insu.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hdlee73.insu.data.export.ExportFormat
import io.github.hdlee73.insu.ui.components.PolicyCard
import io.github.hdlee73.insu.ui.components.defaultExportName
import io.github.hdlee73.insu.ui.components.rememberExportFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyListScreen(
    vm: PolicyListViewModel,
    onOpenDrawer: () -> Unit,
    onOpenPolicy: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    val selecting = selectMode || selection.isNotEmpty()
    val exitSelection: () -> Unit = {
        selectMode = false
        vm.clearSelection()
    }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = selecting) { exitSelection() }
    BackHandler(enabled = !selecting && searching) { searching = false; vm.setQuery("") }

    val startExport = rememberExportFlow(
        defaultMask = vm.maskDefault,
        suggestedName = { defaultExportName(selection.size) },
        onExport = vm::export,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) "${selection.size}개 선택" else "내 증권 ${ui.items.size}") },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = exitSelection) { Icon(Icons.Filled.Close, contentDescription = "선택 취소") }
                    } else {
                        IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, contentDescription = "메뉴") }
                    }
                },
                actions = {
                    if (selecting) {
                        TextButton(onClick = vm::selectAllVisible) { Text("전체") }
                    } else {
                        IconButton(onClick = { searching = !searching; if (!searching) vm.setQuery("") }) {
                            Icon(Icons.Filled.Search, contentDescription = "검색")
                        }
                        if (ui.items.isNotEmpty()) TextButton(onClick = { selectMode = true }) { Text("선택") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (selection.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                        OutlinedButton(onClick = { startExport(ExportFormat.DOCX) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("docx로") }
                        Button(onClick = { startExport(ExportFormat.XLSX) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("xlsx로") }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("증권 추가") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searching) {
                item(key = "search") {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text("이름·보험사·상품명·증권번호") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (!selecting && ui.items.isNotEmpty()) {
                item(key = "hint") {
                    Text(
                        "길게 누르면 여러 건을 골라 한 번에 내보낼 수 있습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                ui.error != null -> item(key = "error") { Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                !ui.loading && ui.items.isEmpty() -> item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (ui.query.isBlank()) "저장된 증권이 없습니다" else "검색 결과가 없습니다",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> items(ui.items, key = { it.id }) { p ->
                    PolicyCard(
                        policy = p,
                        selected = if (selecting) p.id in selection else null,
                        onClick = { if (selecting) vm.toggle(p.id) else onOpenPolicy(p.id) },
                        onLongClick = { selectMode = true; vm.toggle(p.id) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${selection.size}건을 삭제할까요?") },
            text = { Text("삭제한 증권은 되돌릴 수 없습니다.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteSelected() }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
