package com.steamdeck.mobile.di.module

import com.steamdeck.mobile.data.local.preferences.SecurePreferencesImpl
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.data.remote.steam.SteamRepositoryImpl
import com.steamdeck.mobile.data.repository.ControllerRepositoryImpl
import com.steamdeck.mobile.data.repository.DownloadRepositoryImpl
import com.steamdeck.mobile.data.repository.FileImportRepositoryImpl
import com.steamdeck.mobile.data.repository.GameRepositoryImpl
import com.steamdeck.mobile.data.repository.SteamAuthRepositoryImpl
import com.steamdeck.mobile.data.repository.SteamRepositoryAdapter
import com.steamdeck.mobile.data.repository.WinlatorContainerRepositoryImpl
import com.steamdeck.mobile.domain.repository.ControllerRepository
import com.steamdeck.mobile.domain.repository.DownloadRepository
import com.steamdeck.mobile.domain.repository.FileImportRepository
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import com.steamdeck.mobile.domain.repository.ISteamRepository
import com.steamdeck.mobile.domain.repository.SteamAuthRepository
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * リポジトリの依存性注入モジュール
 * Clean Architecture: Domain interfaces bound to data layer implementations
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    /**
     * GameRepositoryの実装をバインド
     */
    @Binds
    @Singleton
    abstract fun bindGameRepository(
        gameRepositoryImpl: GameRepositoryImpl
    ): GameRepository

    /**
     * WinlatorContainerRepositoryの実装をバインド
     */
    @Binds
    @Singleton
    abstract fun bindWinlatorContainerRepository(
        winlatorContainerRepositoryImpl: WinlatorContainerRepositoryImpl
    ): WinlatorContainerRepository

    /**
     * DownloadRepositoryの実装をバインド
     */
    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        downloadRepositoryImpl: DownloadRepositoryImpl
    ): DownloadRepository

    /**
     * SteamAuthRepositoryの実装をバインド
     */
    @Binds
    @Singleton
    abstract fun bindSteamAuthRepository(
        steamAuthRepositoryImpl: SteamAuthRepositoryImpl
    ): SteamAuthRepository

    /**
     * FileImportRepositoryの実装をバインド
     */
    @Binds
    @Singleton
    abstract fun bindFileImportRepository(
        fileImportRepositoryImpl: FileImportRepositoryImpl
    ): FileImportRepository

    /**
     * ControllerRepositoryの実装をバインド
     */
    @Binds
    @Singleton
    abstract fun bindControllerRepository(
        controllerRepositoryImpl: ControllerRepositoryImpl
    ): ControllerRepository

    /**
     * ISecurePreferencesの実装をバインド
     * Clean Architecture: Domain interface for secure storage
     */
    @Binds
    @Singleton
    abstract fun bindSecurePreferences(
        securePreferencesImpl: SecurePreferencesImpl
    ): ISecurePreferences

    /**
     * Data layer SteamRepositoryの実装をバインド
     * This is the direct data layer implementation
     */
    @Binds
    @Singleton
    abstract fun bindSteamRepository(
        steamRepositoryImpl: SteamRepositoryImpl
    ): SteamRepository

    /**
     * ISteamRepositoryの実装をバインド
     * Clean Architecture: Domain interface adapter for Steam operations
     */
    @Binds
    @Singleton
    abstract fun bindISteamRepository(
        steamRepositoryAdapter: SteamRepositoryAdapter
    ): ISteamRepository
}
