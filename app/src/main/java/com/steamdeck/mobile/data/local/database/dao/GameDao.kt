package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.GameEntity
import com.steamdeck.mobile.data.local.database.entity.GameSource
import kotlinx.coroutines.flow.Flow

/**
 * gameinformationto dataアクセスobject
 */
@Dao
interface GameDao {
 /**
  * all gameretrieve（最終プレイdate and time降順）
  */
 @Query("SELECT * FROM games ORDER BY lastPlayedTimestamp DESC")
 fun getAllGames(): Flow<List<GameEntity>>

 /**
  * favoritegameretrieve
  */
 @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY name ASC")
 fun getFavoriteGames(): Flow<List<GameEntity>>

 /**
  * gameID gameretrieve
  */
 @Query("SELECT * FROM games WHERE id = :gameId")
 suspend fun getGameById(gameId: Long): GameEntity?

 /**
  * Steam App ID gameretrieve
  */
 @Query("SELECT * FROM games WHERE steamAppId = :steamAppId")
 suspend fun getGameBySteamAppId(steamAppId: Long): GameEntity?

 /**
  * game名 検索
  */
 @Query("SELECT * FROM games WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
 fun searchGames(query: String): Flow<List<GameEntity>>

 /**
  * ソース別 gameretrieve
  */
 @Query("SELECT * FROM games WHERE source = :source ORDER BY addedTimestamp DESC")
 fun getGamesBySource(source: GameSource): Flow<List<GameEntity>>

 /**
  * game挿入
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertGame(game: GameEntity): Long

 /**
  * 複数 game挿入
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertGames(games: List<GameEntity>)

 /**
  * gameupdate
  */
 @Update
 suspend fun updateGame(game: GameEntity)

 /**
  * gamedelete
  */
 @Delete
 suspend fun deleteGame(game: GameEntity)

 /**
  * play timeupdate
  */
 @Query("UPDATE games SET playTimeMinutes = playTimeMinutes + :additionalMinutes, lastPlayedTimestamp = :timestamp WHERE id = :gameId")
 suspend fun updatePlayTime(gameId: Long, additionalMinutes: Long, timestamp: Long)

 /**
  * favoritestate切り替え
  */
 @Query("UPDATE games SET isFavorite = :isFavorite WHERE id = :gameId")
 suspend fun updateFavoriteStatus(gameId: Long, isFavorite: Boolean)

 /**
  * all gamedelete
  */
 @Query("DELETE FROM games")
 suspend fun deleteAllGames()
}
