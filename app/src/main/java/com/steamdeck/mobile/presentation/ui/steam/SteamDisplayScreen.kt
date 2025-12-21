package com.steamdeck.mobile.presentation.ui.steam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.steamdeck.mobile.core.xserver.ScreenInfo
import com.steamdeck.mobile.core.xserver.XServer
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
    screenSize: String = "1280x720",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // Create XServer instance (X11 protocol server)
    val xServer = remember {
        XServer(ScreenInfo(screenSize))
    }

    // XServerView wraps GLSurfaceView for OpenGL rendering
    val xServerView = remember(xServer) {
        XServerView(context, xServer)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // AndroidView bridge: Compose → Android View system
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { xServerView },
            update = { view ->
                // Resume rendering when screen becomes visible
                view.onResume()
            }
        )
    }

    // Lifecycle management: pause/resume OpenGL rendering
    DisposableEffect(xServerView) {
        onDispose {
            // Cleanup: pause rendering when screen is destroyed
            xServerView.onPause()
        }
    }
}
