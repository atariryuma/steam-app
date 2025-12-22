package com.steamdeck.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SteamDeckMobileApp : Application(), Configuration.Provider {

 @Inject
 lateinit var workerFactory: HiltWorkerFactory

 override fun onCreate() {
  super.onCreate()
  // Application initialization
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
