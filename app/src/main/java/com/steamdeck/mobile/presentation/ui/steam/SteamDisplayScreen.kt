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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Use device native resolution for fullscreen Big Picture experience
    // Get actual display metrics to maximize screen real estate
    val displayMetrics = context.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels
    val screenHeight = displayMetrics.heightPixels
    val nativeScreenSize = "${screenWidth}x${screenHeight}"

    // DEBUG: Log screen composition
    android.util.Log.e("SteamDisplayScreen", "=== COMPOSABLE CALLED === containerId=$containerId, nativeScreenSize=$nativeScreenSize")

    // Create XServer instance (X11 protocol server) with device native resolution
    val xServer = remember {
        android.util.Log.e("SteamDisplayScreen", "Creating XServer with resolution: $nativeScreenSize")
        XServer(ScreenInfo(nativeScreenSize))
    }

    // XServerView wraps GLSurfaceView for OpenGL rendering
    // MUST be created before LaunchedEffect (ViewModel needs it for window filtering)
    // IMPORTANT: xServerView is stable across recompositions (remember with xServer key)
    val xServerView = remember(xServer) {
        XServerView(context, xServer)
    }

    // Launch Steam Big Picture with delay to ensure GLRenderer initialization
    // CRITICAL: GLSurfaceView.onResume() triggers async OpenGL context creation
    // We must wait for this to complete before Wine connects to X11
    LaunchedEffect(containerId) {
        android.util.Log.e("SteamDisplayScreen", "=== [VERSION 3] LaunchedEffect START === containerId=$containerId")
        android.util.Log.e("SteamDisplayScreen", "=== [VERSION 3] Line 92: About to delay 500ms ===")

        // Wait for GLRenderer initialization (500ms is sufficient for GL context creation)
        kotlinx.coroutines.delay(500)

        android.util.Log.e("SteamDisplayScreen", "=== [VERSION 3] Line 96: Delay complete, launching Steam ===")
        viewModel.launchSteam(containerId, xServer, xServerView)
        android.util.Log.e("SteamDisplayScreen", "=== [VERSION 3] Line 98: launchSteam() called ===")
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
            // REMOVED: InstallingSteam state (not defined in SteamDisplayUiState)
            // is SteamDisplayUiState.InstallingSteam -> { ... }
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

    // Lifecycle management: Keep XServer running when navigating away
    // CHANGED (2025-12-22): Remove onPause() to preserve XServer state
    // - Old behavior: onPause() when leaving screen → Steam process continues but rendering stops
    // - New behavior: Keep rendering active → Seamless return to running game/Steam
    // - XServer cleanup only happens when Activity is destroyed (handled by ViewModel)
    DisposableEffect(xServerView) {
        onDispose {
            // DO NOT call onPause() here - let XServer keep running
            // Cleanup will be handled by ViewModel.onCleared() when Activity is destroyed
            android.util.Log.d("SteamDisplayScreen", "Screen disposed but XServer kept alive")
        }
    }
}
