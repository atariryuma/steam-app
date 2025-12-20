package com.steamdeck.mobile.di.module

import android.content.Context
import com.steamdeck.mobile.core.wine.WineMonoInstaller
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.core.winlator.ZstdDecompressor
import com.steamdeck.mobile.domain.emulator.WindowsEmulator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for Windows emulator.
 *
 * This module uses Strategy Pattern to allow switching between
 * different emulator backends (Winlator, Proton+FEX, etc.)
 * by changing a single @Provides method.
 *
 * Future migration example:
 * ```kotlin
 * @Provides
 * @Singleton
 * fun provideWindowsEmulator(
 *  @ApplicationContext context: Context,
 *  zstdDecompressor: ZstdDecompressor,
 *  preferences: AppPreferences
 * ): WindowsEmulator {
 *  return when (preferences.emulatorBackend) {
 *   EmulatorBackend.WINLATOR -> WinlatorEmulator(context, zstdDecompressor)
 *   EmulatorBackend.PROTON_FEX -> ProtonEmulator(context, zstdDecompressor)
 *   else -> WinlatorEmulator(context, zstdDecompressor) // Default
 *  }
 * }
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object EmulatorModule {

 /**
  * Provides Windows emulator instance.
  *
  * Currently: Winlator
  * Future: Configurable (Winlator/Proton/Mobox)
  */
 @Provides
 @Singleton
 fun provideProcessMonitor(): com.steamdeck.mobile.core.winlator.ProcessMonitor {
  return com.steamdeck.mobile.core.winlator.ProcessMonitor()
 }

 @Provides
 @Singleton
 fun provideWindowsEmulator(
  @ApplicationContext context: Context,
  zstdDecompressor: ZstdDecompressor,
  processMonitor: com.steamdeck.mobile.core.winlator.ProcessMonitor,
  wineMonoInstaller: WineMonoInstaller
 ): WindowsEmulator {
  return WinlatorEmulator(context, zstdDecompressor, processMonitor, wineMonoInstaller)
 }
}
