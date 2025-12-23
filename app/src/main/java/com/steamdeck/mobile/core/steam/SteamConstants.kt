package com.steamdeck.mobile.core.steam

/**
 * Steam Client Configuration Constants
 *
 * Centralized configuration for Steam-related timeouts and limits
 *
 * Best Practice: Avoid magic numbers in code by defining constants here
 */
object SteamConstants {
    /**
     * Steam client initialization timeout (milliseconds)
     *
     * Time to wait for Steam client to initialize after launch.
     * Steam initialization includes:
     * - Process startup
     * - Wine/Box64 environment setup
     * - Steam bootstrap manifest download
     * - GUI rendering
     *
     * Typical initialization time: 5-10 seconds (device-dependent)
     */
    const val STEAM_INITIALIZATION_TIMEOUT_MS = 10_000L // 10 seconds

    /**
     * QR authentication completion wait time (milliseconds)
     *
     * Minimal delay to ensure QR authentication flow completes
     * before triggering subsequent operations (VDF writing, sync)
     *
     * NOTE: This is a safety buffer. Real synchronization should use
     * Flow-based waiting (e.g., securePreferences.getSteamId().first { it != null })
     */
    const val QR_AUTH_COMPLETION_DELAY_MS = 500L // 0.5 seconds

    /**
     * Steam executable path relative to Wine container
     */
    const val STEAM_INSTALL_PATH = "drive_c/Program Files (x86)/Steam"

    /**
     * Steam configuration directory relative to Steam installation
     */
    const val STEAM_CONFIG_DIR = "config"

    /**
     * Steam executable filename
     */
    const val STEAM_EXE_NAME = "Steam.exe"
}
