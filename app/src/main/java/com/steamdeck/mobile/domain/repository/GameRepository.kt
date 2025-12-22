package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.model.InstallationStatus
import kotlinx.coroutines.flow.Flow

/**
 * Game information management repository interface
 */
interface GameRepository {
 /**
  * Retrieve all games
  */
 fun getAllGames(): Flow<List<Game>>

 /**
  * Retrieve favorite games
  */
 fun getFavoriteGames(): Flow<List<Game>>

 /**
  * Retrieve game by ID
  */
 suspend fun getGameById(gameId: Long): Game?

 /**
  * Retrieve game by Steam App ID
  */
 suspend fun getGameBySteamAppId(steamAppId: Long): Game?

 /**
  * Search games by name
  */
 fun searchGames(query: String): Flow<List<Game>>

 /**
  * Retrieve games by source
  */
 fun getGamesBySource(source: GameSource): Flow<List<Game>>

 /**
  * Add game
  */
 suspend fun insertGame(game: Game): Long

 /**
  * Add multiple games
  */
 suspend fun insertGames(games: List<Game>)

 /**
  * Update game
  */
 suspend fun updateGame(game: Game)

 /**
  * Delete game
  */
 suspend fun deleteGame(game: Game)

 /**
  * Update play time
  */
 suspend fun updatePlayTime(gameId: Long, additionalMinutes: Long, timestamp: Long)

 /**
  * Toggle favorite status
  */
 suspend fun updateFavoriteStatus(gameId: Long, isFavorite: Boolean)

 /**
  * Delete all games
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

 /**
  * Update game executable path
  *
  * @param gameId Game ID
  * @param executablePath New executable file path
  */
 suspend fun updateGameExecutablePath(gameId: Long, executablePath: String)
}
