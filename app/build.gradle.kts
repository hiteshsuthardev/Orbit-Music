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

android {
    namespace = "com.example.orbitmusic"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.orbitmusic"
        minSdk = 26
        targetSdk = 36
        // Versioning (every update): versionName is MAJOR.MINOR.PATCH.
        // - PATCH (1.1.0 -> 1.1.1): bug fixes only.
        // - MINOR (1.1.x -> 1.2.0): new features or smaller changes, nothing breaking.
        // - MAJOR (1.x -> 2.0.0): big redesigns or breaking changes.
        // versionCode goes up by 1 on every update, whatever the type.
        versionCode = 7
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${secret("YOUTUBE_API_KEY")}\"")
        // Shown on the "Orbit AI limit reached" screen; blank hides the email button.
        buildConfigField("String", "DEVELOPER_CONTACT_EMAIL", "\"${secret("DEVELOPER_CONTACT_EMAIL")}\"")
        // Orbit AI (Groq). The model name is not secret.
        buildConfigField("String", "GROQ_MODEL", "\"${secret("GROQ_MODEL")}\"")
    }

    buildTypes {
        debug {
            // Debug calls Groq directly with the key from local.properties.
            buildConfigField("String", "GROQ_API_KEY", "\"${secret("GROQ_API_KEY")}\"")
            buildConfigField("String", "AI_ENDPOINT", "\"https://api.groq.com/openai/v1/chat/completions\"")
            buildConfigField("boolean", "AI_SENDS_API_KEY", "true")
        }
        release {
            // Release never contains the Groq key: it calls the proxy, which holds it server-side.
            buildConfigField("String", "GROQ_API_KEY", "\"\"")
            buildConfigField("String", "AI_ENDPOINT", "\"${secret("AI_PROXY_URL")}\"")
            buildConfigField("boolean", "AI_SENDS_API_KEY", "false")
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
