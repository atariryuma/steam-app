package com.steamdeck.mobile.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.steamdeck.mobile.data.local.database.entity.SteamInstallEntity
import kotlinx.coroutines.flow.Flow

/**
 * Steam Client インストール情報 DAO
 *
 * Best Practice: Flow for reactive queries
 * Reference: https://developer.android.com/training/data-storage/room/async-queries
 */
@Dao
interface SteamInstallDao {

    /**
     * Steam インストール情報を挿入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(installation: SteamInstallEntity): Long

    /**
     * Steam インストール情報を更新
     */
    @Update
    suspend fun update(installation: SteamInstallEntity)

    /**
     * 最新の Steam インストール情報を取得 (通常は1件のみ)
     */
    @Query("SELECT * FROM steam_installations ORDER BY installed_at DESC LIMIT 1")
    suspend fun getInstallation(): SteamInstallEntity?

    /**
     * Steam インストール情報を Flow で監視
     */
    @Query("SELECT * FROM steam_installations ORDER BY installed_at DESC LIMIT 1")
    fun observeInstallation(): Flow<SteamInstallEntity?>

    /**
     * コンテナ ID で Steam インストール情報を取得
     */
    @Query("SELECT * FROM steam_installations WHERE container_id = :containerId LIMIT 1")
    suspend fun getInstallationByContainerId(containerId: String): SteamInstallEntity?

    /**
     * 全ての Steam インストール情報を削除
     */
    @Query("DELETE FROM steam_installations")
    suspend fun deleteAll()
}
