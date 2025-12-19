package com.steamdeck.mobile.core.input

import android.content.Context
import android.hardware.input.InputManager
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Game controller management class
 *
 * Game controller management using Android InputDevice API:
 * - Monitor controller connection/disconnection
 * - Process button input and analog stick input
 * - Support multiple controllers
 * - Prepare input mapping to Wine
 *
 * Best Practices:
 * - Hot-plug support with InputManager.InputDeviceListener
 * - Support both SOURCE_GAMEPAD and SOURCE_JOYSTICK
 * - Monitor connected controllers with StateFlow
 */
@Singleton
class GameControllerManager @Inject constructor(
 @ApplicationContext private val context: Context
) : InputManager.InputDeviceListener {

 companion object {
  private const val TAG = "GameControllerManager"
 }

 private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

 private val _connectedControllers = MutableStateFlow<List<GameController>>(emptyList())
 val connectedControllers: StateFlow<List<GameController>> = _connectedControllers.asStateFlow()

 private val _controllerState = MutableStateFlow<Map<Int, ControllerState>>(emptyMap())
 val controllerState: StateFlow<Map<Int, ControllerState>> = _controllerState.asStateFlow()

 init {
  // Detect existing controllers
  discoverControllers()

  // Start hot-plug monitor
  inputManager.registerInputDeviceListener(this, null)

  Log.i(TAG, "GameControllerManager initialized with ${_connectedControllers.value.size} controllers")
 }

 /**
  * Detect connected controllers
  */
 private fun discoverControllers() {
  val deviceIds = inputManager.inputDeviceIds
  val controllers = mutableListOf<GameController>()

  for (deviceId in deviceIds) {
   val inputDevice = inputManager.getInputDevice(deviceId)
   if (isGameController(inputDevice)) {
    inputDevice?.let { device ->
     controllers.add(createGameController(device))
    }
   }
  }

  _connectedControllers.value = controllers
  Log.d(TAG, "Discovered ${controllers.size} game controllers")
 }

 /**
  * Determine if device is game controller
  */
 private fun isGameController(device: InputDevice?): Boolean {
  if (device == null) return false

  val sources = device.sources
  return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
    (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
 }

 /**
  * Generate GameController data class
  */
 private fun createGameController(device: InputDevice): GameController {
  return GameController(
   id = device.id,
   name = device.name,
   vendorId = device.vendorId,
   productId = device.productId,
   descriptor = device.descriptor,
   supportsVibration = device.vibrator.hasVibrator()
  )
 }

 /**
  * Process key input
  */
 fun handleKeyEvent(deviceId: Int, event: KeyEvent): Boolean {
  if (!isControllerConnected(deviceId)) return false

  val currentState = _controllerState.value[deviceId] ?: ControllerState()
  val updatedState = when (event.keyCode) {
   // D-Pad
   KeyEvent.KEYCODE_DPAD_UP -> currentState.copy(dpadUp = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_DPAD_DOWN -> currentState.copy(dpadDown = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_DPAD_LEFT -> currentState.copy(dpadLeft = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_DPAD_RIGHT -> currentState.copy(dpadRight = event.action == KeyEvent.ACTION_DOWN)

   // Face buttons (A/B/X/Y or Cross/Circle/Square/Triangle)
   KeyEvent.KEYCODE_BUTTON_A -> currentState.copy(buttonA = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_B -> currentState.copy(buttonB = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_X -> currentState.copy(buttonX = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_Y -> currentState.copy(buttonY = event.action == KeyEvent.ACTION_DOWN)

   // Shoulder buttons
   KeyEvent.KEYCODE_BUTTON_L1 -> currentState.copy(buttonL1 = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_R1 -> currentState.copy(buttonR1 = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_L2 -> currentState.copy(buttonL2 = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_R2 -> currentState.copy(buttonR2 = event.action == KeyEvent.ACTION_DOWN)

   // Thumb buttons
   KeyEvent.KEYCODE_BUTTON_THUMBL -> currentState.copy(buttonThumbL = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_THUMBR -> currentState.copy(buttonThumbR = event.action == KeyEvent.ACTION_DOWN)

   // System buttons
   KeyEvent.KEYCODE_BUTTON_START -> currentState.copy(buttonStart = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_SELECT -> currentState.copy(buttonSelect = event.action == KeyEvent.ACTION_DOWN)
   KeyEvent.KEYCODE_BUTTON_MODE -> currentState.copy(buttonMode = event.action == KeyEvent.ACTION_DOWN)

   else -> return false // Unsupported key
  }

  _controllerState.value = _controllerState.value + (deviceId to updatedState)
  Log.v(TAG, "Controller $deviceId: KeyEvent ${event.keyCode} action=${event.action}")
  return true
 }

 /**
  * Process motion input (analog stick, trigger)
  */
 fun handleMotionEvent(deviceId: Int, event: MotionEvent): Boolean {
  if (!isControllerConnected(deviceId)) return false

  val currentState = _controllerState.value[deviceId] ?: ControllerState()

  // Left stick
  val leftX = event.getAxisValue(MotionEvent.AXIS_X)
  val leftY = event.getAxisValue(MotionEvent.AXIS_Y)

  // Right stick
  val rightX = event.getAxisValue(MotionEvent.AXIS_Z)
  val rightY = event.getAxisValue(MotionEvent.AXIS_RZ)

  // Triggers (L2/R2 analog)
  val triggerL = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
  val triggerR = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

  // Hat switch (D-Pad analog)
  val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
  val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

  val updatedState = currentState.copy(
   leftStickX = leftX,
   leftStickY = leftY,
   rightStickX = rightX,
   rightStickY = rightY,
   triggerL = triggerL,
   triggerR = triggerR,
   hatX = hatX,
   hatY = hatY
  )

  _controllerState.value = _controllerState.value + (deviceId to updatedState)
  Log.v(TAG, "Controller $deviceId: Motion LS($leftX,$leftY) RS($rightX,$rightY) Triggers($triggerL,$triggerR)")
  return true
 }

 /**
  * Check if controller is connected
  */
 fun isControllerConnected(deviceId: Int): Boolean {
  return _connectedControllers.value.any { it.id == deviceId }
 }

 /**
  * Callback when device added
  */
 override fun onInputDeviceAdded(deviceId: Int) {
  val inputDevice = inputManager.getInputDevice(deviceId)
  if (isGameController(inputDevice)) {
   val controller = inputDevice?.let { createGameController(it) }
   if (controller != null) {
    _connectedControllers.value = _connectedControllers.value + controller
    Log.i(TAG, "Controller connected: ${controller.name} (ID: $deviceId)")
   }
  }
 }

 /**
  * Callback when device removed
  */
 override fun onInputDeviceRemoved(deviceId: Int) {
  val removedController = _connectedControllers.value.find { it.id == deviceId }
  if (removedController != null) {
   _connectedControllers.value = _connectedControllers.value.filter { it.id != deviceId }
   _controllerState.value = _controllerState.value - deviceId
   Log.i(TAG, "Controller disconnected: ${removedController.name} (ID: $deviceId)")
  }
 }

 /**
  * Callback when device changed
  */
 override fun onInputDeviceChanged(deviceId: Int) {
  val inputDevice = inputManager.getInputDevice(deviceId)
  if (isGameController(inputDevice)) {
   val controller = inputDevice?.let { createGameController(it) }
   if (controller != null) {
    _connectedControllers.value = _connectedControllers.value.map {
     if (it.id == deviceId) controller else it
    }
    Log.d(TAG, "Controller changed: ${controller.name} (ID: $deviceId)")
   }
  }
 }

 /**
  * Get current state of specific controller
  */
 fun getControllerState(deviceId: Int): ControllerState? {
  return _controllerState.value[deviceId]
 }

 /**
  * Controller vibration (not yet implemented)
  */
 fun vibrate(deviceId: Int, durationMs: Long) {
  val inputDevice = inputManager.getInputDevice(deviceId)
  val vibrator = inputDevice?.vibrator
  if (vibrator?.hasVibrator() == true) {
   vibrator.vibrate(durationMs)
   Log.d(TAG, "Controller $deviceId vibrating for ${durationMs}ms")
  }
 }

 /**
  * Cleanup
  */
 fun cleanup() {
  inputManager.unregisterInputDeviceListener(this)
  Log.i(TAG, "GameControllerManager cleanup complete")
 }
}

/**
 * Game controller information
 */
data class GameController(
 val id: Int,
 val name: String,
 val vendorId: Int,
 val productId: Int,
 val descriptor: String,
 val supportsVibration: Boolean
)

/**
 * Controller input state
 *
 * Hold all button and analog inputs
 */
data class ControllerState(
 // D-Pad (digital)
 val dpadUp: Boolean = false,
 val dpadDown: Boolean = false,
 val dpadLeft: Boolean = false,
 val dpadRight: Boolean = false,

 // Face buttons
 val buttonA: Boolean = false,
 val buttonB: Boolean = false,
 val buttonX: Boolean = false,
 val buttonY: Boolean = false,

 // Shoulder buttons
 val buttonL1: Boolean = false,
 val buttonR1: Boolean = false,
 val buttonL2: Boolean = false,
 val buttonR2: Boolean = false,

 // Thumb buttons (stick press)
 val buttonThumbL: Boolean = false,
 val buttonThumbR: Boolean = false,

 // System buttons
 val buttonStart: Boolean = false,
 val buttonSelect: Boolean = false,
 val buttonMode: Boolean = false,

 // Analog sticks (-1.0 to 1.0)
 val leftStickX: Float = 0f,
 val leftStickY: Float = 0f,
 val rightStickX: Float = 0f,
 val rightStickY: Float = 0f,

 // Analog triggers (0.0 to 1.0)
 val triggerL: Float = 0f,
 val triggerR: Float = 0f,

 // Hat switch (D-Pad analog, -1.0 to 1.0)
 val hatX: Float = 0f,
 val hatY: Float = 0f
)
