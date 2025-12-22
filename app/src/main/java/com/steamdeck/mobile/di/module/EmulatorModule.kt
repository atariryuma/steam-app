package com.steamdeck.mobile.di.module

import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.domain.emulator.WindowsEmulator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for Windows emulator.
 *
 * CRITICAL FIX (2025-12-22): Use @Binds instead of @Provides to ensure single instance
 * Previous implementation created multiple WinlatorEmulator instances:
 * - One from @Inject constructor (used by WinlatorEngineImpl)
 * - One from provideWindowsEmulator (used by LaunchGameUseCase)
 * This caused activeProcesses map to be different across instances.
 *
 * @Binds ensures Hilt uses the same @Singleton instance everywhere.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EmulatorModule {

 /**
  * Bind WindowsEmulator interface to WinlatorEmulator implementation.
  *
  * CRITICAL: Using @Binds ensures only ONE instance is created.
  * WinlatorEmulator is @Singleton, so this binding will reuse that instance.
  */
 @Binds
 @Singleton
 abstract fun bindWindowsEmulator(
  winlatorEmulator: WinlatorEmulator
 ): WindowsEmulator

}
