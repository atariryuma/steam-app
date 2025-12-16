package com.steamdeck.mobile.di.module

import com.steamdeck.mobile.core.winlator.WinlatorEngine
import com.steamdeck.mobile.core.winlator.WinlatorEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Winlatorエンジンの依存性注入モジュール
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WinlatorModule {
    /**
     * WinlatorEngineの実装をバインド
     */
    @Binds
    @Singleton
    abstract fun bindWinlatorEngine(
        winlatorEngineImpl: WinlatorEngineImpl
    ): WinlatorEngine
}
