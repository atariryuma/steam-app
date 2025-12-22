package com.steamdeck.mobile.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.steamdeck.mobile.core.input.GameControllerManager
import com.steamdeck.mobile.presentation.navigation.SteamDeckApp
import com.steamdeck.mobile.presentation.theme.SteamDeckMobileTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main application Activity
 *
 * Single Activity Architecture with Jetpack Compose Navigation
 * Immersive fullscreen mode for controller/gaming experience
 * Best Practice: RetroArch/Winlator pattern
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var gameControllerManager: GameControllerManager

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted - direct file access will now work
            android.util.Log.d("MainActivity", "READ_EXTERNAL_STORAGE permission granted")
        } else {
            // Permission denied - files will be copied to app storage
            android.util.Log.w("MainActivity", "READ_EXTERNAL_STORAGE permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request storage permission if needed (Android 6-12)
        checkAndRequestStoragePermission()

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
     * Check and request READ_EXTERNAL_STORAGE permission
     * Only needed for Android 6.0 (API 23) to Android 12 (API 32)
     * Android 13+ uses granular media permissions
     */
    private fun checkAndRequestStoragePermission() {
        // Only request on Android 6-12 (API 23-32)
        if (Build.VERSION.SDK_INT in 23..32) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    android.util.Log.d("MainActivity", "READ_EXTERNAL_STORAGE already granted")
                }
                else -> {
                    // Request permission
                    android.util.Log.d("MainActivity", "Requesting READ_EXTERNAL_STORAGE permission")
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
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

    /**
     * Dispatch key events to GameControllerManager
     * Routes controller button presses to input routing system
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Check if event is from a game controller
        val device = InputDevice.getDevice(event.deviceId)
        val isGameController = device != null && (
            device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        )

        if (isGameController) {
            lifecycleScope.launch {
                try {
                    if (gameControllerManager.handleKeyEvent(event.deviceId, event)) {
                        // Event handled by controller manager
                        return@launch
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error handling key event", e)
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /**
     * Dispatch motion events to GameControllerManager
     * Routes analog stick/trigger movements to input routing system
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // Check if event is from a game controller
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) {

            lifecycleScope.launch {
                try {
                    if (gameControllerManager.handleMotionEvent(event.deviceId, event)) {
                        // Event handled by controller manager
                        return@launch
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error handling motion event", e)
                }
            }
        }

        return super.dispatchGenericMotionEvent(event)
    }
}
