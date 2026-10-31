package com.miguellobato84.recetas

import org.json.JSONArray
import java.net.URL

internal const val MAX_ANDROID_VERSION_CODE = 2_100_000_000
internal const val MAX_RELEASE_PAGES = 10
internal const val RELEASES_PER_PAGE = 100

internal data class ReleaseAsset(val name: String, val url: String)

internal data class ReleaseCandidate(
    val versionCode: Int,
    val apk: ReleaseAsset?,
    val checksum: ReleaseAsset?,
)

internal fun selectLatestRelease(
    releases: List<ReleaseCandidate>,
    installedVersionCode: Long,
): ReleaseCandidate? = releases
    .asSequence()
    .filter { it.versionCode.toLong() > installedVersionCode && it.apk != null && it.checksum != null }
    .maxByOrNull { it.versionCode }

internal fun parseReleasePage(json: String): List<ReleaseCandidate> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val release = array.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) continue
            val tag = release.optString("tag_name")
            val match = Regex("^v([1-9][0-9]*)$").matchEntire(tag) ?: continue
            val version = match.groupValues[1].toLongOrNull()
                ?.takeIf { it <= MAX_ANDROID_VERSION_CODE }?.toInt() ?: continue

            val assets = release.optJSONArray("assets") ?: JSONArray()
            val matchingApks = mutableListOf<ReleaseAsset>()
            val matchingChecksums = mutableListOf<ReleaseAsset>()
            val expectedApk = "recetas-v$version.apk"
            val expectedChecksum = "$expectedApk.sha256"
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.optJSONObject(assetIndex) ?: continue
                val name = asset.optString("name")
                if (name != expectedApk && name != expectedChecksum) continue
                val url = asset.optString("browser_download_url")
                if (!isTrustedReleaseAssetUrl(url, version, name)) continue
                when (name) {
                    expectedApk -> matchingApks += ReleaseAsset(name, url)
                    expectedChecksum -> matchingChecksums += ReleaseAsset(name, url)
                }
            }
            add(ReleaseCandidate(version, matchingApks.singleOrNull(), matchingChecksums.singleOrNull()))
        }
    }
}

private fun isTrustedReleaseAssetUrl(value: String, version: Int, assetName: String): Boolean = try {
    val url = URL(value)
    url.protocol == "https" && url.host.equals("github.com", ignoreCase = true) &&
        url.path == "/miguellobato84/recipes-android/releases/download/v$version/$assetName"
} catch (_: Exception) {
    false
}

internal fun parseSha256Asset(body: String, apkName: String): ByteArray {
    val match = Regex("^([0-9a-fA-F]{64})(?:[ \\t]+\\*?${Regex.escape(apkName)})?[\\r\\n]*$")
        .matchEntire(body) ?: throw IllegalArgumentException("Invalid SHA-256 asset")
    return match.groupValues[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
