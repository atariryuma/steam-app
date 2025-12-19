package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import kotlinx.coroutines.flow.Flow

/**
 * gameinformationmanagementリポジトリ interface
 */
interface GameRepository {
 /**
  * all gameretrieve
  */
 fun getAllGames(): Flow<List<Game>>

 /**
  * favoritegameretrieve
  */
 fun getFavoriteGames(): Flow<List<Game>>

 /**
  * gameID gameretrieve
  */
 suspend fun getGameById(gameId: Long): Game?

 /**
  * Steam App ID gameretrieve
  */
 suspend fun getGameBySteamAppId(steamAppId: Long): Game?

 /**
  * game名 検索
  */
 fun searchGames(query: String): Flow<List<Game>>

 /**
  * ソース別 gameretrieve
  */
 fun getGamesBySource(source: GameSource): Flow<List<Game>>

 /**
  * gameadd
  */
 suspend fun insertGame(game: Game): Long

 /**
  * 複数 gameadd
  */
 suspend fun insertGames(games: List<Game>)

 /**
  * gameupdate
  */
 suspend fun updateGame(game: Game)

 /**
  * gamedelete
  */
 suspend fun deleteGame(game: Game)

 /**
  * play timeupdate
  */
 suspend fun updatePlayTime(gameId: Long, additionalMinutes: Long, timestamp: Long)

 /**
  * favoritestate切り替え
  */
 suspend fun updateFavoriteStatus(gameId: Long, isFavorite: Boolean)

 /**
  * all gamedelete
  */
 suspend fun deleteAllGames()
}
