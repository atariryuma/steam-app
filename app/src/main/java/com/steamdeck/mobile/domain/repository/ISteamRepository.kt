package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Game

/**
 * Steam API operations repository interface.
 * Abstracts Steam Web API and Steam CMD interactions from the domain layer.
 */
interface ISteamRepository {
    /**
     * Fetches the user's Steam library.
     *
     * @param steamId The Steam ID (SteamID64 format)
     * @param apiKey The Steam Web API key
     * @return Result containing list of games on success, or exception on failure
     */
    suspend fun getUserLibrary(steamId: String, apiKey: String): Result<List<Game>>

    /**
     * Fetches detailed information about a specific game.
     *
     * @param appId The Steam application ID
     * @return Result containing game details on success, or exception on failure
     */
    suspend fun getGameDetails(appId: String): Result<Game?>

    /**
     * Validates a Steam API key.
     *
     * @param apiKey The API key to validate
     * @return Result containing true if valid, false otherwise
     */
    suspend fun validateApiKey(apiKey: String): Result<Boolean>
}
