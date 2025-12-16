package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import kotlinx.coroutines.flow.Flow

/**
 * ゲーム情報管理リポジトリのインターフェース
 */
interface GameRepository {
    /**
     * すべてのゲームを取得
     */
    fun getAllGames(): Flow<List<Game>>

    /**
     * お気に入りゲームを取得
     */
    fun getFavoriteGames(): Flow<List<Game>>

    /**
     * ゲームIDでゲームを取得
     */
    suspend fun getGameById(gameId: Long): Game?

    /**
     * Steam App IDでゲームを取得
     */
    suspend fun getGameBySteamAppId(steamAppId: Long): Game?

    /**
     * ゲーム名で検索
     */
    fun searchGames(query: String): Flow<List<Game>>

    /**
     * ソース別にゲームを取得
     */
    fun getGamesBySource(source: GameSource): Flow<List<Game>>

    /**
     * ゲームを追加
     */
    suspend fun insertGame(game: Game): Long

    /**
     * 複数のゲームを追加
     */
    suspend fun insertGames(games: List<Game>)

    /**
     * ゲームを更新
     */
    suspend fun updateGame(game: Game)

    /**
     * ゲームを削除
     */
    suspend fun deleteGame(game: Game)

    /**
     * プレイ時間を更新
     */
    suspend fun updatePlayTime(gameId: Long, additionalMinutes: Long, timestamp: Long)

    /**
     * お気に入り状態を切り替え
     */
    suspend fun updateFavoriteStatus(gameId: Long, isFavorite: Boolean)

    /**
     * すべてのゲームを削除
     */
    suspend fun deleteAllGames()
}
