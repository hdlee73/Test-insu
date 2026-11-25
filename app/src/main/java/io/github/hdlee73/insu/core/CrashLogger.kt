package io.github.hdlee73.insu.core

import android.content.Context
import android.os.Build
import android.os.Process
import io.github.hdlee73.insu.BuildConfig
import io.github.hdlee73.insu.domain.Masking
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * 마켓 밖에서 배포하므로 크래시·치명적 오류를 앱 내부 저장소(files/logs)에 텍스트로 남긴다.
 * 주민번호·사업자번호 형태의 숫자는 기록 전에 지운다. 최근 [MAX_FILES]개만 보관한다.
 */
object CrashLogger {
    private const val DIR = "logs"
    private const val MAX_FILES = 20

    @Volatile
    private var logDir: File? = null

    fun install(context: Context) {
        logDir = File(context.filesDir, DIR).apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write("crash", "thread=${thread.name}", error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** 앱이 계속 돌 수 있는 오류(파일 읽기 실패 등)도 원인 추적용으로 남긴다. */
    fun logError(tag: String, error: Throwable) {
        runCatching { write("error", tag, error) }
    }

    fun files(): List<File> =
        logDir?.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun clear() {
        files().forEach { it.delete() }
    }

    @Synchronized
    private fun write(kind: String, tag: String, error: Throwable) {
        val dir = logDir ?: return
        val now = Date()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(now)
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("[$kind] ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)}")
            appendLine("app=증권노트 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("tag=$tag")
            appendLine()
            append(trace)
        }
        File(dir, "$kind-$stamp.txt").writeText(Masking.scrub(text))
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_FILES)?.forEach { it.delete() }
    }
}
