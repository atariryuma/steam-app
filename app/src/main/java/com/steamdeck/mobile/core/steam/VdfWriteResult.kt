package com.steamdeck.mobile.core.steam

/**
 * VDF File Write Result
 *
 * Type-safe result wrapper for VDF file write operations.
 *
 * Replaces ambiguous `Result<Boolean>` with explicit states:
 * - Created: VDF file was created/updated successfully
 * - Skipped: VDF file already exists and is fresh (within cache timeout)
 * - Error: VDF file creation failed
 *
 * Usage example:
 * ```kotlin
 * when (val result = steamAuthManager.createLoginUsersVdfIfNeeded(containerDir)) {
 *     is VdfWriteResult.Created -> AppLogger.i(TAG, "VDF created")
 *     is VdfWriteResult.Skipped -> AppLogger.d(TAG, "VDF skipped (cached)")
 *     is VdfWriteResult.Error -> AppLogger.e(TAG, "VDF error: ${result.message}")
 * }
 * ```
 *
 * Best Practice: Use sealed classes for type-safe state management
 */
sealed class VdfWriteResult {
    /**
     * VDF file was created or updated successfully
     */
    data object Created : VdfWriteResult()

    /**
     * VDF file already exists and is fresh (within cache timeout)
     *
     * @param ageSeconds Age of existing file in seconds
     */
    data class Skipped(val ageSeconds: Long) : VdfWriteResult()

    /**
     * VDF file creation failed
     *
     * @param message User-friendly error message
     * @param exception Original exception (for debugging)
     */
    data class Error(
        val message: String,
        val exception: Exception
    ) : VdfWriteResult()
}
