package com.steamdeck.mobile.core.steam

/**
 * SteamID64 Validator
 *
 * Validates Steam ID 64-bit format according to Valve's official specification.
 *
 * SteamID64 Format:
 * - 17-digit decimal number
 * - Valid range: 76561197960265728 ~ 76561202255233023
 * - Formula: SteamID64 = 76561197960265728 + SteamID32
 *
 * Example valid SteamID64: 76561198245791652
 *
 * Reference:
 * - https://developer.valvesoftware.com/wiki/SteamID
 * - https://steamid.io/
 */
object SteamIdValidator {
    /**
     * Minimum valid SteamID64 (corresponds to SteamID32 = 0)
     */
    private const val MIN_STEAM_ID64 = 76561197960265728L

    /**
     * Maximum valid SteamID64 (corresponds to SteamID32 = 4294967295)
     *
     * Calculation: 76561197960265728 + 2^32 - 1 = 76561202255233023
     */
    private const val MAX_STEAM_ID64 = 76561202255233023L

    /**
     * Validate SteamID64 format
     *
     * Checks:
     * 1. String length is exactly 17 digits
     * 2. Contains only numeric characters
     * 3. Value falls within valid SteamID64 range
     *
     * @param steamId Steam ID string to validate
     * @return true if valid SteamID64, false otherwise
     */
    fun isValidSteamId64(steamId: String?): Boolean {
        if (steamId.isNullOrBlank()) return false

        // Check length (must be 17 digits)
        if (steamId.length != 17) return false

        // Parse to Long (returns null if not numeric)
        val id = steamId.toLongOrNull() ?: return false

        // Check range
        return id in MIN_STEAM_ID64..MAX_STEAM_ID64
    }

    /**
     * Validate and return detailed error message
     *
     * @param steamId Steam ID string to validate
     * @return null if valid, error message if invalid
     */
    fun validateWithMessage(steamId: String?): String? {
        if (steamId.isNullOrBlank()) {
            return "Steam ID is empty"
        }

        if (steamId.length != 17) {
            return "Steam ID must be 17 digits (got ${steamId.length})"
        }

        val id = steamId.toLongOrNull()
        if (id == null) {
            return "Steam ID must contain only numeric characters"
        }

        if (id < MIN_STEAM_ID64) {
            return "Steam ID is too small (minimum: $MIN_STEAM_ID64)"
        }

        if (id > MAX_STEAM_ID64) {
            return "Steam ID is too large (maximum: $MAX_STEAM_ID64)"
        }

        return null // Valid
    }
}
