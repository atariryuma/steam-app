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
    - Google Playストアfrom「InputBridge」Installation
    - or APK: https://inputbridge.net/download

   2. **configuration**
    - Open InputBridge app
    - Tap "Create Virtual Controller"
    - Select controller type (Xbox 360 recommended)

   3. **game use**
    - SteamDeck Mobile gamelaunch
    - InputBridge 自動的 controllerdetection
    - game内configuration ボタンマッピングconfirmation

   ## トラブルシューティング

   - controller 認識されないcase
    → InputBridgeアプリ controller再create

   - ボタン 反応しないcase
    → game内configuration controller再detection

   - Wineconfiguration confirmation
    → `wine control joy.cpl` controllerテスト
  """.trimIndent()
 }
}

/**
 * Native uinputimplementation（futureimplementation）
 *
 * Research findings:
 * - /dev/uinputvia仮想Xbox 360controllercreate
 * - rootorinputグループ権限必要
 * - 最低レイテンシ、Wine完全互換
 *
 * TODO: NDK native libraryimplementation
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
  init {
   try {
    System.loadLibrary("uinput_bridge")
   } catch (e: UnsatisfiedLinkError) {
    AppLogger.w(TAG, "Native library not available: ${e.message}")
   }
  }
 }

 // Native methods (not implemented)
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
  return Result.failure(UnsupportedOperationException("Native uinput not yet implemented"))
 }

 override fun isInstalled(): Boolean {
  return false // Not yet implemented
 }

 override fun launch(): Result<Unit> {
  return Result.failure(UnsupportedOperationException("Native uinput not yet implemented"))
 }

 override fun cleanup() {
  // nativeDestroy() when implemented
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
 * custom例外: InputBridge未Installation
 */
class InputBridgeNotInstalledException : Exception(
 "InputBridge app not installed. Please install from Google Play or https://inputbridge.net/"
)

/**
 * 入力ブリッジconfiguration
 */
data class InputBridgeConfig(
 val preferredStrategy: InputBridgeStrategy = InputBridgeStrategy.INPUT_BRIDGE_APP,
 val autoLaunchInputBridge: Boolean = true,
 val showSetupGuideOnFirstLaunch: Boolean = true
)

/**
 * 入力ブリッジstrategy
 */
enum class InputBridgeStrategy {
 INPUT_BRIDGE_APP, // Strategy 3: App integration (current)
 NATIVE_UINPUT,  // Strategy 1: Native uinput (future)
 SDL_VIRTUAL,  // Strategy 2: SDL virtual joystick (experimental)
 NONE    // No bridge (direct pass-through, may not work)
}
