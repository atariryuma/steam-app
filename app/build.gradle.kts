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

// Development API Key Configuration
// Load from local.properties (not committed to git)
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}
val devSteamApiKey = localProperties.getProperty("STEAM_API_KEY", "")

android {
    namespace = "com.steamdeck.mobile"
    compileSdk = 35
    ndkVersion = "22.1.7171670"

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

        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildTypes {
        release {
            // R8 code optimization and resource shrinking
            isMinifyEnabled = true
            isShrinkResources = true

            // R8 full mode (enabled by default in AGP 8.0+)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Remove debug info to reduce APK size
            isDebuggable = false
            isJniDebuggable = false

            // Native library optimization
            ndk {
                debugSymbolLevel = "NONE"
            }
        }

        debug {
            // Debug settings (no optimization)
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // Development API Key (ONLY in debug builds, loaded from local.properties)
            buildConfigField("String", "DEV_STEAM_API_KEY", "\"$devSteamApiKey\"")
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            // Exclude unnecessary META-INF files to reduce APK size
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

            // Merge duplicate resources (pick first)
            pickFirsts += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                // zstd-jni native library (use first found)
                "lib/arm64-v8a/libzstd-jni.so"
            )
        }

        // JNI library optimization
        jniLibs {
            // CRITICAL: Must extract native libraries for libproot.so to be executable
            // libproot.so is built as executable (add_executable in CMake) and needs filesystem access
            useLegacyPackaging = true
            // Keep zstd-jni native library debug symbols
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

    // Browser (Custom Tabs for OAuth/OpenID - RFC 8252 best practices)
    implementation(libs.androidx.browser)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    // Compression (Wine/Box64 binaries & NSIS extraction)
    implementation(libs.zstd.jni) // Zstandard with JNI (includes Android ARM64 libs)
    implementation(libs.commons.compress) // Apache Commons Compress for tar/zip
    implementation(libs.xz) // XZ-Java for .txz decompression
    implementation(libs.sevenzipjbinding) // 7-Zip-JBinding-4Android for NSIS extraction (ARM64 compatible)

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
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.room.testing)
}
