package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.GameEntity
import com.steamdeck.mobile.data.local.database.entity.GameSource
import kotlinx.coroutines.flow.Flow

/**
 * ゲーム情報へのデータアクセスオブジェクト
 */
@Dao
interface GameDao {
    /**
     * すべてのゲームを取得（最終プレイ日時降順）
     */
    @Query("SELECT * FROM games ORDER BY lastPlayedTimestamp DESC")
    fun getAllGames(): Flow<List<GameEntity>>

    /**
     * お気に入りゲームを取得
     */
    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteGames(): Flow<List<GameEntity>>

    /**
     * ゲームIDでゲームを取得
     */
    @Query("SELECT * FROM games WHERE id = :gameId")
    suspend fun getGameById(gameId: Long): GameEntity?

    /**
     * Steam App IDでゲームを取得
     */
    @Query("SELECT * FROM games WHERE steamAppId = :steamAppId")
    suspend fun getGameBySteamAppId(steamAppId: Long): GameEntity?

    /**
     * ゲーム名で検索
     */
    @Query("SELECT * FROM games WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchGames(query: String): Flow<List<GameEntity>>

    /**
     * ソース別にゲームを取得
     */
    @Query("SELECT * FROM games WHERE source = :source ORDER BY addedTimestamp DESC")
    fun getGamesBySource(source: GameSource): Flow<List<GameEntity>>

    /**
     * ゲームを挿入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity): Long

    /**
     * 複数のゲームを挿入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<GameEntity>)

    /**
     * ゲームを更新
     */
    @Update
    suspend fun updateGame(game: GameEntity)

    /**
     * ゲームを削除
     */
    @Delete
    suspend fun deleteGame(game: GameEntity)

    /**
     * プレイ時間を更新
     */
    @Query("UPDATE games SET playTimeMinutes = playTimeMinutes + :additionalMinutes, lastPlayedTimestamp = :timestamp WHERE id = :gameId")
    suspend fun updatePlayTime(gameId: Long, additionalMinutes: Long, timestamp: Long)

    /**
     * お気に入り状態を切り替え
     */
    @Query("UPDATE games SET isFavorite = :isFavorite WHERE id = :gameId")
    suspend fun updateFavoriteStatus(gameId: Long, isFavorite: Boolean)

    /**
     * すべてのゲームを削除
     */
    @Query("DELETE FROM games")
    suspend fun deleteAllGames()
}
