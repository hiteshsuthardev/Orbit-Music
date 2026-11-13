import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android is not applied.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Secrets live in local.properties (git-ignored) and are exposed through BuildConfig.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String =
    (localProperties.getProperty(name) ?: System.getenv(name) ?: "")
        .trim()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

// Version lives in version.properties so `bumpVersion` can change it (see RELEASING.md).
val versionFile = rootProject.file("version.properties")
val versionProperties = Properties().apply { versionFile.inputStream().use { load(it) } }
val appVersionCode = versionProperties.getProperty("VERSION_CODE").trim().toInt()
val appVersionName: String = versionProperties.getProperty("VERSION_NAME").trim()

/** Release signing comes from local.properties; without it, release builds are unsigned. */
val releaseStoreFile: String = localProperties.getProperty("RELEASE_STORE_FILE").orEmpty().trim()
val hasReleaseSigning = releaseStoreFile.isNotEmpty() && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.example.orbitmusic"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.orbitmusic"
        minSdk = 26
        targetSdk = 36
        // Versioning rules and values: see version.properties (bump with `bumpVersion`).
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${secret("YOUTUBE_API_KEY")}\"")
        // Shown on the "OrbitNova AI limit reached" screen; blank hides the email button.
        buildConfigField("String", "DEVELOPER_CONTACT_EMAIL", "\"${secret("DEVELOPER_CONTACT_EMAIL")}\"")
        // Self-hosted updates and remote config (HTTPS only; never secrets in these files).
        buildConfigField("String", "UPDATE_JSON_URL", "\"${secret("UPDATE_JSON_URL")}\"")
        buildConfigField("String", "REMOTE_CONFIG_URL", "\"${secret("REMOTE_CONFIG_URL")}\"")
        // OrbitNova AI (Groq). The model name is not secret.
        buildConfigField("String", "GROQ_MODEL", "\"${secret("GROQ_MODEL")}\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug calls Groq directly with the key from local.properties.
            buildConfigField("String", "GROQ_API_KEY", "\"${secret("GROQ_API_KEY")}\"")
            buildConfigField("String", "AI_ENDPOINT", "\"https://api.groq.com/openai/v1/chat/completions\"")
            buildConfigField("boolean", "AI_SENDS_API_KEY", "true")
            buildConfigField("String", "AI_PROXY_TOKEN", "\"\"")
        }
        release {
            // Release never contains the Groq key: it calls the proxy, which holds it server-side.
            buildConfigField("String", "GROQ_API_KEY", "\"\"")
            buildConfigField("String", "AI_ENDPOINT", "\"${secret("AI_PROXY_URL")}\"")
            buildConfigField("boolean", "AI_SENDS_API_KEY", "false")
            // Not a real secret (it ships in the APK): it only keeps casual callers off the proxy.
            buildConfigField("String", "AI_PROXY_TOKEN", "\"${secret("AI_PROXY_TOKEN")}\"")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        // Apply qualifier annotations (e.g. @IoDispatcher) to constructor params and properties.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Async / serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging.interceptor)

    // Local storage
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.haze)

    // Playback (official YouTube IFrame player wrapper + media session)
    implementation(libs.youtube.player.core)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Auth
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Release helpers (see RELEASING.md) ---------------------------------------------------------

/**
 * Bumps version.properties: ./gradlew bumpVersion -Pbump=patch|minor|major
 * versionCode always goes up by one.
 */
tasks.register("bumpVersion") {
    group = "release"
    description = "Bumps VERSION_CODE and VERSION_NAME in version.properties (-Pbump=patch|minor|major)."
    val bump = providers.gradleProperty("bump").orElse("patch")
    val file = versionFile
    doLast {
        val props = Properties().apply { file.inputStream().use { load(it) } }
        val code = props.getProperty("VERSION_CODE").trim().toInt() + 1
        val (major, minor, patch) = props.getProperty("VERSION_NAME").trim().split(".").map { it.toInt() }
        val name = when (bump.get()) {
            "major" -> "${major + 1}.0.0"
            "minor" -> "$major.${minor + 1}.0"
            "patch" -> "$major.$minor.${patch + 1}"
            else -> throw GradleException("-Pbump must be patch, minor or major")
        }
        val text = file.readText()
            .replace(Regex("(?m)^VERSION_CODE=.*$"), "VERSION_CODE=$code")
            .replace(Regex("(?m)^VERSION_NAME=.*$"), "VERSION_NAME=$name")
        file.writeText(text)
        println("OrbitNova Music version is now $name ($code)")
    }
}

/**
 * Builds the signed release APK, then writes app/build/release-upload/ with
 * orbit-music.apk (renamed) and version.json (with its SHA-256). Upload both to a GitHub
 * Release tagged v<versionName>. Options: -Pnotes="...", -PminVersionCode=N, -PforceUpdate=true
 */
tasks.register("prepareRelease") {
    group = "release"
    description = "Builds the signed release APK and generates version.json for a GitHub Release."
    dependsOn("assembleRelease")
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val outDir = layout.buildDirectory.dir("release-upload")
    val notes = providers.gradleProperty("notes").orElse("Bug fixes and improvements.")
    val minCode = providers.gradleProperty("minVersionCode").orElse("1")
    val force = providers.gradleProperty("forceUpdate").orElse("false")
    val repo = providers.gradleProperty("orbit.githubRepo")
    val code = appVersionCode
    val name = appVersionName
    val signed = hasReleaseSigning
    doLast {
        if (!signed) throw GradleException("Release signing is not set up (RELEASE_* in local.properties).")
        val source = apk.get().asFile
        if (!source.exists()) throw GradleException("Signed APK not found: $source")
        val dir = outDir.get().asFile.apply { mkdirs() }
        val target = File(dir, "orbit-music.apk")
        source.copyTo(target, overwrite = true)
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        fun json(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        val versionJson = """
            |{
            |  "versionCode": $code,
            |  "versionName": ${json(name)},
            |  "apkUrl": ${json("https://github.com/${repo.get()}/releases/download/v$name/orbit-music.apk")},
            |  "sha256": ${json(sha256)},
            |  "minVersionCode": ${minCode.get().toInt()},
            |  "forceUpdate": ${force.get().toBoolean()},
            |  "notes": ${json(notes.get())}
            |}
            |""".trimMargin()
        File(dir, "version.json").writeText(versionJson)
        println("Release $name ($code) ready in $dir")
        println("SHA-256: $sha256")
    }
}
