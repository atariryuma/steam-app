package com.steamdeck.mobile.data.repository

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import android.view.InputDevice
import com.steamdeck.mobile.data.local.database.dao.ControllerProfileDao
import com.steamdeck.mobile.data.mapper.toDomain
import com.steamdeck.mobile.data.mapper.toEntity
import com.steamdeck.mobile.domain.model.Controller
import com.steamdeck.mobile.domain.model.ControllerProfile
import com.steamdeck.mobile.domain.model.ControllerType
import com.steamdeck.mobile.domain.repository.ControllerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ControllerRepository.
 *
 * Manages controller detection via InputDevice API and profile storage.
 */
@Singleton
class ControllerRepositoryImpl @Inject constructor(
 @ApplicationContext private val context: Context,
 private val controllerProfileDao: ControllerProfileDao
) : ControllerRepository {

 companion object {
  private const val TAG = "ControllerRepository"
 }

 override fun getConnectedControllers(): Flow<List<Controller>> = flow {
  try {
   val controllers = detectControllers()
   emit(controllers)
   AppLogger.d(TAG, "Detected ${controllers.size} controllers")
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to detect controllers", e)
   emit(emptyList())
  }
 }

 override fun getAllProfiles(): Flow<List<ControllerProfile>> {
  return controllerProfileDao.getAllProfiles()
   .map { entities -> entities.map { it.toDomain() } }
 }

 override fun getProfilesForController(controllerId: String): Flow<List<ControllerProfile>> {
  return controllerProfileDao.getProfilesForController(controllerId)
   .map { entities -> entities.map { it.toDomain() } }
 }

 override suspend fun getLastUsedProfile(controllerId: String): Result<ControllerProfile?> {
  return try {
   val entity = controllerProfileDao.getLastUsedProfile(controllerId)
   Result.success(entity?.toDomain())
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to get last used profile", e)
   Result.failure(e)
  }
 }

 override suspend fun saveProfile(profile: ControllerProfile): Result<Long> {
  return try {
   val entity = profile.toEntity()
   val id = controllerProfileDao.insertProfile(entity)
   AppLogger.i(TAG, "Saved controller profile: ${profile.name} (ID: $id)")
   Result.success(id)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to save profile", e)
   Result.failure(e)
  }
 }

 override suspend fun updateProfile(profile: ControllerProfile): Result<Unit> {
  return try {
   val entity = profile.toEntity()
   controllerProfileDao.updateProfile(entity)
   AppLogger.i(TAG, "Updated controller profile: ${profile.name}")
   Result.success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to update profile", e)
   Result.failure(e)
  }
 }

 override suspend fun deleteProfile(profile: ControllerProfile): Result<Unit> {
  return try {
   val entity = profile.toEntity()
   controllerProfileDao.deleteProfile(entity)
   AppLogger.i(TAG, "Deleted controller profile: ${profile.name}")
   Result.success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to delete profile", e)
   Result.failure(e)
  }
 }

 override suspend fun markProfileUsed(profileId: Long): Result<Unit> {
  return try {
   controllerProfileDao.updateLastUsedAt(profileId)
   Result.success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to mark profile as used", e)
   Result.failure(e)
  }
 }

 /**
  * Detect connected game controllers using InputDevice API.
  *
  * @return List of detected controllers
  */
 private fun detectControllers(): List<Controller> {
  val controllers = mutableListOf<Controller>()

  try {
   val deviceIds = InputDevice.getDeviceIds()
   deviceIds.forEach { deviceId ->
    try {
     val device = InputDevice.getDevice(deviceId) ?: return@forEach

     // Check if device is a game controller
     val sources = device.sources
     val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
     val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

     if (isGamepad || isJoystick) {
      // Validate device has required properties
      if (device.name.isNullOrBlank()) {
       AppLogger.w(TAG, "Skipping controller with empty name: deviceId=$deviceId")
       return@forEach
      }

      val controller = Controller(
       deviceId = deviceId,
       name = device.name,
       vendorId = device.vendorId,
       productId = device.productId,
       controllerNumber = device.controllerNumber,
       type = ControllerType.fromVendorId(device.vendorId),
       isConnected = true
      )

      controllers.add(controller)
      AppLogger.d(TAG, "Detected controller: ${controller.name} (${controller.type})")
     }
    } catch (e: Exception) {
     AppLogger.w(TAG, "Error processing device $deviceId", e)
    }
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Error getting device IDs", e)
  }

  return controllers
 }
}
