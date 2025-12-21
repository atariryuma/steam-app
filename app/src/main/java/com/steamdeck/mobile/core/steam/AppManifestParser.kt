package com.steamdeck.mobile.core.steam

import android.util.Log
import java.io.File

/**
 * Steam ACF (App Cache File) manifest parser
 *
 * Parses appmanifest_*.acf files in Valve KeyValue format to extract
 * game installation metadata including state, build ID, and installation directory.
 *
 * ACF Format Example:
 * ```
 * "AppState"
 * {
 *     "appid"        "730"
 *     "name"         "Counter-Strike: Global Offensive"
 *     "installdir"   "Counter-Strike Global Offensive"
 *     "StateFlags"   "4"
 *     "buildid"      "987654"
 *     "LastUpdated"  "1703123456"
 * }
 * ```
 *
 * StateFlags values:
 * - 0: Not installed
 * - 1: Update required
 * - 2: Update running (downloading)
 * - 4: Fully installed (playable)
 * - 6: Update paused
 */
data class AppManifest(
    val appId: Long,
    val name: String,
    val installDir: String,
    val stateFlags: Int,
    val buildId: String?,
    val lastUpdated: Long?
)

/**
 * Parser for Steam appmanifest_*.acf files
 */
object AppManifestParser {
    private const val TAG = "AppManifestParser"

    // Regex for Valve KeyValue format: "key"  "value"
    private val keyValueRegex = Regex("\"(\\w+)\"\\s+\"([^\"]+)\"")

    /**
     * Parse an ACF manifest file
     *
     * @param file The appmanifest_*.acf file to parse
     * @return Result containing parsed AppManifest or error
     */
    fun parse(file: File): Result<AppManifest> {
        return try {
            if (!file.exists()) {
                return Result.failure(Exception("Manifest file not found: ${file.absolutePath}"))
            }

            if (!file.canRead()) {
                return Result.failure(Exception("Cannot read manifest file: ${file.absolutePath}"))
            }

            Log.d(TAG, "Parsing ACF manifest: ${file.name}")

            val content = file.readText()

            // Extract key-value pairs
            val values = keyValueRegex.findAll(content)
                .associate { matchResult ->
                    matchResult.groupValues[1] to matchResult.groupValues[2]
                }

            // Validate required fields
            val appId = values["appid"]?.toLongOrNull()
                ?: return Result.failure(Exception("Missing or invalid appid in manifest"))

            val name = values["name"]
                ?: return Result.failure(Exception("Missing name in manifest"))

            val installDir = values["installdir"]
                ?: return Result.failure(Exception("Missing installdir in manifest"))

            val stateFlags = values["StateFlags"]?.toIntOrNull() ?: 0

            // Optional fields
            val buildId = values["buildid"]
            val lastUpdated = values["LastUpdated"]?.toLongOrNull()

            val manifest = AppManifest(
                appId = appId,
                name = name,
                installDir = installDir,
                stateFlags = stateFlags,
                buildId = buildId,
                lastUpdated = lastUpdated
            )

            Log.d(TAG, "Parsed manifest: appId=$appId, name=$name, stateFlags=$stateFlags")

            Result.success(manifest)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ACF manifest: ${file.absolutePath}", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a manifest indicates the game is fully installed
     *
     * @param manifest The parsed AppManifest
     * @return true if StateFlags == 4 (fully installed)
     */
    fun isFullyInstalled(manifest: AppManifest): Boolean {
        return manifest.stateFlags == 4
    }

    /**
     * Check if a manifest indicates the game is downloading
     *
     * @param manifest The parsed AppManifest
     * @return true if StateFlags == 2 (downloading)
     */
    fun isDownloading(manifest: AppManifest): Boolean {
        return manifest.stateFlags == 2
    }

    /**
     * Get human-readable state description
     *
     * @param stateFlags The StateFlags value from manifest
     * @return User-friendly state description
     */
    fun getStateDescription(stateFlags: Int): String {
        return when (stateFlags) {
            0 -> "Not installed"
            1 -> "Update required"
            2 -> "Downloading"
            4 -> "Fully installed"
            6 -> "Update paused"
            else -> "Unknown state ($stateFlags)"
        }
    }
}
