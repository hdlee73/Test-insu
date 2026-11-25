package io.github.hdlee73.insu.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.data.export.ExportFormat
import io.github.hdlee73.insu.ui.importer.ImportKind
import io.github.hdlee73.insu.ui.importer.ImportRequest
import io.github.hdlee73.insu.ui.theme.LocalExtraColors
import java.time.LocalDate

/** 가져오기 버튼 세 개(PDF·사진·스캔)가 쓰는 실행기 묶음. */
class ImportLaunchers(val pickPdf: () -> Unit, val pickImages: () -> Unit, val scan: () -> Unit)

@Composable
fun rememberImportLaunchers(onPicked: (ImportRequest) -> Unit): ImportLaunchers {
    val context = LocalContext.current
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(ImportRequest(ImportKind.PDF, listOf(uri)))
    }
    val images = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onPicked(ImportRequest(ImportKind.IMAGES, uris))
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
            if (pages.isNotEmpty()) onPicked(ImportRequest(ImportKind.IMAGES, pages))
        }
    }
    return remember(pdf, images, scanner) {
        ImportLaunchers(
            pickPdf = { pdf.launch(arrayOf("application/pdf")) },
            pickImages = { images.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            scan = {
                val activity = context.findActivity()
                if (activity == null) {
                    Toast.makeText(context, "스캐너를 열 수 없습니다", Toast.LENGTH_SHORT).show()
                } else {
                    val options = GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setPageLimit(10)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                        .addOnSuccessListener { sender -> scanner.launch(IntentSenderRequest.Builder(sender).build()) }
                        .addOnFailureListener { e ->
                            CrashLogger.logError("DocumentScanner", e)
                            Toast.makeText(context, "문서 스캐너를 쓸 수 없습니다. Google Play 서비스를 확인하세요.", Toast.LENGTH_LONG).show()
                        }
                }
            },
        )
    }
}

/**
 * 내보내기 흐름: 등록번호 가림 선택 → 저장 위치 선택(SAF) → [onExport] 호출.
 * 반환된 함수에 형식을 넘겨 시작한다.
 */
@Composable
fun rememberExportFlow(
    defaultMask: Boolean,
    suggestedName: () -> String,
    onExport: (format: ExportFormat, mask: Boolean, uri: android.net.Uri) -> Unit,
): (ExportFormat) -> Unit {
    var pendingMask by rememberSaveable { mutableStateOf(defaultMask) }
    var dialogFormat by rememberSaveable { mutableStateOf<String?>(null) }

    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.XLSX.mimeType)) { uri ->
        if (uri != null) onExport(ExportFormat.XLSX, pendingMask, uri)
    }
    val docx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.DOCX.mimeType)) { uri ->
        if (uri != null) onExport(ExportFormat.DOCX, pendingMask, uri)
    }

    dialogFormat?.let { name ->
        val format = ExportFormat.valueOf(name)
        var mask by remember(name) { mutableStateOf(defaultMask) }
        AlertDialog(
            onDismissRequest = { dialogFormat = null },
            title = { Text("${format.label}로 내보내기") },
            text = {
                Column {
                    Text("주민등록번호·사업자등록번호를 어떻게 넣을까요?", style = MaterialTheme.typography.bodyMedium)
                    MaskOption("뒷자리 가림 (권장)", "예: 9001011******", mask) { mask = true }
                    MaskOption("전체 번호 포함", "파일을 받는 사람이 번호 전체를 볼 수 있습니다", !mask, warning = true) { mask = false }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMask = mask
                    dialogFormat = null
                    val fileName = "${suggestedName()}.${format.extension}"
                    when (format) {
                        ExportFormat.XLSX -> xlsx.launch(fileName)
                        ExportFormat.DOCX -> docx.launch(fileName)
                    }
                }) { Text("저장 위치 선택") }
            },
            dismissButton = { TextButton(onClick = { dialogFormat = null }) { Text("취소") } },
        )
    }
    return { format -> dialogFormat = format.name }
}

@Composable
private fun MaskOption(title: String, desc: String, selected: Boolean, warning: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) LocalExtraColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun defaultExportName(count: Int): String = "보험증권_${LocalDate.now()}" + if (count > 1) "_${count}건" else ""

/**
 * 민감 번호 전체 보기 전에 생체 인증 또는 기기 잠금 확인.
 * 잠금이 아예 설정되지 않은 기기는 확인 없이 [onSuccess]를 부른다.
 */
fun authenticateToReveal(activity: Activity?, onSuccess: () -> Unit) {
    val fa = activity as? FragmentActivity ?: return onSuccess()
    val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    if (BiometricManager.from(fa).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onSuccess()
        return
    }
    val prompt = BiometricPrompt(
        fa,
        ContextCompat.getMainExecutor(fa),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("등록번호 전체 보기")
            .setSubtitle("본인 확인 후 10초 동안 보입니다")
            .setAllowedAuthenticators(authenticators)
            .build(),
    )
}
