package com.steamdeck.mobile.domain.model

import androidx.compose.runtime.Immutable

/**
 * Game controller domain model.
 *
 * Represents a physical game controller detected by Android InputDevice API.
 * Supports Xbox, PlayStation, Nintendo, and generic controllers.
 *
 * @Immutable annotation enables Compose Strong Skipping optimization
 * Reference: https://developer.android.com/develop/ui/compose/performance/bestpractices
 */
@Immutable
data class Controller(
 val deviceId: Int,
 val name: String,
 val vendorId: Int,
 val productId: Int,
 val controllerNumber: Int,
 val type: ControllerType,
 val isConnected: Boolean = true
) {
 /**
  * Unique identifier combining vendor and product IDs.
  */
 val uniqueId: String
  get() = "${vendorId.toString(16).padStart(4, '0')}:${productId.toString(16).padStart(4, '0')}"
}

/**
 * Controller type classification.
 *
 * Best Practice: Add displayName property to avoid ProGuard/R8 obfuscation
 * Reference: https://medium.com/codex/common-progaurd-rules-you-must-know-for-android-189205301453
 */
enum class ControllerType {
 XBOX,   // Xbox One/Series controllers (Vendor ID: 0x045E)
 PLAYSTATION, // PlayStation 4/5 DualShock/DualSense (Vendor ID: 0x054C)
 NINTENDO,  // Nintendo Switch Pro Controller (Vendor ID: 0x057E)
 GENERIC;  // Other HID-compliant controllers

 /**
  * Display name for UI (not obfuscated by ProGuard/R8)
  */
 val displayName: String
  get() = when (this) {
   XBOX -> "Xbox Controller"
   PLAYSTATION -> "PlayStation Controller"
   NINTENDO -> "Nintendo Controller"
   GENERIC -> "Generic Controller"
  }

 companion object {
  /**
   * Detect controller type from vendor ID.
   *
   * @param vendorId USB vendor ID (e.g., 0x045E for Microsoft)
   * @return Detected controller type
   */
  fun fromVendorId(vendorId: Int): ControllerType {
   return when (vendorId) {
    0x045E -> XBOX   // Microsoft
    0x054C -> PLAYSTATION // Sony
    0x057E -> NINTENDO  // Nintendo
    else -> GENERIC
   }
  }
 }
}

/**
 * Button mapping profile for a controller.
 *
 * Maps Android keycodes to game actions.
 *
 * @Immutable annotation enables Compose Strong Skipping optimization
 */
@Immutable
data class ButtonMapping(
 val buttonA: GameAction = GameAction.CONFIRM,
 val buttonB: GameAction = GameAction.CANCEL,
 val buttonX: GameAction = GameAction.ACTION1,
 val buttonY: GameAction = GameAction.ACTION2,
 val buttonL1: GameAction = GameAction.SHOULDER_LEFT,
 val buttonR1: GameAction = GameAction.SHOULDER_RIGHT,
 val buttonL2: GameAction = GameAction.TRIGGER_LEFT,
 val buttonR2: GameAction = GameAction.TRIGGER_RIGHT,
 val buttonStart: GameAction = GameAction.MENU,
 val buttonSelect: GameAction = GameAction.VIEW,
 val dpadUp: GameAction = GameAction.DPAD_UP,
 val dpadDown: GameAction = GameAction.DPAD_DOWN,
 val dpadLeft: GameAction = GameAction.DPAD_LEFT,
 val dpadRight: GameAction = GameAction.DPAD_RIGHT,
 val leftStickButton: GameAction = GameAction.STICK_LEFT,
 val rightStickButton: GameAction = GameAction.STICK_RIGHT
) {
 companion object {
  /**
   * Default Xbox-style mapping.
   */
  val XBOX_DEFAULT = ButtonMapping()

  /**
   * PlayStation-style mapping (swap A/B, X/Y).
   */
  val PLAYSTATION_DEFAULT = ButtonMapping(
   buttonA = GameAction.CANCEL, // Cross
   buttonB = GameAction.CONFIRM, // Circle
   buttonX = GameAction.ACTION2, // Square
   buttonY = GameAction.ACTION1 // Triangle
  )
 }
}

/**
 * Game action types.
 *
 * Abstract representation of game inputs, independent of controller type.
 */
enum class GameAction {
 CONFIRM,
 CANCEL,
 ACTION1,
 ACTION2,
 SHOULDER_LEFT,
 SHOULDER_RIGHT,
 TRIGGER_LEFT,
 TRIGGER_RIGHT,
 MENU,
 VIEW,
 DPAD_UP,
 DPAD_DOWN,
 DPAD_LEFT,
 DPAD_RIGHT,
 STICK_LEFT,
 STICK_RIGHT,
 NONE
}

/**
 * Joystick state.
 *
 * Represents analog stick and trigger positions.
 *
 * @Immutable annotation enables Compose Strong Skipping optimization
 */
@Immutable
data class JoystickState(
 val leftX: Float = 0f,
 val leftY: Float = 0f,
 val rightX: Float = 0f,
 val rightY: Float = 0f,
 val leftTrigger: Float = 0f,
 val rightTrigger: Float = 0f
) {
 /**
  * Apply deadzone to axis value.
  *
  * @param value Axis value (-1.0 to 1.0)
  * @param deadzone Deadzone threshold (default: 0.1)
  * @return Value with deadzone applied
  */
 fun applyDeadzone(value: Float, deadzone: Float = 0.1f): Float {
  return if (kotlin.math.abs(value) < deadzone) 0f else value
 }

 /**
  * Get normalized left stick position with deadzone.
  */
 fun getLeftStick(deadzone: Float = 0.1f): Pair<Float, Float> {
  return Pair(
   applyDeadzone(leftX, deadzone),
   applyDeadzone(leftY, deadzone)
  )
 }

 /**
  * Get normalized right stick position with deadzone.
  */
 fun getRightStick(deadzone: Float = 0.1f): Pair<Float, Float> {
  return Pair(
   applyDeadzone(rightX, deadzone),
   applyDeadzone(rightY, deadzone)
  )
 }
}

/**
 * Controller profile.
 *
 * Saved configuration for a specific controller.
 *
 * @Immutable annotation enables Compose Strong Skipping optimization
 */
@Immutable
data class ControllerProfile(
 val id: Long = 0,
 val controllerId: String, // uniqueId from Controller
 val name: String,
 val buttonMapping: ButtonMapping = ButtonMapping.XBOX_DEFAULT,
 val vibrationEnabled: Boolean = true,
 val deadzone: Float = 0.1f,
 val createdAt: Long = System.currentTimeMillis(),
 val lastUsedAt: Long = System.currentTimeMillis()
)
