package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Winlator container configuration data access object
 */
@Dao
interface WinlatorContainerDao {
 /**
  * Get all containers
  */
 @Query("SELECT * FROM winlator_containers ORDER BY createdTimestamp DESC")
 fun getAllContainers(): Flow<List<WinlatorContainerEntity>>

 /**
  * Get container by container ID
  */
 @Query("SELECT * FROM winlator_containers WHERE id = :containerId")
 suspend fun getContainerById(containerId: Long): WinlatorContainerEntity?

 /**
  * Search containers by container name
  */
 @Query("SELECT * FROM winlator_containers WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
 fun searchContainers(query: String): Flow<List<WinlatorContainerEntity>>

 /**
  * Insert container
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertContainer(container: WinlatorContainerEntity): Long

 /**
  * Update container
  */
 @Update
 suspend fun updateContainer(container: WinlatorContainerEntity)

 /**
  * Delete container
  */
 @Delete
 suspend fun deleteContainer(container: WinlatorContainerEntity)

 /**
  * Delete all containers
  */
 @Query("DELETE FROM winlator_containers")
 suspend fun deleteAllContainers()
}
