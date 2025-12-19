package com.steamdeck.mobile.data.remote.steam

import com.steamdeck.mobile.data.remote.steam.model.SteamGame
import com.steamdeck.mobile.data.remote.steam.model.SteamPlayer

/**
 * Steam関連data操作 リポジトリinterface
 */
interface SteamRepository {
 /**
  * ユーザー 所有dogamelistretrieve
  *
  * @param apiKey Steam Web API Key
  * @param steamId Steam ID
  * @return gamelist
  */
 suspend fun getOwnedGames(apiKey: String, steamId: String): Result<List<SteamGame>>

 /**
  * ユーザー プロフィールinformationretrieve
  *
  * @param apiKey Steam Web API Key
  * @param steamId Steam ID
  * @return プレイヤーinformation
  */
 suspend fun getPlayerSummary(apiKey: String, steamId: String): Result<SteamPlayer>

 /**
  * game画像download
  *
  * @param url 画像URL
  * @param destinationPath Destination path
  * @return savesuccess可否
  */
 suspend fun downloadGameImage(url: String, destinationPath: String): Result<Unit>
}
