package com.steamdeck.mobile.presentation.ui.steam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.R
import com.steamdeck.mobile.core.xserver.ScreenInfo
import com.steamdeck.mobile.core.xserver.XServer
import com.steamdeck.mobile.presentation.viewmodel.SteamDisplayViewModel
import com.steamdeck.mobile.presentation.viewmodel.SteamDisplayUiState
import com.steamdeck.mobile.presentation.widget.XServerView

/**
 * Steam Display Screen - Renders Steam Client using integrated XServer
 *
 * XServer implementation integrated from Winlator (MIT License)
 * Copyright (c) 2023 BrunoSX
 * https://github.com/brunodev85/winlator
 *
 * Architecture:
 * ```
 * Compose UI (Steam Deck Mobile)
 *     ↓ AndroidView
 * XServerView (GLSurfaceView) ← Winlator XServer
 *     ↓ GLRenderer (OpenGL ES 3.0)
 * XServer (X11 protocol)
 *     ↓ Unix Socket
 * Wine/Steam.exe (WinlatorEmulator)
 * ```
 *
 * Features:
 * - Full X11 protocol implementation
 * - OpenGL ES 3.0 hardware acceleration
 * - GPU acceleration (Turnip/VirGL/DXVK)
 * - Integrated into Steam Deck Mobile app
 */
@Composable
fun SteamDisplayScreen(
    containerId: String = "default_shared_container",
    screenSize: String = "1280x720",
    onBack: () -> Unit = {},
    viewModel: SteamDisplayViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // DEBUG: Log screen composition
    android.util.Log.e("SteamDisplayScreen", "=== COMPOSABLE CALLED === containerId=$containerId, screenSize=$screenSize")

    // Create XServer instance (X11 protocol server)
    val xServer = remember {
        android.util.Log.e("SteamDisplayScreen", "Creating XServer instance")
        XServer(ScreenInfo(screenSize))
    }

    // Launch Steam Big Picture when screen is displayed
    LaunchedEffect(containerId, xServer) {
        android.util.Log.e("SteamDisplayScreen", "LaunchedEffect triggered with containerId: $containerId")
        viewModel.launchSteam(containerId, xServer)
        android.util.Log.e("SteamDisplayScreen", "viewModel.launchSteam() called")
    }

    // XServerView wraps GLSurfaceView for OpenGL rendering
    val xServerView = remember(xServer) {
        XServerView(context, xServer)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // CRITICAL: XServerView must ALWAYS be visible for GLRenderer to initialize
        // AndroidView bridge: Compose → Android View system
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { xServerView },
            update = { view ->
                // Resume rendering when screen becomes visible
                view.onResume()
                android.util.Log.e("SteamDisplayScreen", "AndroidView update called, onResume() executed")
            }
        )

        // Show loading/error states as OVERLAY (don't hide XServerView)
        when (uiState) {
            is SteamDisplayUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SteamDisplayUiState.InitializingXServer -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.steam_display_initializing_xserver))
                    }
                }
            }
            is SteamDisplayUiState.Launching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.steam_display_launching_steam))
                    }
                }
            }
            is SteamDisplayUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState as SteamDisplayUiState.Error).message
                    )
                }
            }
            is SteamDisplayUiState.Running -> {
                // Steam is running, XServer view is displayed
                // No overlay - show XServer only
            }
        }
    }

    // Lifecycle management: pause/resume OpenGL rendering
    DisposableEffect(xServerView) {
        onDispose {
            // Cleanup: pause rendering when screen is destroyed
            xServerView.onPause()
        }
    }
}
