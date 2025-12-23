package com.steamdeck.mobile.core.steam

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Authentication Manager
 *
 * Generates Steam client authentication files using stored credentials from
 * Steam Web API authentication (QR code login).
 *
 * This enables automatic login without manual username/password entry,
 * dramatically improving user experience.
 *
 * Key files generated:
 * - loginusers.vdf: User account information and auto-login preferences
 * - config.vdf: Auto-login user setting (handled by SteamConfigManager)
 *
 * VDF Format: Valve's KeyValue text format (tab-indented, similar to JSON)
 *
 * Security:
 * - No passwords stored (only Steam ID and username)
 * - Uses Android EncryptedSharedPreferences for credential storage
 * - Follows Steam's official "Remember Password" mechanism
 *
 * Steam ToS Compliance: ✅ COMPLIANT
 * - Uses official VDF format
 * - Mimics Steam's built-in auto-login feature
 * - No credential theft or protocol emulation
 */
@Singleton
class SteamAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: ISecurePreferences
) {
    companion object {
        private const val TAG = "SteamAuthManager"
    }

    /**
     * Create loginusers.vdf with stored Steam credentials
     *
     * This file tells Steam client which user to auto-login and enables
     * the "Remember Password" functionality.
     *
     * File location: {container}/drive_c/Program Files (x86)/Steam/config/loginusers.vdf
     *
     * @param containerDir Wine container directory containing Steam installation
     * @return Result indicating success or failure
     */
    suspend fun createLoginUsersVdf(containerDir: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get stored credentials from encrypted preferences
            val steamId = securePreferences.getSteamId().first()
            if (steamId == null) {
                AppLogger.w(TAG, "Steam ID not found - user must login via QR code first")
                return@withContext Result.failure(
                    Exception("Steam ID not found. Please login via QR code in Settings.")
                )
            }

            val username = securePreferences.getSteamUsername().first() ?: "SteamUser"

            // Validate SteamID64 format (17 digits, starts with 7656119)
            if (steamId.length != 17 || !steamId.startsWith("7656119")) {
                AppLogger.e(TAG, "Invalid SteamID64 format: $steamId")
                return@withContext Result.failure(
                    Exception("Invalid Steam ID format: $steamId")
                )
            }

            val steamDir = File(containerDir, "drive_c/Program Files (x86)/Steam")
            if (!steamDir.exists()) {
                AppLogger.w(TAG, "Steam directory not found: ${steamDir.absolutePath}")
                return@withContext Result.failure(
                    Exception("Steam not installed at ${steamDir.absolutePath}")
                )
            }

            val configDir = File(steamDir, "config")
            if (!configDir.exists()) {
                val created = configDir.mkdirs()
                if (!created) {
                    return@withContext Result.failure(
                        Exception("Failed to create config directory: ${configDir.absolutePath}")
                    )
                }
                AppLogger.d(TAG, "Created config directory: ${configDir.absolutePath}")
            }

            val loginUsersVdf = File(configDir, "loginusers.vdf")

            // Generate VDF content with proper formatting
            // Format: Valve KeyValue (VDF) - tab-indented hierarchy
            val vdfContent = buildString {
                appendLine("\"users\"")
                appendLine("{")
                appendLine("\t\"$steamId\"")
                appendLine("\t{")
                appendLine("\t\t\"AccountName\"\t\t\"$username\"")
                appendLine("\t\t\"PersonaName\"\t\t\"$username\"")
                appendLine("\t\t\"RememberPassword\"\t\t\"1\"")
                appendLine("\t\t\"MostRecent\"\t\t\"1\"")
                appendLine("\t\t\"AllowAutoLogin\"\t\t\"1\"")

                // Timestamp: Unix epoch (seconds since 1970-01-01)
                val timestamp = System.currentTimeMillis() / 1000
                appendLine("\t\t\"Timestamp\"\t\t\"$timestamp\"")

                appendLine("\t}")
                appendLine("}")
            }

            loginUsersVdf.writeText(vdfContent)
            AppLogger.i(TAG, "Created loginusers.vdf for auto-login")
            AppLogger.d(TAG, "  Steam ID: $steamId")
            AppLogger.d(TAG, "  Username: $username")
            AppLogger.d(TAG, "  File path: ${loginUsersVdf.absolutePath}")

            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create loginusers.vdf", e)
            Result.failure(e)
        }
    }

    /**
     * Verify loginusers.vdf exists and contains valid configuration
     *
     * @param containerDir Wine container directory
     * @return true if loginusers.vdf is valid, false otherwise
     */
    suspend fun verifyLoginUsersVdf(containerDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val loginUsersVdf = File(containerDir, "drive_c/Program Files (x86)/Steam/config/loginusers.vdf")

            if (!loginUsersVdf.exists()) {
                AppLogger.w(TAG, "loginusers.vdf does not exist")
                return@withContext false
            }

            val content = loginUsersVdf.readText()

            // Basic validation: Check for required fields
            val hasUsers = content.contains("\"users\"")
            val hasAccountName = content.contains("\"AccountName\"")
            val hasRememberPassword = content.contains("\"RememberPassword\"")
            val hasAllowAutoLogin = content.contains("\"AllowAutoLogin\"")

            if (hasUsers && hasAccountName && hasRememberPassword && hasAllowAutoLogin) {
                AppLogger.i(TAG, "loginusers.vdf verification passed")
                true
            } else {
                AppLogger.w(TAG, "loginusers.vdf verification failed (missing required fields)")
                AppLogger.d(TAG, "  hasUsers: $hasUsers")
                AppLogger.d(TAG, "  hasAccountName: $hasAccountName")
                AppLogger.d(TAG, "  hasRememberPassword: $hasRememberPassword")
                AppLogger.d(TAG, "  hasAllowAutoLogin: $hasAllowAutoLogin")
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to verify loginusers.vdf", e)
            false
        }
    }

    /**
     * Delete loginusers.vdf (for logout/reset purposes)
     *
     * @param containerDir Wine container directory
     * @return Result indicating success or failure
     */
    suspend fun deleteLoginUsersVdf(containerDir: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val loginUsersVdf = File(containerDir, "drive_c/Program Files (x86)/Steam/config/loginusers.vdf")

            if (loginUsersVdf.exists()) {
                val deleted = loginUsersVdf.delete()
                if (deleted) {
                    AppLogger.i(TAG, "Deleted loginusers.vdf successfully")
                    Result.success(Unit)
                } else {
                    AppLogger.w(TAG, "Failed to delete loginusers.vdf")
                    Result.failure(Exception("Failed to delete loginusers.vdf"))
                }
            } else {
                AppLogger.d(TAG, "loginusers.vdf does not exist, nothing to delete")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete loginusers.vdf", e)
            Result.failure(e)
        }
    }
}
