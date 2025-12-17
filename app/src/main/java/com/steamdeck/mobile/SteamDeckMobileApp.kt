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
     * WorkManager 2.9.0+ では Configuration.Provider の実装が
     * getWorkManagerConfiguration() メソッドから workManagerConfiguration プロパティに変更された。
     * Hilt を使用する場合、workerFactory が注入される前にアクセスされるクラッシュを防ぐため、
     * getter を使用して遅延初期化を行う。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
