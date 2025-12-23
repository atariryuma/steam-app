package com.steamdeck.mobile.di.module

import android.content.Context
import com.steamdeck.mobile.core.download.DownloadManager
import com.steamdeck.mobile.core.steam.SteamGameScanner
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
 * Steam Authentication Module
 *
 * Provides Steam-related dependencies (installer, launcher, credentials, etc.)
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
  * Manages Steam installer download and verification
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
  * Manages Steam installation within Winlator containers
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
  * Launches games via Steam Client
  */
 @Provides
 @Singleton
 fun provideSteamLauncher(
  @ApplicationContext context: Context,
  winlatorEmulator: WinlatorEmulator
 ): SteamLauncher {
  return SteamLauncher(
   context = context,
   winlatorEmulator = winlatorEmulator
  )
 }

 /**
  * Steam Game Scanner
  *
  * Scans for installed Steam games
  */
 @Provides
 @Singleton
 fun provideSteamGameScanner(
  winlatorEmulator: WinlatorEmulator
 ): SteamGameScanner {
  return SteamGameScanner(
   winlatorEmulator = winlatorEmulator
  )
 }

 /**
  * Steam Authentication Managers
  *
  * SteamAuthManager and SteamConfigManager use @Inject constructor,
  * so Hilt automatically generates instances. No explicit @Provides needed.
  *
  * - SteamAuthManager: Generates loginusers.vdf for auto-login
  * - SteamConfigManager: Generates config.vdf with CDN servers
  */
}
