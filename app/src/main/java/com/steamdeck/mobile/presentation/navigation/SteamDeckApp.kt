package com.steamdeck.mobile.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController

/**
 * SteamDeck Mobileアプリのメインコンポーネント
 *
 * Fullscreen mode for gaming/controller experience
 * No bottom navigation - maximizes screen space
 *
 * NOTE: Winlator initialization is now handled on-demand when launching games,
 * not on app startup
 */
@Composable
fun SteamDeckApp() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        SteamDeckNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize()
        )
    }
}
