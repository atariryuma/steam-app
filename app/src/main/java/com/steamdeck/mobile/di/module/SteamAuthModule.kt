package com.steamdeck.mobile.di.module

import android.content.Context
import com.steamdeck.mobile.core.download.DownloadManager
import com.steamdeck.mobile.core.steam.ProtonManager
import com.steamdeck.mobile.core.steam.SteamInstallerService
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.steam.SteamSetupManager
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Steamモジュール
 *
 * Best Practice: Hilt dependency injection
 * Reference: https://developer.android.com/training/dependency-injection/hilt-android
 */
@Module
@InstallIn(SingletonComponent::class)
object SteamAuthModule {

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
