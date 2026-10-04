package com.notmugil.uta.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface UpdateResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val currentVersion: String,
        val releaseUrl: String,
        val changelog: String?,
        val apkDownloadUrl: String?
    ) : UpdateResult

    data class UpToDate(
        val currentVersion: String
    ) : UpdateResult

    data class Error(
        val message: String
    ) : UpdateResult
}

object AppUpdateManager {
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/NotMugil/uta/releases"
    private const val GITHUB_RELEASES_URL = "https://github.com/NotMugil/uta/releases"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun isNewerVersion(current: String, latest: String): Boolean {
        val currentSemVer = parseSemVer(current)
        val latestSemVer = parseSemVer(latest)

        val maxCoreLength = maxOf(currentSemVer.coreParts.size, latestSemVer.coreParts.size)
        for (i in 0 until maxCoreLength) {
            val c = currentSemVer.coreParts.getOrElse(i) { 0 }
            val l = latestSemVer.coreParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }

        val currentPre = currentSemVer.preRelease
        val latestPre = latestSemVer.preRelease

        if (currentPre == null && latestPre != null) {
            return false
        }
        if (currentPre != null && latestPre == null) {
            return true
        }
        if (currentPre != null && latestPre != null) {
            return comparePreRelease(latestPre, currentPre) > 0
        }

        return false
    }

    private data class ParsedSemVer(
        val coreParts: List<Int>,
        val preRelease: String?
    )

    private fun parseSemVer(versionStr: String): ParsedSemVer {
        val clean = versionStr.trim()
            .trimStart('v', 'V')
            .substringBefore('+')
        val coreStr = clean.substringBefore('-')
        val preRelease = if (clean.contains('-')) clean.substringAfter('-').takeIf { it.isNotBlank() } else null

        val coreParts = coreStr.split('.').mapNotNull { part ->
            part.filter { it.isDigit() }.toIntOrNull()
        }
        return ParsedSemVer(coreParts, preRelease)
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val tokensA = tokenizePreRelease(a)
        val tokensB = tokenizePreRelease(b)
        val maxLen = maxOf(tokensA.size, tokensB.size)
        for (i in 0 until maxLen) {
            val tokA = tokensA.getOrNull(i) ?: return -1
            val tokB = tokensB.getOrNull(i) ?: return 1
            val numA = tokA.toIntOrNull()
            val numB = tokB.toIntOrNull()
            if (numA != null && numB != null) {
                if (numA != numB) return numA.compareTo(numB)
            } else {
                val cmp = tokA.compareTo(tokB, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return 0
    }

    private fun tokenizePreRelease(preRelease: String): List<String> {
        val regex = Regex("([0-9]+|[a-zA-Z]+)")
        return regex.findAll(preRelease).map { it.value }.toList()
    }

    suspend fun checkForUpdates(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val currentVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Uta-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext UpdateResult.UpToDate(currentVersion = currentVersion)
                }
                return@withContext UpdateResult.Error("GitHub API returned HTTP ${response.code}")
            }

            val body = response.body.string()
            if (body.isBlank()) return@withContext UpdateResult.Error("Empty response body")
            val trimmed = body.trim()
            val json = if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                if (array.length() == 0) {
                    return@withContext UpdateResult.UpToDate(currentVersion = currentVersion)
                }
                array.getJSONObject(0)
            } else {
                JSONObject(trimmed)
            }

            val tagName = json.optString("tag_name", "").ifBlank { json.optString("name", "") }
            val releaseUrl = json.optString("html_url", GITHUB_RELEASES_URL).ifBlank { GITHUB_RELEASES_URL }
            val changelog = json.optString("body", "").takeIf { it.isNotBlank() }

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i)
                    val assetName = asset?.optString("name", "").orEmpty()
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset?.optString("browser_download_url")
                        break
                    }
                }
            }

            if (tagName.isNotBlank() && isNewerVersion(current = currentVersion, latest = tagName)) {
                UpdateResult.UpdateAvailable(
                    latestVersion = tagName,
                    currentVersion = currentVersion,
                    releaseUrl = releaseUrl,
                    changelog = changelog,
                    apkDownloadUrl = apkUrl
                )
            } else {
                UpdateResult.UpToDate(currentVersion = currentVersion)
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.localizedMessage ?: "Failed to check for updates")
        }
    }

    fun openReleasePage(context: Context, url: String = GITHUB_RELEASES_URL) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Browser not available
        }
    }
}
