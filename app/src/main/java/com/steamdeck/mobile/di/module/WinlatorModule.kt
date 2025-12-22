package com.steamdeck.mobile.di.module

import com.steamdeck.mobile.core.winlator.WinlatorEngine
import com.steamdeck.mobile.core.winlator.WinlatorEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Winlator engine dependency injection module
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WinlatorModule {
 /**
  * Bind WinlatorEngine implementation
  */
 @Binds
 @Singleton
 abstract fun bindWinlatorEngine(
  winlatorEngineImpl: WinlatorEngineImpl
 ): WinlatorEngine
}
