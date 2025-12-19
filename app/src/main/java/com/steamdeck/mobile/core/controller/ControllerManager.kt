package com.steamdeck.mobile.core.controller

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.steamdeck.mobile.domain.model.*
import com.steamdeck.mobile.domain.repository.ControllerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for game controller input handling.
 *
 * Handles controller detection, input events, and profile management.
 * Emits normalized controller events for game input.
 */
@Singleton
class ControllerManager @Inject constructor(
 @ApplicationContext private val context: Context,
 private val repository: ControllerRepository
) {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

 companion object {
  private const val TAG = "ControllerManager"
 }

 // Connected controllers
 private val _connectedControllers = MutableStateFlow<List<Controller>>(emptyList())
 val connectedControllers: StateFlow<List<Controller>> = _connectedControllers.asStateFlow()

 // Active controller (for single-player games)
 private val _activeController = MutableStateFlow<Controller?>(null)
 val activeController: StateFlow<Controller?> = _activeController.asStateFlow()

 // Current joystick state
 private val _joystickState = MutableStateFlow(JoystickState())
 val joystickState: StateFlow<JoystickState> = _joystickState.asStateFlow()

 // Button press events
 private val _buttonEvents = MutableSharedFlow<ButtonEvent>()
 val buttonEvents: SharedFlow<ButtonEvent> = _buttonEvents.asSharedFlow()

 // Active profile - use StateFlow for thread safety
 private val _activeProfile = MutableStateFlow<ControllerProfile?>(null)
 private val _activeButtonMapping = MutableStateFlow(ButtonMapping.XBOX_DEFAULT)
 private val activeButtonMapping: ButtonMapping
  get() = _activeButtonMapping.value

 init {
  // Detect controllers on initialization
  detectControllers()
 }

 /**
  * Detect connected controllers.
  *
  * Note: Flow emits once and completes (InputDevice API has no change notifications).
  * Call this method to refresh controller list.
  */
 fun detectControllers() {
  scope.launch {
   repository.getConnectedControllers().collect { controllers ->
    _connectedControllers.value = controllers
    Log.i(TAG, "Detected ${controllers.size} controllers")

    // Auto-select first controller if none active
    if (_activeController.value == null && controllers.isNotEmpty()) {
     setActiveController(controllers.first())
    }
   }
  }
 }

 /**
  * Set the active controller for single-player games.
  *
  * @param controller Controller to activate
  */
 fun setActiveController(controller: Controller) {
  scope.launch {
   _activeController.value = controller
   Log.i(TAG, "Active controller set: ${controller.name}")

   // Load profile for this controller
   loadProfileForController(controller)
  }
 }

 /**
  * Load the last used profile for a controller.
  *
  * @param controller Controller to load profile for
  */
 private suspend fun loadProfileForController(controller: Controller) {
  val result = repository.getLastUsedProfile(controller.uniqueId)
  result.onSuccess { profile ->
   if (profile != null) {
    _activeProfile.value = profile
    _activeButtonMapping.value = profile.buttonMapping
    Log.i(TAG, "Loaded profile: ${profile.name}")
   } else {
    // Use default mapping based on controller type
    _activeProfile.value = null
    _activeButtonMapping.value = when (controller.type) {
     ControllerType.PLAYSTATION -> ButtonMapping.PLAYSTATION_DEFAULT
     else -> ButtonMapping.XBOX_DEFAULT
    }
    Log.i(TAG, "Using default ${controller.type} mapping")
   }
  }.onFailure { error ->
   Log.w(TAG, "Failed to load profile, using default", error)
   _activeProfile.value = null
   _activeButtonMapping.value = ButtonMapping.XBOX_DEFAULT
  }
 }

 /**
  * Handle button press events from KeyEvent.
  *
  * Call this from your Composable's onKeyEvent handler.
  *
  * @param event KeyEvent from Android
  * @return true if event was handled, false otherwise
  */
 fun handleKeyEvent(event: KeyEvent): Boolean {
  if (event.repeatCount > 0) return false // Ignore key repeats

  val gameAction = mapKeycodeToGameAction(event.keyCode) ?: return false

  val buttonEvent = ButtonEvent(
   action = gameAction,
   isPressed = event.action == KeyEvent.ACTION_DOWN,
   deviceId = event.deviceId
  )

  scope.launch {
   _buttonEvents.emit(buttonEvent)
  }

  Log.d(TAG, "Button event: $gameAction ${if (buttonEvent.isPressed) "pressed" else "released"}")
  return true
 }

 /**
  * Handle joystick motion events from MotionEvent.
  *
  * Call this from your Composable's onGenericMotionEvent handler.
  *
  * @param event MotionEvent from Android
  * @return true if event was handled, false otherwise
  */
 fun handleMotionEvent(event: MotionEvent): Boolean {
  if (event.action != MotionEvent.ACTION_MOVE) return false

  val newState = JoystickState(
   leftX = event.getAxisValue(MotionEvent.AXIS_X),
   leftY = event.getAxisValue(MotionEvent.AXIS_Y),
   rightX = event.getAxisValue(MotionEvent.AXIS_Z),
   rightY = event.getAxisValue(MotionEvent.AXIS_RZ),
   leftTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
   rightTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
  )

  _joystickState.value = newState
  return true
 }

 /**
  * Map Android keycode to GameAction using active button mapping.
  *
  * @param keyCode Android KeyEvent keycode
  * @return Mapped GameAction or null if not mapped
  */
 private fun mapKeycodeToGameAction(keyCode: Int): GameAction? {
  return when (keyCode) {
   KeyEvent.KEYCODE_BUTTON_A -> activeButtonMapping.buttonA
   KeyEvent.KEYCODE_BUTTON_B -> activeButtonMapping.buttonB
   KeyEvent.KEYCODE_BUTTON_X -> activeButtonMapping.buttonX
   KeyEvent.KEYCODE_BUTTON_Y -> activeButtonMapping.buttonY
   KeyEvent.KEYCODE_BUTTON_L1 -> activeButtonMapping.buttonL1
   KeyEvent.KEYCODE_BUTTON_R1 -> activeButtonMapping.buttonR1
   KeyEvent.KEYCODE_BUTTON_L2 -> activeButtonMapping.buttonL2
   KeyEvent.KEYCODE_BUTTON_R2 -> activeButtonMapping.buttonR2
   KeyEvent.KEYCODE_BUTTON_START -> activeButtonMapping.buttonStart
   KeyEvent.KEYCODE_BUTTON_SELECT -> activeButtonMapping.buttonSelect
   KeyEvent.KEYCODE_DPAD_UP -> activeButtonMapping.dpadUp
   KeyEvent.KEYCODE_DPAD_DOWN -> activeButtonMapping.dpadDown
   KeyEvent.KEYCODE_DPAD_LEFT -> activeButtonMapping.dpadLeft
   KeyEvent.KEYCODE_DPAD_RIGHT -> activeButtonMapping.dpadRight
   KeyEvent.KEYCODE_BUTTON_THUMBL -> activeButtonMapping.leftStickButton
   KeyEvent.KEYCODE_BUTTON_THUMBR -> activeButtonMapping.rightStickButton
   else -> null
  }
 }

 /**
  * Save a new controller profile.
  *
  * @param profile Profile to save
  * @return Result with profile ID
  */
 suspend fun saveProfile(profile: ControllerProfile): Result<Long> {
  return repository.saveProfile(profile)
 }

 /**
  * Update an existing profile.
  *
  * @param profile Profile to update
  */
 suspend fun updateProfile(profile: ControllerProfile): Result<Unit> {
  return repository.updateProfile(profile)
 }

 /**
  * Delete a profile.
  *
  * @param profile Profile to delete
  */
 suspend fun deleteProfile(profile: ControllerProfile): Result<Unit> {
  return repository.deleteProfile(profile)
 }

 /**
  * Get all profiles for the active controller.
  *
  * @return List of profiles or empty list if no controller active
  */
 suspend fun getProfilesForActiveController(): List<ControllerProfile> {
  return _activeController.value?.let { controller ->
   repository.getProfilesForController(controller.uniqueId).first()
  } ?: emptyList()
 }
}

/**
 * Button press/release event.
 */
data class ButtonEvent(
 val action: GameAction,
 val isPressed: Boolean,
 val deviceId: Int
)
