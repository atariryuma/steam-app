package com.steamdeck.mobile.data.remote.steam

import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.data.remote.steam.model.SteamGame
import com.steamdeck.mobile.data.remote.steam.model.SteamPlayer

/**
 * Steam data operations repository interface
 *
 * MIGRATION (2025-12-22): Result<T> → DataResult<T>
 * - Unified error handling with AppError
 * - HTTP status code mapping via AppError.fromHttpCode()
 * - Retryability determination via AppError.isRetryable()
 */
interface SteamRepository {
 /**
  * Retrieve list of games owned by user
  *
  * @param apiKey Steam Web API Key
  * @param steamId Steam ID
  * @return Game list or AppError
  */
 suspend fun getOwnedGames(apiKey: String, steamId: String): DataResult<List<SteamGame>>

 /**
  * Retrieve user profile information
  *
  * @param apiKey Steam Web API Key
  * @param steamId Steam ID
  * @return Player information or AppError
  */
 suspend fun getPlayerSummary(apiKey: String, steamId: String): DataResult<SteamPlayer>

 /**
  * Download game image
  *
  * @param url Image URL
  * @param destinationPath Destination path
  * @return Success or AppError
  */
 suspend fun downloadGameImage(url: String, destinationPath: String): DataResult<Unit>
}
