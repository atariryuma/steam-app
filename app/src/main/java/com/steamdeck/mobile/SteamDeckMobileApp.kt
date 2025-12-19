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
  // アプリケーション初期化処理
 }

 /**
  * WorkManager 2.9.0+ Configuration.Provider 実装 
  * getWorkManagerConfiguration() メソッド from workManagerConfiguration プロパティ 変更された。
  * Hilt usedo場合、workerFactory 注入される前 アクセスされるクラッシュ防ぐため、
  * getter useして遅延初期化行う。
  */
 override val workManagerConfiguration: Configuration
  get() = Configuration.Builder()
   .setWorkerFactory(workerFactory)
   .build()
}
