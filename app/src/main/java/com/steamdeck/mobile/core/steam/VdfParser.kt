package com.steamdeck.mobile.core.steam

import com.steamdeck.mobile.core.logging.AppLogger

/**
 * Parser for Valve Data Format (VDF) files
 *
 * VDF is a simple key-value text format used by Steam for configuration and manifest files.
 * Format example:
 * ```
 * "key"
 * {
 *     "nested_key"    "value"
 *     "number_key"    "123"
 * }
 * ```
 *
 * This parser extracts package information from Steam client manifests.
 */
object VdfParser {
    private const val TAG = "VdfParser"

    /**
     * Represents a Steam package in the manifest
     */
    data class SteamPackage(
        val name: String,
        val filename: String,
        val size: Long,
        val sha256: String
    )

    /**
     * Parse Steam client manifest and extract package list
     *
     * @param vdfContent Raw VDF manifest content
     * @param packageNames List of package names to extract (e.g., "bins_win32", "bins_cef_win32")
     * @return List of SteamPackage objects
     */
    fun parseManifest(vdfContent: String, packageNames: List<String>): List<SteamPackage> {
        val packages = mutableListOf<SteamPackage>()

        try {
            val lines = vdfContent.lines()
            var currentPackageName: String? = null
            var currentFile: String? = null
            var currentSize: Long? = null
            var currentSha256: String? = null

            for (i in lines.indices) {
                val line = lines[i].trim()

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("//")) continue

                // Check if this is a package we're interested in
                for (packageName in packageNames) {
                    if (line.startsWith("\"$packageName\"")) {
                        currentPackageName = packageName
                        AppLogger.d(TAG, "Found package: $packageName at line $i")
                        break
                    }
                }

                // Extract file, size, and sha2 fields
                if (currentPackageName != null) {
                    when {
                        line.contains("\"file\"") -> {
                            currentFile = extractValue(line)
                        }
                        line.contains("\"size\"") -> {
                            currentSize = extractValue(line)?.toLongOrNull()
                        }
                        line.contains("\"sha2\"") -> {
                            currentSha256 = extractValue(line)

                            // When we have all three fields, create package
                            if (currentFile != null && currentSize != null && currentSha256 != null) {
                                packages.add(
                                    SteamPackage(
                                        name = currentPackageName,
                                        filename = currentFile,
                                        size = currentSize,
                                        sha256 = currentSha256
                                    )
                                )
                                AppLogger.i(
                                    TAG,
                                    "Parsed package: $currentPackageName ($currentFile, ${currentSize / 1024 / 1024}MB)"
                                )

                                // Reset for next package
                                currentPackageName = null
                                currentFile = null
                                currentSize = null
                                currentSha256 = null
                            }
                        }
                    }
                }
            }

            AppLogger.i(TAG, "Successfully parsed ${packages.size} packages from manifest")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse VDF manifest", e)
        }

        return packages
    }

    /**
     * Extract quoted value from VDF line
     * Example: "key"    "value" -> "value"
     */
    private fun extractValue(line: String): String? {
        // Find all quoted strings in the line
        val quotes = mutableListOf<Int>()
        var i = 0
        while (i < line.length) {
            if (line[i] == '"' && (i == 0 || line[i - 1] != '\\')) {
                quotes.add(i)
            }
            i++
        }

        // We need at least 4 quotes: "key" "value"
        if (quotes.size >= 4) {
            val startQuote = quotes[2]
            val endQuote = quotes[3]
            return line.substring(startQuote + 1, endQuote)
        }

        return null
    }
}
