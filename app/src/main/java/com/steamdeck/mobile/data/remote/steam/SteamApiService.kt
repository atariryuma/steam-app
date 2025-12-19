package com.steamdeck.mobile.data.remote.steam

import com.steamdeck.mobile.data.remote.steam.model.GetOwnedGamesResponse
import com.steamdeck.mobile.data.remote.steam.model.GetPlayerSummariesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Steam Web API service
 *
 * API Keyretrieve: https://steamcommunity.com/dev/apikey
 * API ドキュメント: https://developer.valvesoftware.com/wiki/Steam_Web_API
 */
interface SteamApiService {
 /**
  * ユーザー 所有dogamelistretrieve
  *
  * @param key Steam Web API Key
  * @param steamId ユーザー Steam ID
  * @param includeAppInfo アプリinformation含めるか（名前、アイコンURLetc）
  * @param includePlayedFreeGames 無料game含めるか
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
  * ユーザー プロフィールinformationretrieve
  *
  * @param key Steam Web API Key
  * @param steamIds Steam ID カンマ区切りリスト
  */
 @GET("ISteamUser/GetPlayerSummaries/v0002/")
 suspend fun getPlayerSummaries(
  @Query("key") key: String,
  @Query("steamids") steamIds: String,
  @Query("format") format: String = "json"
 ): Response<GetPlayerSummariesResponse>

 /**
  * アプリdetailsinformationretrieve (Store API)
  *
  * Note: これ Steam Web API なく、Steam Store APIuse
  * URL: https://store.steampowered.com/api/appdetails
  *
  * @param appIds App ID カンマ区切りリスト
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
