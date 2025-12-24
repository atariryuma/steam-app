package com.steamdeck.mobile.core.security

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger

/**
 * Path validation utility for preventing path traversal attacks.
 *
 * Based on Android Security best practices:
 * - https://developer.android.com/privacy-and-security/risks/path-traversal
 * - Uses canonicalPath to resolve symlinks and relative paths
 *
 * Usage:
 * ```
 * if (!PathValidator.isPathSafe(userInputPath, context)) {
 *     // Reject dangerous path
 * }
 * ```
 */
object PathValidator {
    private const val TAG = "PathValidator"

    /**
     * Validates if a file path is safe from path traversal attacks.
     *
     * Checks:
     * 1. Allows content:// URIs (managed by system)
     * 2. Detects dangerous patterns (.., ~, null bytes, URL-encoded attacks)
     * 3. Verifies path is within allowed directories (app data or external storage)
     * 4. Ensures canonical path doesn't escape parent directory
     *
     * @param path The file path or content:// URI to validate
     * @param context Android context for accessing app directories
     * @return true if path is safe, false if potential security risk detected
     */
    fun isPathSafe(path: String, context: Context): Boolean {
        try {
            // Allow content:// URIs (Storage Access Framework)
            if (path.startsWith("content://")) {
                // content:// URIs are managed by the system and are safe
                return true
            }

            // Detect dangerous patterns BEFORE canonicalization
            val dangerousPatterns = listOf(
                "..",      // Parent directory traversal
                "~",       // Home directory expansion
                "\u0000",  // Null byte injection
                "%2e%2e",  // URL-encoded ..
                "%00"      // URL-encoded null byte
            )

            for (pattern in dangerousPatterns) {
                if (path.contains(pattern, ignoreCase = true)) {
                    AppLogger.w(TAG, "Path traversal attack detected: pattern '$pattern' in path")
                    return false
                }
            }

            // Canonicalize path (resolves symlinks, removes .., etc.)
            val file = java.io.File(path)
            val canonicalPath = file.canonicalPath

            // Verify path is within app's allowed directories
            val appDataDir = context.applicationContext.dataDir.canonicalPath
            val externalStorageDir = android.os.Environment.getExternalStorageDirectory().canonicalPath

            val isWithinAppData = canonicalPath.startsWith(appDataDir)
            val isWithinExternalStorage = canonicalPath.startsWith(externalStorageDir)

            if (!isWithinAppData && !isWithinExternalStorage) {
                AppLogger.w(TAG, "Path outside allowed directories: $canonicalPath")
                return false
            }

            // CRITICAL FIX: Ensure parent directory exists to prevent NPE security bypass
            // Additional check: ensure canonical path doesn't escape original path
            val parentPath = file.parent
            if (parentPath == null) {
                AppLogger.w(TAG, "File has no parent directory (root-level file)")
                return false
            }
            if (!canonicalPath.startsWith(parentPath)) {
                AppLogger.w(TAG, "Canonical path escapes parent directory")
                return false
            }

            return true

        } catch (e: Exception) {
            AppLogger.e(TAG, "Path validation error", e)
            return false // Reject on error (fail-safe)
        }
    }

    /**
     * Validates multiple paths at once.
     *
     * @param paths List of paths to validate
     * @param context Android context
     * @return true if ALL paths are safe, false if ANY path is dangerous
     */
    fun arePathsSafe(paths: List<String>, context: Context): Boolean {
        return paths.all { isPathSafe(it, context) }
    }
}
