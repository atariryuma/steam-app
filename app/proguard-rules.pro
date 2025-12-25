# SteamDeck Mobile - ProGuard/R8 Configuration
# Optimized for APK size reduction and performance
# Target: < 50MB APK size
# Last updated: 2025-12-17

#==========================================
# R8 Full Mode Optimization
#==========================================
-allowaccessmodification
-repackageclasses ''

# Kotlin reflection protection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.steamdeck.mobile.**$$serializer { *; }
-keepclassmembers class com.steamdeck.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class com.steamdeck.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# ZXing QR Code
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Jetpack Compose (optimized)
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.** {
    <init>(...);
}

# Compose Compiler
-keep class androidx.compose.compiler.** { *; }
-keepclassmembers @androidx.compose.runtime.Composable class * {
    <methods>;
}

# Material3
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.icons.** { *; }

# Wine/Box64 Integration
# Keep Winlator emulator classes and JNI bindings
-keep class com.steamdeck.mobile.core.winlator.** { *; }
-keep class com.steamdeck.mobile.domain.emulator.** { *; }

# Apache Commons Compress (for tar/xz extraction)
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.compress.** { *; }

# zstd-jni (JNI bindings)
-keep class com.github.luben.zstd.** { *; }

# Native uinput Controller Bridge (JNI)
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.steamdeck.mobile.core.input.NativeUInputBridge {
    native <methods>;
}
-keep class com.steamdeck.mobile.core.input.XboxButtonCodes { *; }
-keep class com.steamdeck.mobile.core.input.EvdevAxisCodes { *; }

# Google Error Prone Annotations (used by Tink)
-dontwarn com.google.errorprone.annotations.**

# Security Crypto (Tink)
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# Remove debug information (for production: remove debug/verbose/info logs, keep warnings/errors)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 7-Zip-JBinding-4Android (NSIS extraction)
-keep class net.sf.sevenzipjbinding.** { *; }
-keepclassmembers class net.sf.sevenzipjbinding.** { *; }
-dontwarn net.sf.sevenzipjbinding.**
