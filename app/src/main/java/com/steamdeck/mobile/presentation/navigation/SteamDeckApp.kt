package com.steamdeck.mobile.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.steamdeck.mobile.presentation.ui.winlator.WinlatorInitDialog

/**
 * SteamDeck Mobileアプリのメインコンポーネント
 *
 * Fullscreen mode for gaming/controller experience
 * No bottom navigation - maximizes screen space
 */
@Composable
fun SteamDeckApp() {
    val navController = rememberNavController()

    // Winlator initialization state
    var showInitDialog by remember { mutableStateOf(true) }
    var initError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        SteamDeckNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize()
        )

        // Show Winlator initialization dialog on first launch
        if (showInitDialog) {
            WinlatorInitDialog(
                onComplete = {
                    showInitDialog = false
                },
                onError = { error ->
                    initError = error
                    showInitDialog = false
                    // TODO: Show error snackbar/dialog
                }
            )
        }
    }
}
