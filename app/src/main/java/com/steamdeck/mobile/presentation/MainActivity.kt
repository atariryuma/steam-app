package com.steamdeck.mobile.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.steamdeck.mobile.presentation.navigation.SteamDeckApp
import com.steamdeck.mobile.presentation.theme.SteamDeckMobileTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * アプリケーションのメインActivity
 *
 * Single Activity Architecture with Jetpack Compose Navigation
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SteamDeckMobileTheme {
                SteamDeckApp()
            }
        }
    }
}
