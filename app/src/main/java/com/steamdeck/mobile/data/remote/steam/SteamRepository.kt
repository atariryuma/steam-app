package com.steamdeck.mobile.data.remote.steam

import com.steamdeck.mobile.data.remote.steam.model.SteamGame
import com.steamdeck.mobile.data.remote.steam.model.SteamPlayer

/**
 * Steam関連データ操作のリポジトリインターフェース
 */
interface SteamRepository {
    /**
     * ユーザーが所有するゲーム一覧を取得
     *
     * @param apiKey Steam Web API Key
     * @param steamId Steam ID
     * @return ゲーム一覧
     */
    suspend fun getOwnedGames(apiKey: String, steamId: String): Result<List<SteamGame>>

    /**
     * ユーザーのプロフィール情報を取得
     *
     * @param apiKey Steam Web API Key
     * @param steamId Steam ID
     * @return プレイヤー情報
     */
    suspend fun getPlayerSummary(apiKey: String, steamId: String): Result<SteamPlayer>

    /**
     * ゲーム画像をダウンロード
     *
     * @param url 画像URL
     * @param destinationPath 保存先パス
     * @return 保存成功可否
     */
    suspend fun downloadGameImage(url: String, destinationPath: String): Result<Unit>
}
