package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Controller
import com.steamdeck.mobile.domain.model.ControllerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for controller management.
 */
interface ControllerRepository {

 /**
  * Get all connected controllers.
  *
  * @return Flow of currently connected controllers
  */
 fun getConnectedControllers(): Flow<List<Controller>>

 /**
  * Get all controller profiles.
  *
  * @return Flow of all saved profiles
  */
 fun getAllProfiles(): Flow<List<ControllerProfile>>

 /**
  * Get profiles for a specific controller.
  *
  * @param controllerId Controller unique ID
  * @return Flow of profiles for this controller
  */
 fun getProfilesForController(controllerId: String): Flow<List<ControllerProfile>>

 /**
  * Get the last used profile for a controller.
  *
  * @param controllerId Controller unique ID
  * @return Last used profile or null
  */
 suspend fun getLastUsedProfile(controllerId: String): Result<ControllerProfile?>

 /**
  * Save a controller profile.
  *
  * @param profile Profile to save
  * @return Result with saved profile ID
  */
 suspend fun saveProfile(profile: ControllerProfile): Result<Long>

 /**
  * Update a controller profile.
  *
  * @param profile Profile to update
  * @return Result indicating success/failure
  */
 suspend fun updateProfile(profile: ControllerProfile): Result<Unit>

 /**
  * Delete a controller profile.
  *
  * @param profile Profile to delete
  * @return Result indicating success/failure
  */
 suspend fun deleteProfile(profile: ControllerProfile): Result<Unit>

 /**
  * Update last used timestamp for a profile.
  *
  * @param profileId Profile ID
  * @return Result indicating success/failure
  */
 suspend fun markProfileUsed(profileId: Long): Result<Unit>
}
