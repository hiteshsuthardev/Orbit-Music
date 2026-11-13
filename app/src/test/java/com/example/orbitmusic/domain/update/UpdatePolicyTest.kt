package com.example.orbitmusic.domain.update

import com.example.orbitmusic.data.update.ApkDownloader
import com.example.orbitmusic.data.update.RemoteConfigDto
import com.example.orbitmusic.data.update.VersionJsonDto
import com.example.orbitmusic.data.update.toAppUpdate
import com.example.orbitmusic.data.update.toRemoteConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val hash = "a".repeat(64)
    private val hour = 60 * 60 * 1_000L

    private fun update(code: Int = 12, min: Int = 1, force: Boolean = false) = AppUpdate(
        versionCode = code,
        versionName = "1.2.0",
        apkUrl = "https://github.com/x/y/releases/download/v1.2.0/orbit-music.apk",
        sha256 = hash,
        minVersionCode = min,
        forceUpdate = force,
        notes = "",
    )

    @Test
    fun statusFollowsVersionCodes() {
        assertEquals(UpdateStatus.UpToDate, UpdatePolicy.status(update(code = 8), currentVersionCode = 8))
        assertEquals(UpdateStatus.UpToDate, UpdatePolicy.status(update(code = 7), currentVersionCode = 8))
        assertTrue(UpdatePolicy.status(update(code = 9), currentVersionCode = 8) is UpdateStatus.Available)
        assertTrue(UpdatePolicy.status(update(code = 12, min = 10), currentVersionCode = 8) is UpdateStatus.Required)
        assertTrue(UpdatePolicy.status(update(code = 9, force = true), currentVersionCode = 8) is UpdateStatus.Required)
        // Forced update for a version you already have isn't required.
        assertEquals(UpdateStatus.UpToDate, UpdatePolicy.status(update(code = 8, force = true), currentVersionCode = 8))
    }

    @Test
    fun automaticChecksAtMostEveryFifteenMinutes() {
        val minute = 60 * 1_000L
        val history = UpdateHistory(lastCheckAt = 10 * hour)
        assertFalse(UpdatePolicy.isAutomaticCheckDue(history, 10 * hour + 14 * minute))
        assertTrue(UpdatePolicy.isAutomaticCheckDue(history, 10 * hour + 15 * minute))
        assertTrue(UpdatePolicy.isAutomaticCheckDue(UpdateHistory(), 10 * hour))
        // Clock moved back: don't wait for it to catch up.
        assertTrue(UpdatePolicy.isAutomaticCheckDue(history, 9 * hour))
    }

    @Test
    fun promptsOncePerDayPerVersionAndRespectsSkip() {
        val now = 100 * hour
        val fresh = UpdateHistory()
        assertTrue(UpdatePolicy.shouldPrompt(update(), fresh, now, manual = false))

        val promptedRecently = UpdateHistory(lastPromptedVersionCode = 12, lastPromptedAt = now - 2 * hour)
        assertFalse(UpdatePolicy.shouldPrompt(update(), promptedRecently, now, manual = false))
        assertTrue(UpdatePolicy.shouldPrompt(update(code = 13), promptedRecently, now, manual = false))
        assertTrue(UpdatePolicy.shouldPrompt(update(), promptedRecently.copy(lastPromptedAt = now - 25 * hour), now, manual = false))

        val skipped = UpdateHistory(skippedVersionCode = 12)
        assertFalse(UpdatePolicy.shouldPrompt(update(), skipped, now, manual = false))
        assertTrue(UpdatePolicy.shouldPrompt(update(code = 13), skipped, now, manual = false))
        // "Check for updates" always shows it.
        assertTrue(UpdatePolicy.shouldPrompt(update(), skipped, now, manual = true))
    }

    @Test
    fun versionJsonIsParsedAndValidated() {
        val json = Json { ignoreUnknownKeys = true }
        val text = """
            {"versionCode": 12, "versionName": "1.2.0",
             "apkUrl": "https://github.com/u/r/releases/download/v1.2.0/orbit-music.apk",
             "sha256": "${"AB".repeat(32)}", "minVersionCode": 10, "forceUpdate": false,
             "notes": "What's new..."}
        """.trimIndent()
        val update = json.decodeFromString(VersionJsonDto.serializer(), text).toAppUpdate()!!
        assertEquals(12, update.versionCode)
        assertEquals("ab".repeat(32), update.sha256)
        assertEquals(10, update.minVersionCode)

        val dto = json.decodeFromString(VersionJsonDto.serializer(), text)
        assertNull(dto.copy(apkUrl = "http://insecure.example/app.apk").toAppUpdate())
        assertNull(dto.copy(sha256 = "not-a-hash").toAppUpdate())
        assertNull(dto.copy(versionCode = 0).toAppUpdate())
    }

    @Test
    fun remoteConfigUsesSnakeCaseAndSafeDefaults() {
        val json = Json { ignoreUnknownKeys = true }
        val config = json.decodeFromString(
            RemoteConfigDto.serializer(),
            """{"announcement_text": " New: OrbitNova AI! ", "feature_ai_enabled": false, "ai_model": ""}""",
        ).toRemoteConfig()
        assertEquals("New: OrbitNova AI!", config.announcementText)
        assertFalse(config.featureAiEnabled)
        assertNull(config.aiModel)
        assertTrue(json.decodeFromString(RemoteConfigDto.serializer(), "{}").toRemoteConfig().featureAiEnabled)
    }

    @Test
    fun onlyHttpsAndRealHashesAreAccepted() {
        assertTrue(UpdatePolicy.isSecureUrl("https://example.com/a.apk"))
        assertFalse(UpdatePolicy.isSecureUrl("http://example.com/a.apk"))
        assertFalse(UpdatePolicy.isSecureUrl("file:///sdcard/a.apk"))
        assertTrue(UpdatePolicy.isValidSha256(hash))
        assertFalse(UpdatePolicy.isValidSha256("abc"))
    }

    @Test
    fun sha256MatchesKnownValue() {
        // SHA-256 of "abc" (FIPS 180-2 test vector).
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkDownloader.sha256("abc".byteInputStream()),
        )
    }
}
