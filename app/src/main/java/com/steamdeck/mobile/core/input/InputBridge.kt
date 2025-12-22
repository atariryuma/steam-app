package com.steamdeck.mobile.core.input

import android.content.Context
import android.content.pm.PackageManager
import com.steamdeck.mobile.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wine input mapping bridge
 *
 * Research findings implementation:
 * - Strategy 1: Native uinput injection (future implementation)
 * - Strategy 2: SDL event injection (experimental)
 * - Strategy 3: InputBridge app integration (current implementation) ✅
 *
 * With InputBridge app integration, send Android input to Wine/Proton games in
 * DirectInput/XInput format.
 *
 * Best Practices:
 * - Zero root requirement
 * - User-friendly configuration
 * - Works with Mobox/Winlator pattern
 */
interface InputBridge {
 /**
  * Initialization processing
  */
 fun initialize(): Result<Unit>

 /**
  * Check if InputBridge is installed
  */
 fun isInstalled(): Boolean

 /**
  * Launch InputBridge app
  */
 fun launch(): Result<Unit>

 /**
  * cleanup
  */
 fun cleanup()
}

/**
 * InputBridge app integration implementation
 *
 * InputBridge app: https://inputbridge.net/
 * - Create virtual gamepad
 * - User-friendly button mapping UI
 * - Auto-detect Wine/Proton games
 */
@Singleton
class InputBridgeAppIntegration @Inject constructor(
 @ApplicationContext private val context: Context
) : InputBridge {

 companion object {
  private const val TAG = "InputBridgeApp"
  private const val INPUT_BRIDGE_PACKAGE = "com.inputbridge.app"

  // Alternative packages (if different)
  private val ALTERNATIVE_PACKAGES = listOf(
   "com.inputbridge",
   "net.inputbridge.app"
  )
 }

 private var installedPackage: String? = null

 override fun initialize(): Result<Unit> {
  return try {
   installedPackage = detectInputBridgePackage()
   if (installedPackage != null) {
    AppLogger.i(TAG, "InputBridge detected: $installedPackage")
    Result.success(Unit)
   } else {
    AppLogger.w(TAG, "InputBridge not installed")
    Result.failure(InputBridgeNotInstalledException())
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to initialize InputBridge", e)
   Result.failure(e)
  }
 }

 override fun isInstalled(): Boolean {
  if (installedPackage != null) return true
  installedPackage = detectInputBridgePackage()
  return installedPackage != null
 }

 override fun launch(): Result<Unit> {
  return try {
   val packageName = installedPackage ?: detectInputBridgePackage()
   if (packageName == null) {
    return Result.failure(InputBridgeNotInstalledException())
   }

   val intent = context.packageManager.getLaunchIntentForPackage(packageName)
   if (intent != null) {
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    AppLogger.i(TAG, "Launched InputBridge: $packageName")
    Result.success(Unit)
   } else {
    Result.failure(Exception("Failed to create launch intent for $packageName"))
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to launch InputBridge", e)
   Result.failure(e)
  }
 }

 override fun cleanup() {
  // No cleanup needed for app integration
  AppLogger.d(TAG, "InputBridge cleanup (no-op)")
 }

 /**
  * Detect InputBridge package
  */
 private fun detectInputBridgePackage(): String? {
  val packagesToCheck = listOf(INPUT_BRIDGE_PACKAGE) + ALTERNATIVE_PACKAGES

  for (pkg in packagesToCheck) {
   try {
    context.packageManager.getPackageInfo(pkg, 0)
    return pkg
   } catch (e: PackageManager.NameNotFoundException) {
    // Continue to next package
   }
  }

  return null
 }

 /**
  * Show InputBridge configuration guide (future implementation)
  */
 fun showSetupGuide(): String {
  return """
   ## InputBridge Setup Guide

   1. **Installation**
    - Install "InputBridge" from Google Play Store
    - or APK: https://inputbridge.net/download

   2. **Configuration**
    - Open InputBridge app
    - Tap "Create Virtual Controller"
    - Select controller type (Xbox 360 recommended)

   3. **Game Usage**
    - Launch game in SteamDeck Mobile
    - InputBridge automatically detects controller
    - Confirm button mapping in game configuration

   ## Troubleshooting

   - Controller not recognized
    → Recreate controller in InputBridge app

   - Buttons not responding
    → Re-detect controller in game configuration

   - Wine configuration check
    → Test controller with `wine control joy.cpl`
  """.trimIndent()
 }
}

/**
 * Native uinput implementation (future implementation)
 *
 * Research findings:
 * - Create virtual Xbox 360 controller via /dev/uinput
 * - Requires root or input group permissions
 * - Lowest latency, fully Wine-compatible
 *
 * TODO: Implement NDK native library
 * - libuinput_bridge.so
 * - JNI bindings
 * - SELinux policy (optional)
 */
@Singleton
class NativeUInputBridge @Inject constructor(
 @ApplicationContext private val context: Context
) : InputBridge {

 companion object {
  private const val TAG = "NativeUInputBridge"

  // Xbox 360 controller VID/PID
  private const val XBOX360_VENDOR_ID = 0x045e
  private const val XBOX360_PRODUCT_ID = 0x028e

  init {
   try {
    System.loadLibrary("uinput_bridge")
    AppLogger.i(TAG, "Native library loaded successfully")
   } catch (e: UnsatisfiedLinkError) {
    AppLogger.w(TAG, "Native library not available: ${e.message}")
   }
  }
 }

 private var isInitialized = false
 private var controllerId: Int = -1

 // Native methods (implemented in uinput_jni.c)
 private external fun nativeInit(): Boolean
 private external fun nativeCreateVirtualController(
  name: String,
  vendorId: Int,
  productId: Int
 ): Int
 private external fun nativeSendButtonEvent(button: Int, pressed: Boolean): Boolean
 private external fun nativeSendAxisEvent(axis: Int, value: Float): Boolean
 private external fun nativeDestroy()

 override fun initialize(): Result<Unit> {
  return try {
   if (isInitialized) {
    AppLogger.w(TAG, "Already initialized")
    return Result.success(Unit)
   }

   if (!nativeInit()) {
    val errorMsg = "Failed to initialize uinput (/dev/uinput permission denied?)"
    AppLogger.e(TAG, errorMsg)
    return Result.failure(Exception(errorMsg))
   }

   controllerId = nativeCreateVirtualController(
    "Steam Deck Mobile Controller",
    XBOX360_VENDOR_ID,
    XBOX360_PRODUCT_ID
   )

   if (controllerId < 0) {
    val errorMsg = "Failed to create virtual controller"
    AppLogger.e(TAG, errorMsg)
    return Result.failure(Exception(errorMsg))
   }

   isInitialized = true
   AppLogger.i(TAG, "Native uinput bridge initialized (controller ID: $controllerId)")
   Result.success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to initialize native uinput", e)
   Result.failure(e)
  }
 }

 override fun isInstalled(): Boolean {
  return isInitialized
 }

 override fun launch(): Result<Unit> {
  // uinput is automatically recognized by kernel, no launch needed
  return if (isInitialized) {
   Result.success(Unit)
  } else {
   Result.failure(Exception("Not initialized"))
  }
 }

 override fun cleanup() {
  if (isInitialized) {
   try {
    nativeDestroy()
    isInitialized = false
    controllerId = -1
    AppLogger.i(TAG, "Native uinput bridge cleaned up")
   } catch (e: Exception) {
    AppLogger.e(TAG, "Error during cleanup", e)
   }
  }
 }

 /**
  * Send button event
  * @param button Xbox button code (BTN_A=304, BTN_B=305, etc.)
  * @param pressed true for press, false for release
  * @return true on success, false on failure
  */
 fun sendButtonEvent(button: Int, pressed: Boolean): Boolean {
  if (!isInitialized) {
   AppLogger.w(TAG, "Not initialized, cannot send button event")
   return false
  }
  return nativeSendButtonEvent(button, pressed)
 }

 /**
  * Send axis event
  * @param axis evdev axis code (ABS_X=0, ABS_Y=1, etc.)
  * @param value -1.0 ~ 1.0 (Android value)
  * @return true on success, false on failure
  */
 fun sendAxisEvent(axis: Int, value: Float): Boolean {
  if (!isInitialized) {
   AppLogger.w(TAG, "Not initialized, cannot send axis event")
   return false
  }
  return nativeSendAxisEvent(axis, value)
 }

 /**
  * Android MotionEvent axis to Linux evdev code mapping
  *
  * Research findings:
  * - AXIS_X/Y → ABS_X/Y (left stick)
  * - AXIS_Z/RZ → ABS_RX/RY (right stick)
  * - AXIS_LTRIGGER/RTRIGGER → ABS_Z/RZ (triggers)
  */
 fun androidAxisToEvdev(androidAxis: Int): Int {
  return when (androidAxis) {
   android.view.MotionEvent.AXIS_X -> 0x00 // ABS_X
   android.view.MotionEvent.AXIS_Y -> 0x01 // ABS_Y
   android.view.MotionEvent.AXIS_Z -> 0x03 // ABS_RX
   android.view.MotionEvent.AXIS_RZ -> 0x04 // ABS_RY
   android.view.MotionEvent.AXIS_LTRIGGER -> 0x02 // ABS_Z
   android.view.MotionEvent.AXIS_RTRIGGER -> 0x05 // ABS_RZ
   else -> -1
  }
 }

 /**
  * Android float (-1.0 to 1.0) to evdev int (-32768 to 32767)
  */
 fun androidValueToEvdev(value: Float): Int {
  return ((value + 1.0f) * 32767.5f - 32768.0f).toInt().coerceIn(-32768, 32767)
 }
}

/**
 * Custom exception: InputBridge not installed
 */
class InputBridgeNotInstalledException : Exception(
 "InputBridge app not installed. Please install from Google Play or https://inputbridge.net/"
)

/**
 * Xbox 360 button code constants (Linux input event codes)
 */
object XboxButtonCodes {
 const val BTN_A = 0x130      // 304
 const val BTN_B = 0x131      // 305
 const val BTN_X = 0x133      // 307
 const val BTN_Y = 0x134      // 308
 const val BTN_TL = 0x136     // 310 (LB)
 const val BTN_TR = 0x137     // 311 (RB)
 const val BTN_SELECT = 0x13a // 314 (Back)
 const val BTN_START = 0x13b  // 315 (Start)
 const val BTN_MODE = 0x13c   // 316 (Xbox button)
 const val BTN_THUMBL = 0x13d // 317 (Left stick press)
 const val BTN_THUMBR = 0x13e // 318 (Right stick press)
}

/**
 * evdev axis code constants (Linux input event codes)
 */
object EvdevAxisCodes {
 const val ABS_X = 0x00     // 0 (Left stick X)
 const val ABS_Y = 0x01     // 1 (Left stick Y)
 const val ABS_Z = 0x02     // 2 (Left trigger)
 const val ABS_RX = 0x03    // 3 (Right stick X)
 const val ABS_RY = 0x04    // 4 (Right stick Y)
 const val ABS_RZ = 0x05    // 5 (Right trigger)
 const val ABS_HAT0X = 0x10 // 16 (D-pad X)
 const val ABS_HAT0Y = 0x11 // 17 (D-pad Y)
}

/**
 * Input bridge configuration
 */
data class InputBridgeConfig(
 val preferredStrategy: InputBridgeStrategy = InputBridgeStrategy.INPUT_BRIDGE_APP,
 val autoLaunchInputBridge: Boolean = true,
 val showSetupGuideOnFirstLaunch: Boolean = true
)

/**
 * Input bridge strategy
 */
enum class InputBridgeStrategy {
 INPUT_BRIDGE_APP, // Strategy 3: App integration (current)
 NATIVE_UINPUT,  // Strategy 1: Native uinput (future)
 SDL_VIRTUAL,  // Strategy 2: SDL virtual joystick (experimental)
 NONE    // No bridge (direct pass-through, may not work)
}
