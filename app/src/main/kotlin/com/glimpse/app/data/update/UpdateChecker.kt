package com.glimpse.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.glimpse.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

// The repo is private, so the unauthenticated public GitHub API isn't
// usable here — a fine-grained PAT (Contents: Read-only, scoped to just
// this repo) is baked into the build via BuildConfig (see build.gradle.kts
// and .github/workflows/build.yml), populated from a repo secret. Blank in
// builds where that secret isn't set (e.g. local/PR builds), in which case
// every function here is a silent no-op.
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private val client = OkHttpClient()

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val token = BuildConfig.GLIMPSE_RELEASES_TOKEN
        if (token.isBlank()) return@withContext null

        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.RELEASES_REPO_OWNER}/${BuildConfig.RELEASES_REPO_NAME}/releases/latest")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "checkForUpdate: HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                parseLatestRelease(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkForUpdate failed", e)
            null
        }
    }

    private fun parseLatestRelease(body: String): UpdateInfo? {
        val json = JSONObject(body)
        // Tag format is "v<versionName>-build<runNumber>" (see
        // .github/workflows/build.yml) — the run number is exactly the
        // versionCode that build was compiled with, so comparing it
        // directly avoids any semver-parsing ambiguity.
        val tagName = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val releaseVersionCode = tagName.substringAfterLast("-build").toIntOrNull() ?: return null
        if (releaseVersionCode <= BuildConfig.VERSION_CODE) return null

        val assets = json.optJSONArray("assets") ?: return null
        var assetUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith("-release.apk")) {
                assetUrl = asset.optString("url").takeIf { it.isNotBlank() }
                break
            }
        }
        val downloadUrl = assetUrl ?: return null

        return UpdateInfo(
            versionName = tagName.removePrefix("v").substringBefore("-build"),
            versionCode = releaseVersionCode,
            downloadAssetUrl = downloadUrl,
            releaseNotes = sanitizeReleaseNotes(json.optString("body"))
        )
    }

    // GitHub's auto-generated body (generate_release_notes in build.yml) is
    // meant for the GitHub UI — "* ... by @user in <PR url>" bullet lines and
    // a trailing "**Full Changelog**: <compare url>" line. This is shown
    // in-app only, so strip that GitHub-specific link boilerplate rather than
    // dumping raw github.com URLs into the update screen.
    private fun sanitizeReleaseNotes(rawBody: String): String =
        rawBody.lineSequence()
            .filterNot { it.trimStart().startsWith("**Full Changelog**", ignoreCase = true) }
            .map { line ->
                line
                    .replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1")
                    .replace(Regex(""" in https?://\S+"""), "")
                    .replace(Regex("""https?://\S+"""), "")
                    .trimEnd()
            }
            .joinToString("\n")
            .trim()

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val token = BuildConfig.GLIMPSE_RELEASES_TOKEN
        if (token.isBlank()) return@withContext null

        try {
            val request = Request.Builder()
                .url(info.downloadAssetUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/octet-stream")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    Log.w(TAG, "downloadApk: HTTP ${response.code}")
                    return@withContext null
                }

                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(dir, "glimpse-update.apk")
                val total = body.contentLength()

                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var copied = 0L
                        var read = input.read(buffer)
                        while (read >= 0) {
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress(copied.toFloat() / total)
                            read = input.read(buffer)
                        }
                    }
                }
                outFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadApk failed", e)
            null
        }
    }

    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
