package com.steamdeck.mobile.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.steamdeck.mobile.presentation.navigation.SteamDeckApp
import com.steamdeck.mobile.presentation.theme.SteamDeckMobileTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * アプリケーションのメインActivity
 *
 * Single Activity Architecture with Jetpack Compose Navigation
 * Immersive fullscreen mode for controller/gaming experience
 * Best Practice: RetroArch/Winlator pattern
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable immersive fullscreen mode
        enableImmersiveMode()

        enableEdgeToEdge()
        setContent {
            SteamDeckMobileTheme {
                SteamDeckApp()
            }
        }
    }

    /**
     * Immersive mode with sticky behavior
     * Hides status bar and navigation bar for gaming experience
     *
     * Best Practice: WindowInsetsControllerCompat for API compatibility
     * Reference: https://developer.android.com/design/ui/mobile/guides/layout-and-content/immersive-content
     */
    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Hide both status bar and navigation bar
            hide(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())

            // Sticky immersive mode (bars reappear on swipe, auto-hide after delay)
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Re-enable immersive mode when activity resumes
     * Important: Prevents bars from staying visible after app switching
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }
}
