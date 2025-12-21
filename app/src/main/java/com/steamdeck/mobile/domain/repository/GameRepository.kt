package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.model.InstallationStatus
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

 /**
  * Update game installation status
  *
  * @param gameId Game ID
  * @param status Installation status (NOT_INSTALLED, DOWNLOADING, INSTALLING, INSTALLED, etc.)
  * @param progress Installation progress (0-100)
  */
 suspend fun updateInstallationStatus(
  gameId: Long,
  status: InstallationStatus,
  progress: Int = 0
 )

 /**
  * Observe game installation status changes
  *
  * @param gameId Game ID
  * @return Flow of game updates
  */
 fun observeGame(gameId: Long): Flow<Game?>

 /**
  * Get games by installation status
  *
  * @param status Installation status filter
  * @return Flow of games matching the status
  */
 fun getGamesByInstallationStatus(status: InstallationStatus): Flow<List<Game>>
}
