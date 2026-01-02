package com.steamdeck.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.winlator.ComponentVersionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SteamDeckMobileApp : Application(), Configuration.Provider {

 @Inject
 lateinit var workerFactory: HiltWorkerFactory

 @Inject
 lateinit var componentVersionManager: ComponentVersionManager

 override fun onCreate() {
  super.onCreate()

  // CRITICAL: Load component versions configuration at app startup
  // This must happen BEFORE any WinlatorEmulator operations
  componentVersionManager.loadConfig().onFailure { error ->
   AppLogger.e(TAG, "Failed to load component versions (using defaults)", error)
  }

  AppLogger.i(TAG, "App initialized - Active runtime: ${componentVersionManager.getActiveVersion()}")
 }

 companion object {
  private const val TAG = "SteamDeckMobileApp"
 }

 /**
  * WorkManager 2.9.0+ Configuration.Provider implementation
  * getWorkManagerConfiguration() method changed to workManagerConfiguration property.
  * When using Hilt, use getter for lazy initialization to prevent crashes
  * when workerFactory is accessed before being injected.
  */
 override val workManagerConfiguration: Configuration
  get() = Configuration.Builder()
   .setWorkerFactory(workerFactory)
   .build()
}
