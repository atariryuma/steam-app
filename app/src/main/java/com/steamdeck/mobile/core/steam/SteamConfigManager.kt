package com.steamdeck.mobile.core.steam

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Configuration Manager
 *
 * Manages Steam client configuration files (config.vdf) to ensure proper
 * network connectivity in Wine/PRoot environments.
 *
 * Key responsibilities:
 * - Pre-configure Steam content servers to bypass bootstrap download failures
 * - Set optimal download regions and network settings
 * - Prevent "Content Servers Unreachable" errors in Wine
 *
 * Based on: https://gist.github.com/neowinx/1a03ee15bd7fb8d3f56ced965a4499eb
 *           https://steamcommunity.com/discussions/forum/1/2381701715716658433/
 */
@Singleton
class SteamConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SteamConfigManager"

        // Valve's official content servers (reliable CDN endpoints)
        private val CONTENT_SERVERS = listOf(
            "valve500.steamcontent.com",
            "valve501.steamcontent.com",
            "valve517.steamcontent.com",
            "valve557.steamcontent.com",
            "valve558.steamcontent.com",
            "valve559.steamcontent.com",
            "valve560.steamcontent.com"
        )

        // Steam Content Management servers (bootstrap)
        private val CM_SERVERS = listOf(
            "162.254.197.40:27017",
            "162.254.197.40:27018",
            "162.254.197.40:27019",
            "162.254.197.41:27017"
        )
    }

    /**
     * Create Steam config.vdf with pre-configured content servers
     *
     * This method creates the config/config.vdf file BEFORE Steam's first launch,
     * pre-populating it with known-good content server addresses. This bypasses
     * Steam's bootstrap manifest download, which often fails in Wine/PRoot environments.
     *
     * VDF Format: Valve's KeyValue text format (similar to JSON but tab-indented)
     *
     * @param containerDir Wine container directory containing Steam installation
     * @param accountName Optional Steam account name (NOT SteamID64) for auto-login
     *                    Example: "myusername" (the name used for Steam login)
     * @return Result indicating success or failure
     */
    suspend fun createConfigVdf(containerDir: File, accountName: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val steamDir = File(containerDir, SteamConstants.STEAM_INSTALL_PATH)
            if (!steamDir.exists()) {
                AppLogger.w(TAG, "Steam directory not found: ${steamDir.absolutePath}")
                return@withContext Result.failure(
                    Exception("Steam not installed at ${steamDir.absolutePath}")
                )
            }

            val configDir = File(steamDir, SteamConstants.STEAM_CONFIG_DIR)
            if (!configDir.exists()) {
                val created = configDir.mkdirs()
                if (!created) {
                    return@withContext Result.failure(
                        Exception("Failed to create config directory: ${configDir.absolutePath}")
                    )
                }
                AppLogger.d(TAG, "Created config directory: ${configDir.absolutePath}")
            }

            val configVdf = File(configDir, "config.vdf")

            // Generate VDF content with proper formatting
            val vdfContent = buildString {
                appendLine("\"InstallConfigStore\"")
                appendLine("{")
                appendLine("\t\"Software\"")
                appendLine("\t{")
                appendLine("\t\t\"Valve\"")
                appendLine("\t\t{")
                appendLine("\t\t\t\"Steam\"")
                appendLine("\t\t\t{")

                // Content Servers (CS) - semicolon-separated list
                append("\t\t\t\t\"CS\"\t\t\"")
                append(CONTENT_SERVERS.joinToString(";"))
                appendLine("\"")

                // Content Management Servers (CM) - semicolon-separated list
                append("\t\t\t\t\"CM\"\t\t\"")
                append(CM_SERVERS.joinToString(";"))
                appendLine("\"")

                // Download Region - US Washington DC (reliable, low-latency)
                appendLine("\t\t\t\t\"DownloadRegion\"\t\t\"US - Washington DC\"")

                // Disable auto-update window (prevents update prompts during gaming)
                appendLine("\t\t\t\t\"AutoUpdateWindowEnabled\"\t\t\"0\"")

                // CRITICAL: Disable automatic manifest verification
                // This prevents Steam from trying to re-download content servers list
                // which often fails in Wine environments
                appendLine("\t\t\t\t\"AutoUpdateBehavior\"\t\t\"UpdateOnlyOnClose\"")

                // AUTO-LOGIN CONFIGURATION (if account name provided)
                // This enables automatic login using credentials from loginusers.vdf
                // IMPORTANT: AutoLoginUser requires Steam account name, NOT SteamID64
                if (accountName != null) {
                    appendLine("\t\t\t\t\"AutoLoginUser\"\t\t\"$accountName\"")
                    appendLine("\t\t\t\t\"RememberPassword\"\t\t\"1\"")
                }

                appendLine("\t\t\t}")
                appendLine("\t\t}")
                appendLine("\t}")
                appendLine("}")
            }

            configVdf.writeText(vdfContent)
            AppLogger.i(TAG, "Created Steam config.vdf with pre-configured content servers")
            AppLogger.d(TAG, "  Content Servers: ${CONTENT_SERVERS.size} endpoints")
            AppLogger.d(TAG, "  CM Servers: ${CM_SERVERS.size} endpoints")
            AppLogger.d(TAG, "  Download Region: US - Washington DC")
            if (accountName != null) {
                AppLogger.d(TAG, "  Auto-Login User: $accountName (enabled)")
            }
            AppLogger.d(TAG, "  Config path: ${configVdf.absolutePath}")

            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create config.vdf", e)
            Result.failure(e)
        }
    }

    /**
     * Create config.vdf with cache guard (5-minute timeout)
     *
     * Prevents duplicate VDF writes by checking if the file was recently created.
     * If the file was modified within the last 5 minutes, skip writing.
     *
     * Use case: Avoid redundant writes when called from multiple locations
     * (e.g., SettingsViewModel and SteamDisplayViewModel)
     *
     * @param containerDir Wine container directory containing Steam installation
     * @param accountName Optional Steam account name (NOT SteamID64) for auto-login
     * @return VdfWriteResult - Created/Skipped/Error with detailed state
     */
    suspend fun createConfigVdfIfNeeded(containerDir: File, accountName: String? = null): VdfWriteResult = withContext(Dispatchers.IO) {
        try {
            val vdfFile = File(containerDir, "${SteamConstants.STEAM_INSTALL_PATH}/${SteamConstants.STEAM_CONFIG_DIR}/config.vdf")

            // Cache guard: Check if file needs updating
            if (!vdfFile.shouldWriteVdf()) {
                val ageSeconds = (System.currentTimeMillis() - vdfFile.lastModified()) / 1000
                AppLogger.d(TAG, "Skipping config.vdf write (cached, age: ${ageSeconds}s)")
                return@withContext VdfWriteResult.Skipped(ageSeconds)
            }

            // File expired or doesn't exist, create/update
            val result = createConfigVdf(containerDir, accountName)
            return@withContext if (result.isSuccess) {
                AppLogger.d(TAG, "config.vdf created/updated")
                VdfWriteResult.Created
            } else {
                val throwable = result.exceptionOrNull() ?: Exception("Unknown error")
                val exception = if (throwable is Exception) throwable else Exception(throwable.message, throwable)
                VdfWriteResult.Error(
                    message = exception.message ?: "Failed to create config.vdf",
                    exception = exception
                )
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create config.vdf with cache guard", e)
            VdfWriteResult.Error(
                message = e.message ?: "Unexpected error",
                exception = e
            )
        }
    }

    /**
     * Update existing config.vdf with content server settings
     *
     * This method updates an existing config.vdf file by merging in the
     * content server configuration. Preserves existing settings while
     * ensuring CS/CM entries are correct.
     *
     * @param containerDir Wine container directory
     * @return Result indicating success or failure
     */
    suspend fun updateConfigVdf(containerDir: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val configVdf = File(containerDir, "${SteamConstants.STEAM_INSTALL_PATH}/${SteamConstants.STEAM_CONFIG_DIR}/config.vdf")

            if (!configVdf.exists()) {
                AppLogger.d(TAG, "config.vdf doesn't exist, creating new one")
                return@withContext createConfigVdf(containerDir)
            }

            AppLogger.d(TAG, "Updating existing config.vdf with content server settings")

            // Read existing content
            val existingContent = configVdf.readText()

            // SIMPLE APPROACH: If CS or CM entries already exist, assume config is valid
            // This prevents overwriting user's custom settings
            if (existingContent.contains("\"CS\"") && existingContent.contains("\"CM\"")) {
                AppLogger.i(TAG, "config.vdf already contains CS/CM entries, skipping update")
                return@withContext Result.success(Unit)
            }

            // Otherwise, recreate the file with our default configuration
            AppLogger.i(TAG, "config.vdf missing CS/CM entries, recreating")
            return@withContext createConfigVdf(containerDir)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update config.vdf", e)
            Result.failure(e)
        }
    }

    /**
     * Verify config.vdf exists and contains valid content server configuration
     *
     * @param containerDir Wine container directory
     * @return true if config.vdf is valid, false otherwise
     */
    suspend fun verifyConfigVdf(containerDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val configVdf = File(containerDir, "${SteamConstants.STEAM_INSTALL_PATH}/${SteamConstants.STEAM_CONFIG_DIR}/config.vdf")

            if (!configVdf.exists()) {
                AppLogger.w(TAG, "config.vdf does not exist")
                return@withContext false
            }

            val content = configVdf.readText()
            val hasCS = content.contains("\"CS\"")
            val hasCM = content.contains("\"CM\"")

            if (hasCS && hasCM) {
                AppLogger.i(TAG, "config.vdf verification passed (CS and CM entries present)")
                true
            } else {
                AppLogger.w(TAG, "config.vdf verification failed (missing CS=$hasCS, CM=$hasCM)")
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to verify config.vdf", e)
            false
        }
    }
}
