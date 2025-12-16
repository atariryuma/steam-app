package com.steamdeck.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SteamDeckMobileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // アプリケーション初期化処理
    }
}
