package com.miguellobato84.recetas

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "RecetasUpdate"
private const val MAX_RELEASE_JSON_BYTES = 2 * 1024 * 1024
private const val MAX_APK_BYTES = 100L * 1024 * 1024
private const val MAX_CHECKSUM_BYTES = 256
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val MAX_TOTAL_TIME_MS = 90_000L
private const val ACTION_INSTALL_STATUS = "com.miguellobato84.recetas.INSTALL_STATUS"

data class AppUpdateStatus(val message: String? = null, val error: Boolean = false)

/** Process-wide startup check. No activity or recipe-cache operation is involved. */
internal object AppUpdateManager {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStatus = MutableStateFlow(AppUpdateStatus())
    val status: StateFlow<AppUpdateStatus> = mutableStatus.asStateFlow()

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                checkForUpdate(appContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Startup app update check failed", error)
                publish(AppUpdateStatus("No se pudieron comprobar las actualizaciones.", error = true))
            }
        }
    }

    fun publish(status: AppUpdateStatus) {
        mutableStatus.value = status
    }

    private suspend fun checkForUpdate(context: Context) = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val installed = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
        val currentVersion = installed.longVersionCode
        val release = GithubReleaseClient.findLatestNewer(currentVersion) ?: return@withContext

        if (!packageManager.canRequestPackageInstalls()) {
            publish(AppUpdateStatus("Actualización pendiente: permite instalar aplicaciones desde Recetas en Ajustes."))
            return@withContext
        }

        val updateDirectory = File(context.cacheDir, "app-update")
        if (!updateDirectory.isDirectory && !updateDirectory.mkdirs()) {
            throw IllegalStateException("Cannot create private update directory")
        }
        val apkFile = File(updateDirectory, "recetas-v${release.versionCode}.apk")
        val checksumFile = File(updateDirectory, "recetas-v${release.versionCode}.apk.sha256")
        try {
            val apkAsset = requireNotNull(release.apk)
            val checksumAsset = requireNotNull(release.checksum)
            BoundedHttps.download(apkAsset.url, apkFile, MAX_APK_BYTES)
            val checksum = BoundedHttps.read(checksumAsset.url, MAX_CHECKSUM_BYTES.toLong())
            checksumFile.writeBytes(checksum)
            val expectedHash = parseSha256Asset(checksumFile.readText(Charsets.US_ASCII), apkAsset.name)
            if (!MessageDigest.isEqual(expectedHash, sha256(apkFile))) {
                throw IllegalArgumentException("APK checksum mismatch")
            }
            validateApk(context, packageManager, apkFile, release.versionCode, currentVersion, installed)
            submitInstall(context, apkFile)
        } finally {
            apkFile.delete()
            checksumFile.delete()
            updateDirectory.delete()
        }
    }

    private fun validateApk(
        context: Context,
        packageManager: PackageManager,
        file: File,
        releaseVersionCode: Int,
        installedVersionCode: Long,
        installedInfo: PackageInfo,
    ) {
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IllegalArgumentException("Downloaded file is not a valid APK")
        if (archive.packageName != context.packageName) throw IllegalArgumentException("Unexpected package ID")
        if (archive.longVersionCode != releaseVersionCode.toLong() || archive.longVersionCode <= installedVersionCode) {
            throw IllegalArgumentException("Unexpected app version")
        }
        val appInfo = archive.applicationInfo ?: throw IllegalArgumentException("Missing APK metadata")
        if (appInfo.minSdkVersion > Build.VERSION.SDK_INT) {
            throw IllegalArgumentException("APK is not compatible with this Android version")
        }
        val installedSigning = installedInfo.signingInfo
            ?: throw IllegalArgumentException("Installed app signing identity is unavailable")
        val archiveSigning = archive.signingInfo
            ?: throw IllegalArgumentException("Downloaded app signing identity is unavailable")
        if (!sameSigningIdentity(installedSigning, archiveSigning)) {
            throw IllegalArgumentException("APK signing identity does not match installed app")
        }
    }

    private fun sameSigningIdentity(
        installed: android.content.pm.SigningInfo,
        incoming: android.content.pm.SigningInfo,
    ): Boolean {
        if (installed.hasMultipleSigners() || incoming.hasMultipleSigners()) {
            val installedCertificates = installed.apkContentsSigners.map { it.toByteArray().toList() }.toSet()
            val incomingCertificates = incoming.apkContentsSigners.map { it.toByteArray().toList() }.toSet()
            return installed.hasMultipleSigners() && incoming.hasMultipleSigners() &&
                installedCertificates == incomingCertificates
        }
        val trustedCurrentSigner = installed.signingCertificateHistory?.lastOrNull()?.toByteArray() ?: return false
        val trustedDigest = MessageDigest.getInstance("SHA-256").digest(trustedCurrentSigner)
        return incoming.signingCertificateHistory.orEmpty().any { candidate ->
            MessageDigest.isEqual(trustedDigest, MessageDigest.getInstance("SHA-256").digest(candidate.toByteArray()))
        }
    }

    private fun submitInstall(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setAppLabel(context.getString(R.string.app_name))
        params.setSize(apk.length())
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallStatusReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                    .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
        } catch (error: Exception) {
            installer.abandonSession(sessionId)
            throw error
        }
        publish(AppUpdateStatus("Actualización descargada. Android está comprobando la instalación."))
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }
}

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> AppUpdateManager.publish(
                AppUpdateStatus("Actualización instalada. Cierra y vuelve a abrir Recetas."),
            )
            PackageInstaller.STATUS_PENDING_USER_ACTION -> AppUpdateManager.publish(
                AppUpdateStatus("Actualización pendiente: Android requiere autorización."),
            )
            else -> AppUpdateManager.publish(
                AppUpdateStatus("No se pudo instalar la actualización. La versión actual sigue disponible.", error = true),
            )
        }
        // Never read or launch Intent.EXTRA_INTENT: that would open system UI.
    }
}

private object GithubReleaseClient {
    private const val BASE_URL = "https://api.github.com/repos/miguellobato84/recipes-android/releases"

    fun findLatestNewer(installedVersionCode: Long): ReleaseCandidate? {
        val candidates = mutableListOf<ReleaseCandidate>()
        var page = 1
        val startedAt = System.nanoTime()
        while (page <= MAX_RELEASE_PAGES) {
            val url = "$BASE_URL?per_page=$RELEASES_PER_PAGE&page=$page"
            val body = BoundedHttps.read(url, MAX_RELEASE_JSON_BYTES.toLong(), startedAt)
            val json = body.toString(Charsets.UTF_8)
            val array = org.json.JSONArray(json)
            candidates += parseReleasePage(json)
            if (array.length() < RELEASES_PER_PAGE) {
                return selectLatestRelease(candidates, installedVersionCode)
            }
            page++
        }
        throw IllegalStateException("Release list exceeded configured pagination limit")
    }
}

private object BoundedHttps {
    fun read(url: String, limit: Long, startedAt: Long = System.nanoTime()): ByteArray {
        val destination = java.io.ByteArrayOutputStream()
        stream(url, limit, startedAt) { input ->
            val buffer = ByteArray(8192)
            while (true) {
                ensureTime(startedAt)
                val count = input.read(buffer)
                if (count < 0) break
                if (destination.size().toLong() + count > limit) throw IllegalStateException("Response too large")
                destination.write(buffer, 0, count)
            }
        }
        return destination.toByteArray()
    }

    fun download(url: String, destination: File, limit: Long) {
        val startedAt = System.nanoTime()
        stream(url, limit, startedAt) { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    ensureTime(startedAt)
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > limit) throw IllegalStateException("Download too large")
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
    }

    private fun stream(url: String, limit: Long, startedAt: Long, consume: (java.io.InputStream) -> Unit) {
        val parsed = URL(url)
        require(parsed.protocol == "https") { "HTTPS is required" }
        val connection = parsed.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Recetas-Android")
        if (parsed.host == "api.github.com") connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw java.io.IOException("HTTP $code")
            require(connection.url.protocol == "https") { "HTTPS redirect required" }
            val length = connection.contentLengthLong
            if (length > limit) throw IllegalStateException("Response too large")
            ensureTime(startedAt)
            connection.inputStream.use(consume)
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureTime(startedAt: Long) {
        if ((System.nanoTime() - startedAt) / 1_000_000 > MAX_TOTAL_TIME_MS) {
            throw java.io.IOException("Request timeout")
        }
    }
}
