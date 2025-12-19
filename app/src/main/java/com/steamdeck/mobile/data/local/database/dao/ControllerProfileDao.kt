package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.ControllerProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for controller profile operations.
 */
@Dao
interface ControllerProfileDao {

 /**
  * Get all controller profiles.
  *
  * @return Flow of all profiles
  */
 @Query("SELECT * FROM controller_profiles ORDER BY lastUsedAt DESC")
 fun getAllProfiles(): Flow<List<ControllerProfileEntity>>

 /**
  * Get profile by ID.
  *
  * @param id Profile ID
  * @return Profile or null if not found
  */
 @Query("SELECT * FROM controller_profiles WHERE id = :id")
 suspend fun getProfileById(id: Long): ControllerProfileEntity?

 /**
  * Get profiles for a specific controller.
  *
  * @param controllerId Controller unique ID
  * @return Flow of profiles for this controller
  */
 @Query("SELECT * FROM controller_profiles WHERE controllerId = :controllerId ORDER BY lastUsedAt DESC")
 fun getProfilesForController(controllerId: String): Flow<List<ControllerProfileEntity>>

 /**
  * Get the most recently used profile for a controller.
  *
  * @param controllerId Controller unique ID
  * @return Most recent profile or null
  */
 @Query("SELECT * FROM controller_profiles WHERE controllerId = :controllerId ORDER BY lastUsedAt DESC LIMIT 1")
 suspend fun getLastUsedProfile(controllerId: String): ControllerProfileEntity?

 /**
  * Insert a new profile.
  *
  * @param profile Profile to insert
  * @return Inserted profile ID
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertProfile(profile: ControllerProfileEntity): Long

 /**
  * Update an existing profile.
  *
  * @param profile Profile to update
  */
 @Update
 suspend fun updateProfile(profile: ControllerProfileEntity)

 /**
  * Delete a profile.
  *
  * @param profile Profile to delete
  */
 @Delete
 suspend fun deleteProfile(profile: ControllerProfileEntity)

 /**
  * Delete all profiles for a controller.
  *
  * @param controllerId Controller unique ID
  */
 @Query("DELETE FROM controller_profiles WHERE controllerId = :controllerId")
 suspend fun deleteProfilesForController(controllerId: String)

 /**
  * Update last used timestamp for a profile.
  *
  * @param id Profile ID
  * @param timestamp New timestamp
  */
 @Query("UPDATE controller_profiles SET lastUsedAt = :timestamp WHERE id = :id")
 suspend fun updateLastUsedAt(id: Long, timestamp: Long = System.currentTimeMillis())
}
