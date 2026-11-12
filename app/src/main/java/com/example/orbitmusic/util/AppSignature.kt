package com.example.orbitmusic.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identifies this app to Google APIs. An API key restricted to "Android apps" only
 * accepts requests carrying `X-Android-Package` and `X-Android-Cert` (the SHA-1 of the
 * signing certificate), so we compute them from the installed package at runtime.
 */
@Singleton
class AppSignature @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val packageName: String get() = context.packageName

    /** Uppercase hex SHA-1 of the signing certificate, without colons. Null if unavailable. */
    val certificateSha1: String? by lazy {
        runCatching {
            val signature = signingCertificateBytes() ?: return@runCatching null
            MessageDigest.getInstance("SHA-1").digest(signature)
                .joinToString("") { "%02X".format(Locale.US, it) }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateBytes(): ByteArray? {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return null
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers?.firstOrNull()?.toByteArray()
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
        }
    }
}

/** Region for chart / trending requests, from the device locale (fallback US). */
fun currentRegionCode(): String =
    Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.US) ?: "US"
