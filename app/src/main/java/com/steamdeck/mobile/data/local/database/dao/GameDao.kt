package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.GameEntity
import com.steamdeck.mobile.data.local.database.entity.GameSource
import kotlinx.coroutines.flow.Flow

/**
 * Game information data access object
 */
@Dao
interface GameDao {
 /**
  * Get all games (sorted by last played date/time descending)
  */
 @Query("SELECT * FROM games ORDER BY lastPlayedTimestamp DESC")
 fun getAllGames(): Flow<List<GameEntity>>

 /**
  * Get favorite games
  */
 @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY name ASC")
 fun getFavoriteGames(): Flow<List<GameEntity>>

 /**
  * Get game by game ID
  */
 @Query("SELECT * FROM games WHERE id = :gameId")
 suspend fun getGameById(gameId: Long): GameEntity?

 /**
  * Get game by Steam App ID
  */
 @Query("SELECT * FROM games WHERE steamAppId = :steamAppId")
 suspend fun getGameBySteamAppId(steamAppId: Long): GameEntity?

 /**
  * Search games by name
  */
 @Query("SELECT * FROM games WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
 fun searchGames(query: String): Flow<List<GameEntity>>

 /**
  * Get games by source
  */
 @Query("SELECT * FROM games WHERE source = :source ORDER BY addedTimestamp DESC")
 fun getGamesBySource(source: GameSource): Flow<List<GameEntity>>

 /**
  * Insert game
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertGame(game: GameEntity): Long

 /**
  * Insert multiple games
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertGames(games: List<GameEntity>)

 /**
  * Update game
  */
 @Update
 suspend fun updateGame(game: GameEntity)

 /**
  * Delete game
  */
 @Delete
 suspend fun deleteGame(game: GameEntity)

 /**
  * Update play time
  */
 @Query("UPDATE games SET playTimeMinutes = playTimeMinutes + :additionalMinutes, lastPlayedTimestamp = :timestamp WHERE id = :gameId")
 suspend fun updatePlayTime(gameId: Long, additionalMinutes: Long, timestamp: Long)

 /**
  * Update favorite status
  */
 @Query("UPDATE games SET isFavorite = :isFavorite WHERE id = :gameId")
 suspend fun updateFavoriteStatus(gameId: Long, isFavorite: Boolean)

 /**
  * Delete all games
  */
 @Query("DELETE FROM games")
 suspend fun deleteAllGames()

 /**
  * Update game installation status
  */
 @Query("""
  UPDATE games
  SET installationStatus = :status,
      installProgress = :progress,
      statusUpdatedTimestamp = :timestamp
  WHERE id = :gameId
 """)
 suspend fun updateInstallationStatus(
  gameId: Long,
  status: String,
  progress: Int,
  timestamp: Long
 )

 /**
  * Observe game changes (for real-time installation status updates)
  */
 @Query("SELECT * FROM games WHERE id = :gameId")
 fun observeGame(gameId: Long): Flow<GameEntity?>

 /**
  * Get games by installation status
  */
 @Query("SELECT * FROM games WHERE installationStatus = :status ORDER BY addedTimestamp DESC")
 fun getGamesByInstallationStatus(status: String): Flow<List<GameEntity>>

 /**
  * Update game executable path
  */
 @Query("UPDATE games SET executablePath = :executablePath WHERE id = :gameId")
 suspend fun updateGameExecutablePath(gameId: Long, executablePath: String)

 /**
  * Update game container ID
  *
  * FIXED (2025-12-25): Container ID stored as String type (matches Winlator implementation)
  * No type conversion needed - direct String storage
  */
 @Query("UPDATE games SET winlatorContainerId = :containerId WHERE id = :gameId")
 suspend fun updateGameContainer(gameId: Long, containerId: String)
}
