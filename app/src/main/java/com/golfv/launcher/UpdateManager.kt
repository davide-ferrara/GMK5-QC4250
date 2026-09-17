package com.golfv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal sealed interface UpdateResult {
    data class Current(val version: String) : UpdateResult
    data class Downloaded(val apk: File, val version: String) : UpdateResult
    data object NoRelease : UpdateResult
    data object InvalidApk : UpdateResult
    data object Failed : UpdateResult
}

internal class UpdateManager(private val context: Context) {
    suspend fun downloadLatest(
        onProgress: suspend (version: String, percent: Int) -> Unit,
    ): UpdateResult =
        withContext(Dispatchers.IO) {
            try {
                val release = getLatestRelease()
                if (!isNewerVersion(release.tag, BuildConfig.VERSION_NAME)) {
                    return@withContext UpdateResult.Current(release.tag)
                }

                val expectedDigest = release.asset.digest
                    ?: release.digestAsset?.let(::downloadDigest)
                    ?: return@withContext UpdateResult.InvalidApk
                val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
                val destination = File(updateDirectory, UPDATE_FILE)
                val actualDigest = downloadApk(release, destination, onProgress)

                if (!actualDigest.equals(expectedDigest, ignoreCase = true) ||
                    !isValidUpdate(destination)
                ) {
                    destination.delete()
                    return@withContext UpdateResult.InvalidApk
                }

                UpdateResult.Downloaded(destination, release.tag)
            } catch (error: HttpStatusException) {
                if (error.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    UpdateResult.NoRelease
                } else {
                    UpdateResult.Failed
                }
            } catch (_: Exception) {
                UpdateResult.Failed
            }
        }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    fun install(apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.isSuccess

    private fun getLatestRelease(): Release {
        val connection = openConnection(RELEASE_API)
        val status = connection.responseCode
        if (status !in 200..299) throw HttpStatusException(status)
        val root = connection.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
        val assets = root.getJSONArray("assets")
        var apkAsset: Asset? = null
        val allAssets = buildList {
            repeat(assets.length()) { index ->
                val asset = assets.getJSONObject(index).toAsset()
                add(asset)
                if (apkAsset == null && asset.name.endsWith(".apk", ignoreCase = true)) {
                    apkAsset = asset
                }
            }
        }
        val apk = apkAsset ?: throw IllegalStateException("Release has no APK asset")
        val digestAsset = allAssets.firstOrNull {
            it.name.equals("${apk.name}.sha256", ignoreCase = true) ||
                it.name.equals("${apk.name.removeSuffix(".apk")}.sha256", ignoreCase = true)
        }
        return Release(
            tag = root.getString("tag_name").removePrefix("v"),
            asset = apk,
            digestAsset = digestAsset,
        )
    }

    private fun JSONObject.toAsset(): Asset {
        val githubDigest = optString("digest")
            .takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")
            ?.takeIf(::isSha256)
        return Asset(
            name = getString("name"),
            url = getString("browser_download_url"),
            size = getLong("size"),
            digest = githubDigest,
        )
    }

    private fun downloadDigest(asset: Asset): String {
        val connection = openConnection(asset.url)
        val status = connection.responseCode
        if (status !in 200..299) throw HttpStatusException(status)
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        return SHA256_REGEX.find(text)?.value
            ?: throw IllegalStateException("Invalid SHA-256 asset")
    }

    private suspend fun downloadApk(
        release: Release,
        destination: File,
        onProgress: suspend (version: String, percent: Int) -> Unit,
    ): String {
        val connection = openConnection(release.asset.url)
        val status = connection.responseCode
        if (status !in 200..299) throw HttpStatusException(status)
        val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: release.asset.size
        val digest = MessageDigest.getInstance("SHA-256")
        var downloadedBytes = 0L
        var lastPercent = -1

        connection.inputStream.buffered().use { input ->
            FileOutputStream(destination, false).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    downloadedBytes += count
                    val percent = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).coerceIn(0, 100).toInt()
                    } else {
                        0
                    }
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(release.tag, percent)
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isValidUpdate(apk: File): Boolean {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val candidate = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return false
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        if (candidate.packageName != context.packageName ||
            candidate.longVersionCode <= BuildConfig.VERSION_CODE
        ) {
            return false
        }
        val candidateSigners = candidate.signingInfo?.apkContentsSigners
            ?.map { sha256(it.toByteArray()) }
            ?.toSet()
        val installedSigners = installed.signingInfo?.apkContentsSigners
            ?.map { sha256(it.toByteArray()) }
            ?.toSet()
        return !candidateSigners.isNullOrEmpty() && candidateSigners == installedSigners
    }

    private fun openConnection(address: String): HttpURLConnection {
        val url = URL(address)
        require(url.protocol == "https")
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GolfMk5Launcher/${BuildConfig.VERSION_NAME}")
        }
    }

    private data class Release(
        val tag: String,
        val asset: Asset,
        val digestAsset: Asset?,
    )

    private data class Asset(
        val name: String,
        val url: String,
        val size: Long,
        val digest: String?,
    )

    private class HttpStatusException(val statusCode: Int) : Exception()

    private companion object {
        const val RELEASE_API =
            "https://api.github.com/repos/davide-ferrara/GMK5-QC4250/releases/latest"
        const val UPDATE_DIRECTORY = "updates"
        const val UPDATE_FILE = "golf-mk5-launcher-update.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val NETWORK_TIMEOUT_MS = 20_000
        val SHA256_REGEX = Regex("(?i)[a-f0-9]{64}")

        fun isSha256(value: String): Boolean = SHA256_REGEX.matches(value)

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        fun isNewerVersion(latest: String, current: String): Boolean {
            val latestParts = latest.substringBefore('-').split('.').mapNotNull(String::toIntOrNull)
            val currentParts = current.substringBefore('-').split('.').mapNotNull(String::toIntOrNull)
            if (latestParts.isEmpty() || currentParts.isEmpty()) return latest != current
            repeat(maxOf(latestParts.size, currentParts.size)) { index ->
                val latestPart = latestParts.getOrElse(index) { 0 }
                val currentPart = currentParts.getOrElse(index) { 0 }
                if (latestPart != currentPart) return latestPart > currentPart
            }
            return false
        }
    }
}
