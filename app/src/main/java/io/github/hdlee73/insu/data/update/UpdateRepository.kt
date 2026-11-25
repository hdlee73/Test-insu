package io.github.hdlee73.insu.data.update

import io.github.hdlee73.insu.BuildConfig
import io.github.hdlee73.insu.core.CrashLogger
import io.github.hdlee73.insu.domain.VersionCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

sealed interface UpdateResult {
    data class Available(val version: String, val notes: String, val pageUrl: String, val apkUrl: String?) : UpdateResult
    data class UpToDate(val version: String) : UpdateResult
    data object NoRelease : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/** GitHub Releases API로 최신 버전을 확인한다. 이 앱이 인터넷을 쓰는 유일한 곳이다. */
class UpdateRepository(private val repo: String = BuildConfig.GITHUB_REPO) {

    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): UpdateResult = withContext(Dispatchers.IO) {
        val conn = try {
            URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpsURLConnection
        } catch (e: IOException) {
            return@withContext UpdateResult.Failed("네트워크에 연결할 수 없습니다")
        }
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "InsuNote-Android")
            when (val code = conn.responseCode) {
                200 -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parse(body, currentVersion)
                }
                404 -> UpdateResult.NoRelease
                403, 429 -> UpdateResult.Failed("잠시 후 다시 시도하세요 (요청 한도 초과)")
                else -> UpdateResult.Failed("서버 응답 오류 ($code)")
            }
        } catch (e: IOException) {
            UpdateResult.Failed("네트워크에 연결할 수 없습니다")
        } catch (e: Exception) {
            CrashLogger.logError("UpdateCheck", e)
            UpdateResult.Failed("업데이트 정보를 읽지 못했습니다")
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String, current: String): UpdateResult {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").ifBlank { return UpdateResult.NoRelease }
        if (VersionCompare.compare(tag, current) <= 0) return UpdateResult.UpToDate(current)
        val assets = json.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    break
                }
            }
        }
        return UpdateResult.Available(
            version = tag.removePrefix("v"),
            notes = json.optString("body").take(2000),
            pageUrl = json.optString("html_url").takeIf { it.startsWith("https://github.com/") } ?: "https://github.com/$repo/releases",
            apkUrl = apk,
        )
    }
}
