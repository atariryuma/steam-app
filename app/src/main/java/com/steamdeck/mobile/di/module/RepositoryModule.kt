package com.steamdeck.mobile.di.module

import com.steamdeck.mobile.data.repository.DownloadRepositoryImpl
import com.steamdeck.mobile.data.repository.GameRepositoryImpl
import com.steamdeck.mobile.data.repository.WinlatorContainerRepositoryImpl
import com.steamdeck.mobile.domain.repository.DownloadRepository
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * リポジトリの依存性注入モジュール
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
}
