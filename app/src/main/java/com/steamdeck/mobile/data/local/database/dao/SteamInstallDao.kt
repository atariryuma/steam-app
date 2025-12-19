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
  * Steam installationinformation挿入
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insert(installation: SteamInstallEntity): Long

 /**
  * Steam installationinformationupdate
  */
 @Update
 suspend fun update(installation: SteamInstallEntity)

 /**
  * 最新 Steam installationinformationretrieve (通常 1件 み)
  */
 @Query("SELECT * FROM steam_installations ORDER BY installed_at DESC LIMIT 1")
 suspend fun getInstallation(): SteamInstallEntity?

 /**
  * Steam installationinformation Flow monitor
  */
 @Query("SELECT * FROM steam_installations ORDER BY installed_at DESC LIMIT 1")
 fun observeInstallation(): Flow<SteamInstallEntity?>

 /**
  * container ID Steam installationinformationretrieve
  */
 @Query("SELECT * FROM steam_installations WHERE container_id = :containerId LIMIT 1")
 suspend fun getInstallationByContainerId(containerId: String): SteamInstallEntity?

 /**
  * 全て Steam installationinformationdelete
  */
 @Query("DELETE FROM steam_installations")
 suspend fun deleteAll()
}
