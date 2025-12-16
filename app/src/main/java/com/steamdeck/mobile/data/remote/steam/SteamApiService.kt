package com.steamdeck.mobile.data.remote.steam

import com.steamdeck.mobile.data.remote.steam.model.GetOwnedGamesResponse
import com.steamdeck.mobile.data.remote.steam.model.GetPlayerSummariesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Steam Web API サービス
 *
 * API Key取得: https://steamcommunity.com/dev/apikey
 * API ドキュメント: https://developer.valvesoftware.com/wiki/Steam_Web_API
 */
interface SteamApiService {
    /**
     * ユーザーが所有するゲーム一覧を取得
     *
     * @param key Steam Web API Key
     * @param steamId ユーザーのSteam ID
     * @param includeAppInfo アプリ情報を含めるか（名前、アイコンURL等）
     * @param includePlayedFreeGames 無料ゲームを含めるか
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
     * ユーザーのプロフィール情報を取得
     *
     * @param key Steam Web API Key
     * @param steamIds Steam IDのカンマ区切りリスト
     */
    @GET("ISteamUser/GetPlayerSummaries/v0002/")
    suspend fun getPlayerSummaries(
        @Query("key") key: String,
        @Query("steamids") steamIds: String,
        @Query("format") format: String = "json"
    ): Response<GetPlayerSummariesResponse>

    companion object {
        const val BASE_URL = "https://api.steampowered.com/"
    }
}
