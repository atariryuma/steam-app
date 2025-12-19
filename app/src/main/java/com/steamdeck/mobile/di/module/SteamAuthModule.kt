package com.steamdeck.mobile.di.module

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.steamdeck.mobile.core.download.DownloadManager
import com.steamdeck.mobile.core.steam.ProtonManager
import com.steamdeck.mobile.core.steam.SteamInstallerService
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.steam.SteamSetupManager
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.remote.steam.SteamAuthenticationService
import com.steamdeck.mobile.data.repository.SteamAuthRepositoryImpl
import com.steamdeck.mobile.domain.repository.SteamAuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
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
     * Steam Installer Service
     *
     * Steamインストーラーのダウンロード・検証を管理します
     */
    @Provides
    @Singleton
    fun provideSteamInstallerService(
        @ApplicationContext context: Context,
        downloadManager: DownloadManager,
        database: SteamDeckDatabase,
        okHttpClient: OkHttpClient
    ): SteamInstallerService {
        return SteamInstallerService(
            context = context,
            downloadManager = downloadManager,
            database = database,
            okHttpClient = okHttpClient
        )
    }

    /**
     * Steam Setup Manager
     *
     * Winlatorコンテナ内でのSteamインストールを管理します
     */
    @Provides
    @Singleton
    fun provideSteamSetupManager(
        @ApplicationContext context: Context,
        winlatorEmulator: WinlatorEmulator,
        steamInstallerService: SteamInstallerService,
        database: SteamDeckDatabase
    ): SteamSetupManager {
        return SteamSetupManager(
            context = context,
            winlatorEmulator = winlatorEmulator,
            steamInstallerService = steamInstallerService,
            database = database
        )
    }

    /**
     * Steam Launcher
     *
     * Steam Client経由でゲームを起動します
     */
    @Provides
    @Singleton
    fun provideSteamLauncher(
        @ApplicationContext context: Context,
        winlatorEmulator: WinlatorEmulator,
        database: SteamDeckDatabase
    ): SteamLauncher {
        return SteamLauncher(
            context = context,
            winlatorEmulator = winlatorEmulator,
            database = database
        )
    }

    /**
     * Proton Manager
     *
     * Steam Play (Proton) の設定を管理します
     */
    @Provides
    @Singleton
    fun provideProtonManager(
        @ApplicationContext context: Context,
        database: SteamDeckDatabase
    ): ProtonManager {
        return ProtonManager(
            context = context,
            database = database
        )
    }
}
