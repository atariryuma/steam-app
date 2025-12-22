package com.steamdeck.mobile.data.remote.steam

import com.steamdeck.mobile.data.remote.steam.model.SteamGame
import com.steamdeck.mobile.data.remote.steam.model.SteamPlayer

/**
 * Steam data operations repository interface
 */
interface SteamRepository {
 /**
  * Retrieve list of games owned by user
  *
  * @param apiKey Steam Web API Key
  * @param steamId Steam ID
  * @return Game list
  */
 suspend fun getOwnedGames(apiKey: String, steamId: String): Result<List<SteamGame>>

 /**
  * Retrieve user profile information
  *
  * @param apiKey Steam Web API Key
  * @param steamId Steam ID
  * @return Player information
  */
 suspend fun getPlayerSummary(apiKey: String, steamId: String): Result<SteamPlayer>

 /**
  * Download game image
  *
  * @param url Image URL
  * @param destinationPath Destination path
  * @return Success or failure
  */
 suspend fun downloadGameImage(url: String, destinationPath: String): Result<Unit>
}
