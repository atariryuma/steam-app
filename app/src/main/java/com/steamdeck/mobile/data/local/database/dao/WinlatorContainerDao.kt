package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Winlatorコンテナ設定へのデータアクセスオブジェクト
 */
@Dao
interface WinlatorContainerDao {
    /**
     * すべてのコンテナを取得
     */
    @Query("SELECT * FROM winlator_containers ORDER BY createdTimestamp DESC")
    fun getAllContainers(): Flow<List<WinlatorContainerEntity>>

    /**
     * コンテナIDでコンテナを取得
     */
    @Query("SELECT * FROM winlator_containers WHERE id = :containerId")
    suspend fun getContainerById(containerId: Long): WinlatorContainerEntity?

    /**
     * コンテナ名でコンテナを検索
     */
    @Query("SELECT * FROM winlator_containers WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchContainers(query: String): Flow<List<WinlatorContainerEntity>>

    /**
     * コンテナを挿入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContainer(container: WinlatorContainerEntity): Long

    /**
     * コンテナを更新
     */
    @Update
    suspend fun updateContainer(container: WinlatorContainerEntity)

    /**
     * コンテナを削除
     */
    @Delete
    suspend fun deleteContainer(container: WinlatorContainerEntity)

    /**
     * すべてのコンテナを削除
     */
    @Query("DELETE FROM winlator_containers")
    suspend fun deleteAllContainers()
}
