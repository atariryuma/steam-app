package com.steamdeck.mobile.core.steam

import java.io.File

/**
 * VDF File Cache Configuration
 *
 * Cache timeout for VDF file writes (prevents redundant writes within this period)
 */
const val VDF_CACHE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

/**
 * VDF File Cache Guard
 *
 * Prevents redundant VDF file writes by checking file modification time.
 * Skips write if file was modified within the cache timeout period.
 *
 * Use case: Avoid duplicate writes when called from multiple locations
 * (e.g., SettingsViewModel and SteamDisplayViewModel)
 *
 * @receiver File to check
 * @param cacheTimeoutMillis Cache timeout in milliseconds (default: 5 minutes)
 * @return true if file should be written (expired or doesn't exist), false if cached
 */
fun File.shouldWriteVdf(cacheTimeoutMillis: Long = VDF_CACHE_TIMEOUT_MS): Boolean {
    if (!exists()) return true // File doesn't exist, should write

    val age = System.currentTimeMillis() - lastModified()
    return age >= cacheTimeoutMillis
}
