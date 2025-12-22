package com.steamdeck.mobile.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.steamdeck.mobile.data.local.database.entity.SteamInstallEntity
import kotlinx.coroutines.flow.Flow

/**
 * Steam Client installationinformation DAO
 *
 * Best Practice: Flow for reactive queries
 * Reference: https://developer.android.com/training/data-storage/room/async-queries
 */
@Dao
interface SteamInstallDao {

 /**
  * Insert Steam installation information
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insert(installation: SteamInstallEntity): Long

 /**
  * Update Steam installation information
  */
 @Update
 suspend fun update(installation: SteamInstallEntity)

 /**
  * Get latest Steam installation information (usually only 1 record)
  */
 @Query("SELECT * FROM steam_installations ORDER BY installed_at DESC LIMIT 1")
 suspend fun getInstallation(): SteamInstallEntity?

 /**
  * Monitor Steam installation information via Flow
  */
 @Query("SELECT * FROM steam_installations ORDER BY installed_at DESC LIMIT 1")
 fun observeInstallation(): Flow<SteamInstallEntity?>

 /**
  * Get Steam installation information by container ID
  */
 @Query("SELECT * FROM steam_installations WHERE container_id = :containerId LIMIT 1")
 suspend fun getInstallationByContainerId(containerId: String): SteamInstallEntity?

 /**
  * Delete all Steam installation information
  */
 @Query("DELETE FROM steam_installations")
 suspend fun deleteAll()
}
