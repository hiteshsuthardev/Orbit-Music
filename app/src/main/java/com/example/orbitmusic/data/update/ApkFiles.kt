package com.example.orbitmusic.data.update

import android.content.Context
import android.content.Intent
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.orbitmusic.di.IoDispatcher
import com.example.orbitmusic.di.UpdateHttpClient
import com.example.orbitmusic.domain.update.AppUpdate
import com.example.orbitmusic.domain.update.UpdatePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.UnknownHostException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadFailure { NoInternet, LowStorage, Network, VerificationFailed }

sealed interface DownloadEvent {
    /** [fraction] is null while the size is unknown. */
    data class Progress(val fraction: Float?) : DownloadEvent
    data object Verifying : DownloadEvent
    data class Ready(val file: File) : DownloadEvent
    data class Failed(val reason: DownloadFailure) : DownloadEvent
}

/**
 * Downloads update APKs into the app's cache (cache/updates/orbit-music-<versionCode>.apk) and
 * verifies their SHA-256 against version.json. A file that doesn't match is deleted, so an
 * unverified APK is never handed to the installer. Cancelling (collector cancelled) removes
 * the partial file.
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    @UpdateHttpClient private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dir: File get() = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }

    fun download(update: AppUpdate): Flow<DownloadEvent> = flow {
        val target = File(dir, "orbit-music-${update.versionCode}.apk")
        // Already downloaded and verified earlier (e.g. the installer was cancelled).
        if (target.exists() && sha256(target) == update.sha256) {
            emit(DownloadEvent.Ready(target))
            return@flow
        }
        if (!UpdatePolicy.isSecureUrl(update.apkUrl)) {
            emit(DownloadEvent.Failed(DownloadFailure.VerificationFailed))
            return@flow
        }
        val partial = File(dir, target.name + ".part")
        var completed = false
        try {
            emit(DownloadEvent.Progress(null))
            client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    emit(DownloadEvent.Failed(DownloadFailure.Network))
                    return@flow
                }
                val length = body.contentLength()
                if (!hasRoomFor(length)) {
                    emit(DownloadEvent.Failed(DownloadFailure.LowStorage))
                    return@flow
                }
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var copied = 0L
                        var lastReported = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (length > 0) {
                                val percent = (copied * 100 / length).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    emit(DownloadEvent.Progress(percent / 100f))
                                }
                            }
                        }
                    }
                }
            }
            emit(DownloadEvent.Verifying)
            if (sha256(partial) != update.sha256) {
                emit(DownloadEvent.Failed(DownloadFailure.VerificationFailed))
                return@flow
            }
            target.delete()
            if (!partial.renameTo(target)) {
                emit(DownloadEvent.Failed(DownloadFailure.Network))
                return@flow
            }
            completed = true
            emit(DownloadEvent.Ready(target))
        } catch (error: IOException) {
            emit(DownloadEvent.Failed(error.toFailure()))
        } finally {
            // Partial, cancelled or unverified downloads never stay on disk.
            if (!completed) partial.delete()
        }
    }.flowOn(io)

    /** Removes APKs for versions already installed (and any leftover partial files). */
    fun cleanUp(installedVersionCode: Int) {
        dir.listFiles()?.forEach { file ->
            val code = APK_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()
            if (file.name.endsWith(".part") || (code != null && code <= installedVersionCode)) file.delete()
        }
    }

    private fun hasRoomFor(length: Long): Boolean {
        val needed = (if (length > 0) length else UNKNOWN_SIZE_ESTIMATE) + FREE_SPACE_MARGIN
        return StatFs(dir.path).availableBytes >= needed
    }

    private fun IOException.toFailure(): DownloadFailure = when {
        this is UnknownHostException || this is ConnectException -> DownloadFailure.NoInternet
        message?.contains("ENOSPC") == true || message?.contains("No space left", ignoreCase = true) == true ->
            DownloadFailure.LowStorage
        else -> DownloadFailure.Network
    }

    companion object {
        const val UPDATES_DIR = "updates"
        private const val BUFFER_SIZE = 64 * 1024
        private const val UNKNOWN_SIZE_ESTIMATE = 60L * 1024 * 1024
        private const val FREE_SPACE_MARGIN = 20L * 1024 * 1024
        private val APK_NAME = Regex("""orbit-music-(\d+)\.apk(?:\.part)?""")

        /** Lower-case hex SHA-256 of [file]. Visible for tests. */
        fun sha256(file: File): String = file.inputStream().use(::sha256)

        fun sha256(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** Hands a verified APK to Android's package installer. */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Android 8+ asks the user once to allow installs from this app. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user allows Orbit Music to install updates. */
    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** The system installer for [apk], shared through FileProvider as a content:// URI. */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
