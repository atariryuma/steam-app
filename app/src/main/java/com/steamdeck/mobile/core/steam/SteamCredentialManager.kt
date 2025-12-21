package com.steamdeck.mobile.core.steam

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.domain.repository.ISecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Credential Manager
 *
 * Writes Steam client configuration files (loginusers.vdf, config.vdf)
 * to enable auto-login functionality after QR authentication.
 *
 * Architecture:
 * - Write-only VDF generator (no parsing needed for initial implementation)
 * - Uses Kotlin string templates for VDF format generation
 * - No external dependencies (APK size: +0 MB)
 *
 * VDF Format Specification:
 * - KeyValue format with nested braces {} and quoted strings
 * - Control characters: {, }, " (quotes only for tokens)
 * - Tab-indented hierarchy (Steam convention)
 *
 * References:
 * - VDF Format: https://developer.valvesoftware.com/wiki/VDF
 * - KeyValues Spec: https://developer.valvesoftware.com/wiki/KeyValues
 * - Steam Config Files: https://github.com/TCNOco/TcNo-Acc-Switcher/wiki/Platform:-Steam
 * - loginusers.vdf Example: https://gist.github.com/uranio-235/e44cf156c0c3c2cedcb03c618407a973
 *
 * Best Practices (2025):
 * - Minimal VDF structure (only required fields)
 * - UTF-8 encoding (Steam standard)
 * - Unix line endings for Wine compatibility
 * - Atomic file writes (write to temp, then rename)
 *
 * Security:
 * - Does NOT store passwords/tokens in VDF files
 * - Only stores SteamID64 and account names (public information)
 * - Actual authentication handled by Steam client
 */
@Singleton
class SteamCredentialManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: ISecurePreferences
) {
    companion object {
        private const val TAG = "SteamCredentialManager"
    }

    /**
     * Write Steam credentials to VDF files
     *
     * Creates/updates:
     * 1. loginusers.vdf - User account information for auto-login
     * 2. config.vdf - Steam client configuration (AutoLoginUser setting)
     *
     * Files are created in Wine container's Steam config directory:
     * <container>/drive_c/Program Files (x86)/Steam/config/
     *
     * @param containerId Wine container ID where Steam is installed
     * @param steamId SteamID64 from QR authentication (e.g., "76561198245791652")
     * @param accountName Steam account name (optional, defaults to steamId)
     * @param personaName Steam display name (optional, defaults to accountName)
     * @return Result indicating success or failure
     */
    suspend fun writeSteamCredentials(
        containerId: String,
        steamId: String,
        accountName: String? = null,
        personaName: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Writing Steam credentials for SteamID: $steamId, Container: $containerId")

            // Validate SteamID64 format (must be 17-digit number starting with 7656119)
            if (!isValidSteamId64(steamId)) {
                AppLogger.e(TAG, "Invalid SteamID64 format: $steamId")
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid SteamID64 format: $steamId")
                )
            }

            val steamConfigDir = File(
                context.filesDir,
                "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/config"
            )

            // Create config directory if it doesn't exist
            if (!steamConfigDir.exists()) {
                val created = steamConfigDir.mkdirs()
                if (!created) {
                    AppLogger.e(TAG, "Failed to create config directory: ${steamConfigDir.absolutePath}")
                    return@withContext Result.failure(
                        Exception("Failed to create Steam config directory")
                    )
                }
                AppLogger.d(TAG, "Created config directory: ${steamConfigDir.absolutePath}")
            }

            val username = accountName ?: steamId
            val displayName = personaName ?: username
            val timestamp = System.currentTimeMillis() / 1000 // Unix timestamp

            // 1. Write loginusers.vdf
            val loginUsersResult = writeLoginUsersVdf(
                configDir = steamConfigDir,
                steamId = steamId,
                accountName = username,
                personaName = displayName,
                timestamp = timestamp
            )

            if (loginUsersResult.isFailure) {
                return@withContext loginUsersResult
            }

            // 2. Write config.vdf (minimal version - only auto-login settings)
            val configResult = writeConfigVdf(
                configDir = steamConfigDir,
                accountName = username
            )

            if (configResult.isFailure) {
                return@withContext configResult
            }

            AppLogger.i(TAG, "✅ Steam credentials written successfully")
            AppLogger.d(TAG, "  SteamID: $steamId")
            AppLogger.d(TAG, "  Account: $username")
            AppLogger.d(TAG, "  Display: $displayName")
            AppLogger.d(TAG, "  Location: ${steamConfigDir.absolutePath}")

            Result.success(Unit)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to write Steam credentials", e)
            Result.failure(e)
        }
    }

    /**
     * Write loginusers.vdf file
     *
     * This file contains user account information that Steam uses for:
     * - Remembering logged-in users
     * - Auto-login functionality
     * - Offline mode settings
     *
     * VDF Structure (from official Steam client):
     * "users"
     * {
     *     "76561198245791652"  // SteamID64 (key)
     *     {
     *         "AccountName"           "username"
     *         "PersonaName"           "Display Name"
     *         "RememberPassword"      "1"      // Enable auto-login
     *         "MostRecent"            "1"      // Last logged-in user
     *         "WantsOfflineMode"      "0"      // Don't start in offline mode
     *         "SkipOfflineModeWarning" "0"     // Show offline warning
     *         "AllowAutoLogin"        "1"      // Allow auto-login
     *         "Timestamp"             "1734779654"
     *     }
     * }
     *
     * @param configDir Steam config directory
     * @param steamId SteamID64
     * @param accountName Steam account name
     * @param personaName Steam display name
     * @param timestamp Unix timestamp
     * @return Result indicating success or failure
     */
    private fun writeLoginUsersVdf(
        configDir: File,
        steamId: String,
        accountName: String,
        personaName: String,
        timestamp: Long
    ): Result<Unit> {
        return try {
            // Generate VDF content with proper formatting
            // Note: Steam uses tabs for indentation (not spaces)
            val vdfContent = buildString {
                appendLine("\"users\"")
                appendLine("{")
                appendLine("\t\"$steamId\"")
                appendLine("\t{")
                appendLine("\t\t\"AccountName\"\t\t\"$accountName\"")
                appendLine("\t\t\"PersonaName\"\t\t\"$personaName\"")
                appendLine("\t\t\"RememberPassword\"\t\t\"1\"")
                appendLine("\t\t\"MostRecent\"\t\t\"1\"")
                appendLine("\t\t\"WantsOfflineMode\"\t\t\"0\"")
                appendLine("\t\t\"SkipOfflineModeWarning\"\t\t\"0\"")
                appendLine("\t\t\"AllowAutoLogin\"\t\t\"1\"")
                appendLine("\t\t\"Timestamp\"\t\t\"$timestamp\"")
                appendLine("\t}")
                appendLine("}")
            }

            val file = File(configDir, "loginusers.vdf")

            // Atomic write: write to temp file, then rename
            val tempFile = File(configDir, "loginusers.vdf.tmp")
            tempFile.writeText(vdfContent, Charsets.UTF_8)

            // Rename temp file to actual file (atomic operation on most filesystems)
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)

            AppLogger.i(TAG, "✅ Written loginusers.vdf: ${file.absolutePath}")
            AppLogger.d(TAG, "File size: ${file.length()} bytes")

            Result.success(Unit)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to write loginusers.vdf", e)
            Result.failure(e)
        }
    }

    /**
     * Write config.vdf file
     *
     * This file contains Steam client configuration, including:
     * - Auto-login user setting
     * - Remember password setting
     * - Family View settings (not implemented here)
     *
     * VDF Structure (minimal version for auto-login):
     * "InstallConfigStore"
     * {
     *     "Software"
     *     {
     *         "Valve"
     *         {
     *             "Steam"
     *             {
     *                 "AutoLoginUser"    "username"
     *                 "RememberPassword" "1"
     *             }
     *         }
     *     }
     * }
     *
     * Note: This is a minimal implementation. The actual Steam config.vdf
     * contains many more settings, but these are sufficient for auto-login.
     *
     * @param configDir Steam config directory
     * @param accountName Steam account name
     * @return Result indicating success or failure
     */
    private fun writeConfigVdf(
        configDir: File,
        accountName: String
    ): Result<Unit> {
        return try {
            // Generate minimal config.vdf for auto-login
            val vdfContent = buildString {
                appendLine("\"InstallConfigStore\"")
                appendLine("{")
                appendLine("\t\"Software\"")
                appendLine("\t{")
                appendLine("\t\t\"Valve\"")
                appendLine("\t\t{")
                appendLine("\t\t\t\"Steam\"")
                appendLine("\t\t\t{")
                appendLine("\t\t\t\t\"AutoLoginUser\"\t\t\"$accountName\"")
                appendLine("\t\t\t\t\"RememberPassword\"\t\t\"1\"")
                appendLine("\t\t\t}")
                appendLine("\t\t}")
                appendLine("\t}")
                appendLine("}")
            }

            val file = File(configDir, "config.vdf")

            // Atomic write: write to temp file, then rename
            val tempFile = File(configDir, "config.vdf.tmp")
            tempFile.writeText(vdfContent, Charsets.UTF_8)

            // Rename temp file to actual file
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)

            AppLogger.i(TAG, "✅ Written config.vdf: ${file.absolutePath}")
            AppLogger.d(TAG, "File size: ${file.length()} bytes")

            Result.success(Unit)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to write config.vdf", e)
            Result.failure(e)
        }
    }

    /**
     * Validate SteamID64 format
     *
     * SteamID64 format:
     * - 17-digit decimal number
     * - Starts with "7656119" (Steam account prefix)
     * - Range: 76561197960265728 ~ 76561202255233023 (valid Steam accounts)
     *
     * @param steamId SteamID64 to validate
     * @return true if valid, false otherwise
     */
    private fun isValidSteamId64(steamId: String): Boolean {
        // Must be 17 digits
        if (steamId.length != 17) return false

        // Must be numeric
        val steamIdLong = steamId.toLongOrNull() ?: return false

        // Must start with 7656119 (Steam account prefix)
        if (!steamId.startsWith("7656119")) return false

        // Must be in valid range
        val minSteamId = 76561197960265728L
        val maxSteamId = 76561202255233023L
        if (steamIdLong !in minSteamId..maxSteamId) return false

        return true
    }

    /**
     * Check if Steam credentials are configured for a container
     *
     * Verifies that both loginusers.vdf and config.vdf exist
     * in the specified container's Steam config directory.
     *
     * @param containerId Wine container ID
     * @return true if credentials are configured, false otherwise
     */
    suspend fun areCredentialsConfigured(containerId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val steamConfigDir = File(
                context.filesDir,
                "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/config"
            )

            val loginUsersFile = File(steamConfigDir, "loginusers.vdf")
            val configFile = File(steamConfigDir, "config.vdf")

            val configured = loginUsersFile.exists() && configFile.exists()

            if (configured) {
                AppLogger.d(TAG, "Steam credentials configured for container: $containerId")
            } else {
                AppLogger.d(TAG, "Steam credentials NOT configured for container: $containerId")
                AppLogger.d(TAG, "  loginusers.vdf exists: ${loginUsersFile.exists()}")
                AppLogger.d(TAG, "  config.vdf exists: ${configFile.exists()}")
            }

            configured

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to check credentials configuration", e)
            false
        }
    }

    /**
     * Clear Steam credentials for a container
     *
     * Deletes loginusers.vdf and config.vdf files.
     * Useful when logging out or switching Steam accounts.
     *
     * @param containerId Wine container ID
     * @return Result indicating success or failure
     */
    suspend fun clearCredentials(containerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Clearing Steam credentials for container: $containerId")

            val steamConfigDir = File(
                context.filesDir,
                "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/config"
            )

            val loginUsersFile = File(steamConfigDir, "loginusers.vdf")
            val configFile = File(steamConfigDir, "config.vdf")

            var deleted = 0
            if (loginUsersFile.exists()) {
                loginUsersFile.delete()
                deleted++
            }
            if (configFile.exists()) {
                configFile.delete()
                deleted++
            }

            AppLogger.i(TAG, "✅ Cleared $deleted credential file(s)")
            Result.success(Unit)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to clear credentials", e)
            Result.failure(e)
        }
    }
}
