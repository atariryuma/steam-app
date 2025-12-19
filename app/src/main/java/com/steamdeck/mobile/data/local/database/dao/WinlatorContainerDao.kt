package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Winlatorcontainerconfigurationto dataアクセスobject
 */
@Dao
interface WinlatorContainerDao {
 /**
  * all containerretrieve
  */
 @Query("SELECT * FROM winlator_containers ORDER BY createdTimestamp DESC")
 fun getAllContainers(): Flow<List<WinlatorContainerEntity>>

 /**
  * containerID containerretrieve
  */
 @Query("SELECT * FROM winlator_containers WHERE id = :containerId")
 suspend fun getContainerById(containerId: Long): WinlatorContainerEntity?

 /**
  * container名 container検索
  */
 @Query("SELECT * FROM winlator_containers WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
 fun searchContainers(query: String): Flow<List<WinlatorContainerEntity>>

 /**
  * container挿入
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertContainer(container: WinlatorContainerEntity): Long

 /**
  * containerupdate
  */
 @Update
 suspend fun updateContainer(container: WinlatorContainerEntity)

 /**
  * containerdelete
  */
 @Delete
 suspend fun deleteContainer(container: WinlatorContainerEntity)

 /**
  * all containerdelete
  */
 @Query("DELETE FROM winlator_containers")
 suspend fun deleteAllContainers()
}
