package com.steamdeck.mobile.di.module

import android.content.Context
import com.steamdeck.mobile.core.download.DownloadManager
import com.steamdeck.mobile.core.steam.NsisExtractor
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
  okHttpClient: OkHttpClient,
  nsisExtractor: NsisExtractor
 ): SteamInstallerService {
  return SteamInstallerService(
   context = context,
   downloadManager = downloadManager,
   database = database,
   okHttpClient = okHttpClient,
   nsisExtractor = nsisExtractor
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
  * The following managers use @Inject constructor, so Hilt automatically
  * generates instances (no explicit @Provides needed):
  *
  * - SteamAuthManager: Generates loginusers.vdf for auto-login
  *   - Uses SteamIdValidator for robust SteamID64 validation
  *   - Includes 5-minute cache guard to prevent redundant writes
  *   - Use createLoginUsersVdfIfNeeded() for cached writes (returns VdfWriteResult)
  *   - References: SteamConstants for paths, VdfCacheUtils for caching logic
  *
  * - SteamConfigManager: Generates config.vdf with CDN servers
  *   - Pre-configures 7 CDN servers + 4 CM servers for reliable Steam bootstrap
  *   - AutoLoginUser setting requires Steam account name (NOT SteamID64)
  *   - Includes 5-minute cache guard to prevent redundant writes
  *   - Use createConfigVdfIfNeeded() for cached writes (returns VdfWriteResult)
  *   - References: SteamConstants for paths, VdfCacheUtils for caching logic
  *
  * Shared Utilities (auto-injected):
  * - SteamConstants: Centralized timeout and path configuration
  * - SteamIdValidator: SteamID64 format validation (range: 76561197960265728-76561202255233023)
  * - VdfWriteResult: Type-safe result wrapper (Created/Skipped/Error)
  * - VdfCacheUtils: File modification time-based cache guard (5-minute timeout)
  */
}
