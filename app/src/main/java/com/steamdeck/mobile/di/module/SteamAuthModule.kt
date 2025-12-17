package com.steamdeck.mobile.di.module

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.steamdeck.mobile.BuildConfig
import com.steamdeck.mobile.data.remote.steam.SteamAuthenticationService
import com.steamdeck.mobile.data.repository.SteamAuthRepositoryImpl
import com.steamdeck.mobile.domain.repository.SteamAuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Steam認証モジュール
 *
 * Best Practice: Hilt dependency injection
 * Reference: https://developer.android.com/training/dependency-injection/hilt-android
 */
@Module
@InstallIn(SingletonComponent::class)
object SteamAuthModule {

    /**
     * Kotlinx Serialization JSON設定
     *
     * Best Practice: Lenient parsing for API compatibility
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Steam Authentication Service Retrofit
     *
     * Best Practice: Kotlinx Serialization Converter
     * Reference: https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter
     *
     * Note: NetworkModuleの共有OkHttpClientを使用
     */
    @Provides
    @Singleton
    fun provideSteamAuthenticationService(
        okHttpClient: OkHttpClient,
        json: Json
    ): SteamAuthenticationService {
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(SteamAuthenticationService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(SteamAuthenticationService::class.java)
    }

    /**
     * Steam Auth Repository
     */
    @Provides
    @Singleton
    fun provideSteamAuthRepository(
        steamAuthService: SteamAuthenticationService
    ): SteamAuthRepository {
        return SteamAuthRepositoryImpl(steamAuthService)
    }

    /**
     * Embedded Steam Web API Key
     *
     * Best Practice: Embedded in app for zero-configuration user experience
     * Security: Obfuscated by ProGuard/R8 in release builds
     *
     * Reference: GameHub approach (https://github.com/tkashkin/GameHub)
     */
    @Provides
    @Singleton
    @Named("embedded_steam_api_key")
    fun provideEmbeddedSteamApiKey(): String {
        return BuildConfig.STEAM_API_KEY
    }
}
