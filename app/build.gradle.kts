import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Read API Key from local.properties (development only)
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { localProperties.load(it) }
}
val steamApiKey = localProperties.getProperty("STEAM_API_KEY") ?: ""

android {
    namespace = "com.steamdeck.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.steamdeck.mobile"
        minSdk = 26
        targetSdk = 28  // Android 9.0 (SELinux workaround for binary execution on Android 10+)
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // Development-only Steam Web API Key from local.properties
        // Production: Users provide their own API keys in-app
        // This is ONLY for development/testing convenience
        buildConfigField("String", "DEV_STEAM_API_KEY", "\"$steamApiKey\"")
    }

    buildTypes {
        release {
            // R8コード最適化とリソース削減
            isMinifyEnabled = true
            isShrinkResources = true

            // R8フルモード（AGP 8.0+でデフォルト有効）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // デバッグ情報削除でAPKサイズ削減
            isDebuggable = false
            isJniDebuggable = false

            // ネイティブライブラリの最適化
            ndk {
                debugSymbolLevel = "NONE"
            }
        }

        debug {
            // Debug用設定（最適化なし）
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // 不要なMETA-INFファイルを除外してAPKサイズ削減
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.md",
                "/META-INF/*.txt",
                "/META-INF/*.kotlin_module",
                "/META-INF/versions/**",
                "META-INF/com.android.tools/**",
                "kotlin/**",
                "DebugProbesKt.bin"
            )

            // 重複リソースのマージ
            pickFirsts += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                // zstd-jniのネイティブライブラリ（最初に見つかったものを使用）
                "lib/arm64-v8a/libzstd-jni.so"
            )
        }

        // JNIライブラリの最適化
        jniLibs {
            useLegacyPackaging = false
            // zstd-jniのネイティブライブラリを除外しない
            keepDebugSymbols += listOf("**/libzstd-jni*.so")
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Coil
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security
    implementation(libs.androidx.security.crypto)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    // Compression (Wine/Box64 binaries)
    implementation(libs.zstd.jni) // Zstandard with JNI (includes Android ARM64 libs)
    implementation(libs.commons.compress)
    implementation(libs.xz) // XZ-Java for .txz decompression

    // File Import
    // TODO: Re-enable when libaums v0.10.0 migration is complete
    // implementation(libs.libaums) // USB Mass Storage
    implementation(libs.jcifs.ng) // SMB/CIFS (SMB2/3 support)
    implementation(libs.commons.net) // FTP/FTPS

    // Kotlinx Serialization (Steam API JSON)
    implementation(libs.kotlinx.serialization.json)

    // ZXing (QR Code Generation Only)
    implementation(libs.zxing.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.arch.core.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.room.testing)
}
