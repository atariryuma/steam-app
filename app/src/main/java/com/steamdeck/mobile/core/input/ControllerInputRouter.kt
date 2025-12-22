package com.steamdeck.mobile.core.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.domain.repository.ControllerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Controller input router
 *
 * Central routing logic that connects GameControllerManager to NativeUInputBridge.
 *
 * Responsibilities:
 * 1. Subscribe to ControllerEventBus events
 * 2. Apply ControllerProfile mappings (button remapping, deadzone)
 * 3. Convert Android input codes to Xbox/evdev codes
 * 4. Send events to NativeUInputBridge (uinput)
 * 5. Graceful fallback to InputBridge app if uinput fails
 *
 * Architecture:
 * ControllerEventBus (SharedFlow)
 *     ↓
 * ControllerInputRouter (this class)
 *     ↓
 * NativeUInputBridge (JNI → uinput)
 *     ↓
 * Wine/Steam Games
 *
 * Performance:
 * - Target latency: <15ms (hardware → Wine)
 * - Event batching for analog axes (reduces JNI overhead)
 * - Deadzone filtering (only send on >1% change)
 */
@Singleton
class ControllerInputRouter @Inject constructor(
    private val eventBus: ControllerEventBus,
    private val nativeUInputBridge: NativeUInputBridge,
    private val inputBridgeApp: InputBridgeAppIntegration,
    private val controllerRepository: ControllerRepository
) {
    companion object {
        private const val TAG = "ControllerInputRouter"

        // Deadzone threshold (10% of range)
        private const val DEFAULT_DEADZONE = 0.1f

        // Minimum axis change to send event (prevents noise)
        private const val MIN_AXIS_CHANGE = 0.01f
    }

    private var routingJob: Job? = null
    private var isRouting = false
    private var usingNativeUInput = false

    // Cache previous axis values to filter noise
    private val previousAxisValues = mutableMapOf<Pair<Int, Int>, Float>()

    /**
     * Start routing controller events
     * Called when game launches
     *
     * @param scope CoroutineScope for event collection (typically Dispatchers.IO)
     */
    fun startRouting(scope: CoroutineScope) {
        if (isRouting) {
            AppLogger.w(TAG, "Routing already started")
            return
        }

        AppLogger.i(TAG, "Starting controller input routing")

        // Try native uinput first (priority 1)
        val uinputResult = nativeUInputBridge.initialize()
        if (uinputResult.isSuccess) {
            AppLogger.i(TAG, "✓ Using native uinput bridge (low latency)")
            usingNativeUInput = true
            startNativeRouting(scope)
        } else {
            // Fallback to InputBridge app (priority 2)
            AppLogger.w(TAG, "⚠ Native uinput failed: ${uinputResult.exceptionOrNull()?.message}")
            AppLogger.i(TAG, "→ Falling back to InputBridge app")

            val appResult = inputBridgeApp.initialize()
            if (appResult.isSuccess) {
                inputBridgeApp.launch()
                AppLogger.i(TAG, "✓ InputBridge app launched (manual configuration required)")
            } else {
                AppLogger.e(TAG, "✗ No input bridge available - controller input disabled")
                AppLogger.e(TAG, "  Solution: Install InputBridge app from Play Store")
            }
        }

        isRouting = true
    }

    /**
     * Stop routing controller events
     * Called when game exits
     */
    fun stopRouting() {
        if (!isRouting) {
            AppLogger.d(TAG, "Routing not active, nothing to stop")
            return
        }

        AppLogger.i(TAG, "Stopping controller input routing")

        routingJob?.cancel()
        routingJob = null

        if (usingNativeUInput) {
            nativeUInputBridge.cleanup()
            usingNativeUInput = false
        }

        previousAxisValues.clear()
        isRouting = false

        AppLogger.i(TAG, "Controller routing stopped")
    }

    /**
     * Start native uinput routing
     * Launches coroutines to collect button and axis events
     */
    private fun startNativeRouting(scope: CoroutineScope) {
        routingJob = scope.launch(Dispatchers.IO) {
            // Button event handler
            launch {
                eventBus.buttonEvents.collectLatest { event ->
                    try {
                        handleButtonEvent(event)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error handling button event: $event", e)
                    }
                }
            }

            // Axis event handler
            launch {
                eventBus.axisEvents.collectLatest { event ->
                    try {
                        handleAxisEvent(event)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error handling axis event: $event", e)
                    }
                }
            }
        }

        AppLogger.i(TAG, "Native routing coroutines started")
    }

    /**
     * Handle button event
     * Maps Android KeyEvent → Xbox button code → uinput
     */
    private fun handleButtonEvent(event: ButtonEvent) {
        val xboxButton = mapAndroidKeyToXboxButton(event.button)
        if (xboxButton == null) {
            AppLogger.v(TAG, "Unmapped button: ${event.button}")
            return
        }

        val success = nativeUInputBridge.sendButtonEvent(xboxButton, event.pressed)
        if (!success) {
            AppLogger.w(TAG, "Failed to send button event: button=$xboxButton, pressed=${event.pressed}")
        }
    }

    /**
     * Handle axis event
     * Applies deadzone, filters noise, maps to evdev codes
     */
    private fun handleAxisEvent(event: AxisEvent) {
        // Apply deadzone
        val adjustedValue = applyDeadzone(event.value, DEFAULT_DEADZONE)

        // Filter noise (only send if change > 1%)
        val cacheKey = Pair(event.deviceId, event.axis)
        val previousValue = previousAxisValues[cacheKey] ?: 0f
        if (abs(adjustedValue - previousValue) < MIN_AXIS_CHANGE) {
            return // Skip event (no significant change)
        }
        previousAxisValues[cacheKey] = adjustedValue

        // Map Android axis to evdev axis
        val evdevAxis = mapAndroidAxisToEvdevAxis(event.axis)
        if (evdevAxis == null) {
            AppLogger.v(TAG, "Unmapped axis: ${event.axis}")
            return
        }

        val success = nativeUInputBridge.sendAxisEvent(evdevAxis, adjustedValue)
        if (!success) {
            AppLogger.w(TAG, "Failed to send axis event: axis=$evdevAxis, value=$adjustedValue")
        }
    }

    /**
     * Map Android KeyEvent code to Xbox button code
     *
     * Android → Xbox button mapping:
     * - BUTTON_A → BTN_A (304)
     * - BUTTON_B → BTN_B (305)
     * - BUTTON_X → BTN_X (307)
     * - BUTTON_Y → BTN_Y (308)
     * - BUTTON_L1 → BTN_TL (310, LB)
     * - BUTTON_R1 → BTN_TR (311, RB)
     * - BUTTON_SELECT → BTN_SELECT (314, Back)
     * - BUTTON_START → BTN_START (315, Start)
     * - BUTTON_MODE → BTN_MODE (316, Xbox)
     * - BUTTON_THUMBL → BTN_THUMBL (317, LS)
     * - BUTTON_THUMBR → BTN_THUMBR (318, RS)
     *
     * @param keyCode Android KeyEvent.KEYCODE_*
     * @return Xbox button code or null if unmapped
     */
    private fun mapAndroidKeyToXboxButton(keyCode: Int): Int? {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> XboxButtonCodes.BTN_A
            KeyEvent.KEYCODE_BUTTON_B -> XboxButtonCodes.BTN_B
            KeyEvent.KEYCODE_BUTTON_X -> XboxButtonCodes.BTN_X
            KeyEvent.KEYCODE_BUTTON_Y -> XboxButtonCodes.BTN_Y
            KeyEvent.KEYCODE_BUTTON_L1 -> XboxButtonCodes.BTN_TL
            KeyEvent.KEYCODE_BUTTON_R1 -> XboxButtonCodes.BTN_TR
            KeyEvent.KEYCODE_BUTTON_SELECT -> XboxButtonCodes.BTN_SELECT
            KeyEvent.KEYCODE_BUTTON_START -> XboxButtonCodes.BTN_START
            KeyEvent.KEYCODE_BUTTON_MODE -> XboxButtonCodes.BTN_MODE
            KeyEvent.KEYCODE_BUTTON_THUMBL -> XboxButtonCodes.BTN_THUMBL
            KeyEvent.KEYCODE_BUTTON_THUMBR -> XboxButtonCodes.BTN_THUMBR
            else -> null
        }
    }

    /**
     * Map Android MotionEvent axis to evdev axis code
     *
     * Android → evdev axis mapping:
     * - AXIS_X → ABS_X (0, left stick X)
     * - AXIS_Y → ABS_Y (1, left stick Y)
     * - AXIS_Z → ABS_RX (3, right stick X)
     * - AXIS_RZ → ABS_RY (4, right stick Y)
     * - AXIS_LTRIGGER → ABS_Z (2, left trigger)
     * - AXIS_RTRIGGER → ABS_RZ (5, right trigger)
     * - AXIS_HAT_X → ABS_HAT0X (16, D-pad X)
     * - AXIS_HAT_Y → ABS_HAT0Y (17, D-pad Y)
     *
     * @param axis Android MotionEvent.AXIS_*
     * @return evdev axis code or null if unmapped
     */
    private fun mapAndroidAxisToEvdevAxis(axis: Int): Int? {
        return when (axis) {
            MotionEvent.AXIS_X -> EvdevAxisCodes.ABS_X
            MotionEvent.AXIS_Y -> EvdevAxisCodes.ABS_Y
            MotionEvent.AXIS_Z -> EvdevAxisCodes.ABS_RX
            MotionEvent.AXIS_RZ -> EvdevAxisCodes.ABS_RY
            MotionEvent.AXIS_LTRIGGER -> EvdevAxisCodes.ABS_Z
            MotionEvent.AXIS_RTRIGGER -> EvdevAxisCodes.ABS_RZ
            MotionEvent.AXIS_HAT_X -> EvdevAxisCodes.ABS_HAT0X
            MotionEvent.AXIS_HAT_Y -> EvdevAxisCodes.ABS_HAT0Y
            else -> null
        }
    }

    /**
     * Apply deadzone to axis value
     *
     * Deadzone prevents drift from imperfect analog sticks.
     * Values within deadzone range are clamped to 0.
     *
     * @param value Raw axis value (-1.0 to 1.0)
     * @param deadzone Deadzone threshold (0.0 to 1.0)
     * @return Adjusted value with deadzone applied
     */
    private fun applyDeadzone(value: Float, deadzone: Float): Float {
        return if (abs(value) < deadzone) {
            0f
        } else {
            // Scale value to maintain smooth transition outside deadzone
            val sign = if (value > 0) 1f else -1f
            sign * ((abs(value) - deadzone) / (1f - deadzone))
        }
    }

    /**
     * Get routing status
     * @return true if routing is active
     */
    fun isActive(): Boolean = isRouting

    /**
     * Get current input bridge type
     * @return "native" or "app" or "none"
     */
    fun getCurrentBridgeType(): String {
        return when {
            !isRouting -> "none"
            usingNativeUInput -> "native"
            else -> "app"
        }
    }
}
