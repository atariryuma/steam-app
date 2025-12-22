package com.steamdeck.mobile.data.remote.steam

import com.steamdeck.mobile.data.remote.steam.model.GetOwnedGamesResponse
import com.steamdeck.mobile.data.remote.steam.model.GetPlayerSummariesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Steam Web API service
 *
 * API Key retrieve: https://steamcommunity.com/dev/apikey
 * API documentation: https://developer.valvesoftware.com/wiki/Steam_Web_API
 */
interface SteamApiService {
 /**
  * Retrieve list of games owned by user
  *
  * @param key Steam Web API Key
  * @param steamId User's Steam ID
  * @param includeAppInfo Whether to include app information (name, icon URL, etc.)
  * @param includePlayedFreeGames Whether to include free games
  */
 @GET("IPlayerService/GetOwnedGames/v0001/")
 suspend fun getOwnedGames(
  @Query("key") key: String,
  @Query("steamid") steamId: String,
  @Query("include_appinfo") includeAppInfo: Int = 1,
  @Query("include_played_free_games") includePlayedFreeGames: Int = 1,
  @Query("format") format: String = "json"
 ): Response<GetOwnedGamesResponse>

 /**
  * Retrieve user profile information
  *
  * @param key Steam Web API Key
  * @param steamIds Comma-separated list of Steam IDs
  */
 @GET("ISteamUser/GetPlayerSummaries/v0002/")
 suspend fun getPlayerSummaries(
  @Query("key") key: String,
  @Query("steamids") steamIds: String,
  @Query("format") format: String = "json"
 ): Response<GetPlayerSummariesResponse>

 /**
  * Retrieve app details information (Store API)
  *
  * Note: This uses Steam Store API, not Steam Web API
  * URL: https://store.steampowered.com/api/appdetails
  *
  * @param appIds Comma-separated list of App IDs
  */
 @GET("https://store.steampowered.com/api/appdetails")
 suspend fun getAppDetails(
  @Query("appids") appIds: String,
  @Query("filters") filters: String = "basic"
 ): Response<Map<String, Any>>

 companion object {
  const val BASE_URL = "https://api.steampowered.com/"
 }
}
