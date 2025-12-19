package com.steamdeck.mobile.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.controller.ControllerManager
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.input.GameControllerManager
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for controller settings and configuration UI.
 *
 * Manages controller detection, profile management, and button mapping customization.
 * Integrates both legacy ControllerManager and new GameControllerManager.
 */
@HiltViewModel
class ControllerViewModel @Inject constructor(
 private val controllerManager: ControllerManager,
 private val gameControllerManager: GameControllerManager
) : ViewModel() {

 companion object {
  private const val TAG = "ControllerVM"
 }

 // UI State
 private val _uiState = MutableStateFlow<ControllerUiState>(ControllerUiState.Loading)
 val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

 // Connected controllers from ControllerManager (legacy)
 val connectedControllers: StateFlow<List<Controller>> = controllerManager.connectedControllers

 // Active controller
 val activeController: StateFlow<Controller?> = controllerManager.activeController

 // Joystick state (for live preview)
 val joystickState: StateFlow<JoystickState> = controllerManager.joystickState

 // Button events (for button mapping)
 val buttonEvents: SharedFlow<com.steamdeck.mobile.core.controller.ButtonEvent> = controllerManager.buttonEvents

 // NEW: Real-time controller state from GameControllerManager
 val controllerStates = gameControllerManager.controllerState

 // Profiles for active controller
 private val _profiles = MutableStateFlow<List<ControllerProfile>>(emptyList())
 val profiles: StateFlow<List<ControllerProfile>> = _profiles.asStateFlow()

 // Currently editing profile
 private val _editingProfile = MutableStateFlow<ControllerProfile?>(null)
 val editingProfile: StateFlow<ControllerProfile?> = _editingProfile.asStateFlow()

 init {
  loadControllers()
  observeActiveControllerProfiles()
 }

 /**
  * Load connected controllers.
  */
 private fun loadControllers() {
  viewModelScope.launch {
   try {
    _uiState.value = ControllerUiState.Loading
    controllerManager.detectControllers()
    _uiState.value = ControllerUiState.Success
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to load controllers", e)
    _uiState.value = ControllerUiState.Error(e.message ?: "controller検出エラー")
   }
  }
 }

 /**
  * Observe active controller changes and reload profiles.
  */
 private fun observeActiveControllerProfiles() {
  viewModelScope.launch {
   activeController.collect { controller ->
    if (controller != null) {
     loadProfilesForActiveController()
    } else {
     _profiles.value = emptyList()
    }
   }
  }
 }

 /**
  * Load profiles for the currently active controller.
  */
 private suspend fun loadProfilesForActiveController() {
  try {
   val profilesList = controllerManager.getProfilesForActiveController()
   _profiles.value = profilesList
   AppLogger.d(TAG, "Loaded ${profilesList.size} profiles for active controller")
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to load profiles", e)
   _profiles.value = emptyList()
  }
 }

 /**
  * Refresh controller detection.
  */
 fun refreshControllers() {
  loadControllers()
 }

 /**
  * Set the active controller for configuration.
  *
  * @param controller Controller to activate
  */
 fun setActiveController(controller: Controller) {
  controllerManager.setActiveController(controller)
  AppLogger.d(TAG, "Active controller changed to: ${controller.name}")
 }

 /**
  * Start creating a new profile.
  *
  * @param baseName Base name for the profile (e.g., "Custom Profile")
  */
 fun startCreateProfile(baseName: String = "Custom Profile") {
  viewModelScope.launch {
   val controller = activeController.value
   if (controller == null) {
    AppLogger.w(TAG, "Cannot create profile: No active controller")
    return@launch
   }

   val newProfile = ControllerProfile(
    id = 0, // Auto-generated
    controllerId = controller.uniqueId,
    name = baseName,
    buttonMapping = when (controller.type) {
     ControllerType.PLAYSTATION -> ButtonMapping.PLAYSTATION_DEFAULT
     else -> ButtonMapping.XBOX_DEFAULT
    },
    vibrationEnabled = true,
    deadzone = 0.1f,
    createdAt = System.currentTimeMillis(),
    lastUsedAt = System.currentTimeMillis()
   )

   _editingProfile.value = newProfile
   AppLogger.d(TAG, "Started creating new profile: $baseName")
  }
 }

 /**
  * Start editing an existing profile.
  *
  * @param profile Profile to edit
  */
 fun startEditProfile(profile: ControllerProfile) {
  _editingProfile.value = profile
  AppLogger.d(TAG, "Started editing profile: ${profile.name}")
 }

 /**
  * Update button mapping for the editing profile.
  *
  * @param newMapping Updated button mapping
  */
 fun updateButtonMapping(newMapping: ButtonMapping) {
  val current = _editingProfile.value ?: return
  _editingProfile.value = current.copy(buttonMapping = newMapping)
  AppLogger.d(TAG, "Updated button mapping for profile: ${current.name}")
 }

 /**
  * Update a specific button in the mapping.
  *
  * @param buttonKey Button identifier (e.g., "buttonA", "buttonB")
  * @param action New game action
  */
 fun updateButtonAction(buttonKey: String, action: GameAction) {
  val current = _editingProfile.value ?: return
  val currentMapping = current.buttonMapping

  val newMapping = when (buttonKey) {
   "buttonA" -> currentMapping.copy(buttonA = action)
   "buttonB" -> currentMapping.copy(buttonB = action)
   "buttonX" -> currentMapping.copy(buttonX = action)
   "buttonY" -> currentMapping.copy(buttonY = action)
   "buttonL1" -> currentMapping.copy(buttonL1 = action)
   "buttonR1" -> currentMapping.copy(buttonR1 = action)
   "buttonL2" -> currentMapping.copy(buttonL2 = action)
   "buttonR2" -> currentMapping.copy(buttonR2 = action)
   "buttonStart" -> currentMapping.copy(buttonStart = action)
   "buttonSelect" -> currentMapping.copy(buttonSelect = action)
   "dpadUp" -> currentMapping.copy(dpadUp = action)
   "dpadDown" -> currentMapping.copy(dpadDown = action)
   "dpadLeft" -> currentMapping.copy(dpadLeft = action)
   "dpadRight" -> currentMapping.copy(dpadRight = action)
   "leftStickButton" -> currentMapping.copy(leftStickButton = action)
   "rightStickButton" -> currentMapping.copy(rightStickButton = action)
   else -> {
    AppLogger.w(TAG, "Unknown button key: $buttonKey")
    return
   }
  }

  _editingProfile.value = current.copy(buttonMapping = newMapping)
  AppLogger.d(TAG, "Updated $buttonKey to $action")
 }

 /**
  * Update vibration setting for editing profile.
  *
  * @param enabled Whether vibration is enabled
  */
 fun updateVibration(enabled: Boolean) {
  val current = _editingProfile.value ?: return
  _editingProfile.value = current.copy(vibrationEnabled = enabled)
  AppLogger.d(TAG, "Updated vibration: $enabled")
 }

 /**
  * Update deadzone for editing profile.
  *
  * @param deadzone Deadzone value (0.0 - 1.0)
  */
 fun updateDeadzone(deadzone: Float) {
  val current = _editingProfile.value ?: return
  val clampedDeadzone = deadzone.coerceIn(0f, 1f)
  _editingProfile.value = current.copy(deadzone = clampedDeadzone)
  AppLogger.d(TAG, "Updated deadzone: $clampedDeadzone")
 }

 /**
  * Save the currently editing profile.
  */
 fun saveProfile() {
  viewModelScope.launch {
   val profile = _editingProfile.value
   if (profile == null) {
    AppLogger.w(TAG, "Cannot save: No profile being edited")
    return@launch
   }

   try {
    _uiState.value = ControllerUiState.Loading

    val legacyResult = if (profile.id == 0L) {
     // New profile - returns Result<Long>
     controllerManager.saveProfile(profile).map { Unit }
    } else {
     // Update existing - returns Result<Unit>
     controllerManager.updateProfile(profile)
    }

    // Convert legacy Result<T> to DataResult<T>
    val result = DataResult.fromResult(legacyResult)

    when (result) {
     is DataResult.Success -> {
      AppLogger.i(TAG, "Profile saved successfully: ${profile.name}")
      _editingProfile.value = null
      loadProfilesForActiveController()
      _uiState.value = ControllerUiState.Success
     }
     is DataResult.Error -> {
      AppLogger.e(TAG, "Failed to save profile: ${profile.name}")
      _uiState.value = ControllerUiState.Error("プロファイルsaveエラー: ${result.error}")
     }
     is DataResult.Loading -> {
      // Should not happen for legacy Result conversion
     }
    }
   } catch (e: Exception) {
    AppLogger.e(TAG, "Exception saving profile", e)
    _uiState.value = ControllerUiState.Error(e.message ?: "不明なエラー")
   }
  }
 }

 /**
  * Cancel editing the current profile.
  */
 fun cancelEditProfile() {
  _editingProfile.value = null
  AppLogger.d(TAG, "Cancelled profile editing")
 }

 /**
  * Delete a profile.
  *
  * @param profile Profile to delete
  */
 fun deleteProfile(profile: ControllerProfile) {
  viewModelScope.launch {
   try {
    _uiState.value = ControllerUiState.Loading

    val legacyResult = controllerManager.deleteProfile(profile)
    val result = DataResult.fromResult(legacyResult)

    when (result) {
     is DataResult.Success -> {
      AppLogger.i(TAG, "Profile deleted: ${profile.name}")
      loadProfilesForActiveController()
      _uiState.value = ControllerUiState.Success
     }
     is DataResult.Error -> {
      AppLogger.e(TAG, "Failed to delete profile: ${profile.name}")
      _uiState.value = ControllerUiState.Error("プロファイルdeleteエラー: ${result.error}")
     }
     is DataResult.Loading -> {
      // Should not happen for legacy Result conversion
     }
    }
   } catch (e: Exception) {
    AppLogger.e(TAG, "Exception deleting profile", e)
    _uiState.value = ControllerUiState.Error(e.message ?: "不明なエラー")
   }
  }
 }

 /**
  * Reset to default mapping for the current controller type.
  */
 fun resetToDefault() {
  val controller = activeController.value ?: return
  val current = _editingProfile.value ?: return

  val defaultMapping = when (controller.type) {
   ControllerType.PLAYSTATION -> ButtonMapping.PLAYSTATION_DEFAULT
   else -> ButtonMapping.XBOX_DEFAULT
  }

  _editingProfile.value = current.copy(buttonMapping = defaultMapping)
  AppLogger.d(TAG, "Reset to ${controller.type} default mapping")
 }
}

/**
 * UI state for controller configuration screen.
 */
@Immutable
sealed class ControllerUiState {
 @Immutable
 data object Loading : ControllerUiState()
 @Immutable
 data object Success : ControllerUiState()
 @Immutable
 data class Error(val message: String) : ControllerUiState()
}
