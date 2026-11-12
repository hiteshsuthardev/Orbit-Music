package com.example.orbitmusic.domain.update

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** The latest release, as described by version.json. */
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    /** Lower-case hex SHA-256 of the APK. */
    val sha256: String,
    /** Versions below this must update before using the app. */
    val minVersionCode: Int,
    val forceUpdate: Boolean,
    val notes: String,
)

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus

    /** A newer version the user may install later. */
    data class Available(val update: AppUpdate) : UpdateStatus

    /** The app must be updated before it can be used. */
    data class Required(val update: AppUpdate) : UpdateStatus
}

/** Remote switches read from remote-config.json (cached for offline use). Never secrets. */
data class RemoteConfig(
    val announcementText: String? = null,
    val featureAiEnabled: Boolean = true,
    val aiModel: String? = null,
)

/** What has already happened with updates on this device. */
data class UpdateHistory(
    val lastCheckAt: Long = 0L,
    val skippedVersionCode: Int? = null,
    val lastPromptedVersionCode: Int? = null,
    val lastPromptedAt: Long = 0L,
)

/** The update rules, kept free of Android so they are unit-tested directly. */
object UpdatePolicy {
    /** Automatic checks (start / resume) run at most this often. */
    val CHECK_INTERVAL_MS: Long = 6.hours.inWholeMilliseconds

    /** The same optional update is offered at most once in this period. */
    val PROMPT_INTERVAL_MS: Long = 1.days.inWholeMilliseconds

    fun status(update: AppUpdate, currentVersionCode: Int): UpdateStatus = when {
        update.versionCode <= currentVersionCode -> UpdateStatus.UpToDate
        currentVersionCode < update.minVersionCode || update.forceUpdate -> UpdateStatus.Required(update)
        else -> UpdateStatus.Available(update)
    }

    fun isAutomaticCheckDue(history: UpdateHistory, now: Long): Boolean =
        now - history.lastCheckAt >= CHECK_INTERVAL_MS

    /**
     * Whether to show the optional-update dialog. A manual "Check for updates" always shows
     * it; automatic checks respect "Skip this version" and once-a-day-per-version.
     */
    fun shouldPrompt(update: AppUpdate, history: UpdateHistory, now: Long, manual: Boolean): Boolean = when {
        manual -> true
        history.skippedVersionCode == update.versionCode -> false
        history.lastPromptedVersionCode == update.versionCode && now - history.lastPromptedAt < PROMPT_INTERVAL_MS -> false
        else -> true
    }

    /** Only HTTPS addresses are ever fetched or downloaded. */
    fun isSecureUrl(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)

    fun isValidSha256(hash: String): Boolean = hash.matches(Regex("[0-9a-fA-F]{64}"))
}
