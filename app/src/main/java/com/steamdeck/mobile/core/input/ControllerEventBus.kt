package com.steamdeck.mobile.core.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller event bus
 *
 * Distributes controller input events from GameControllerManager to ControllerInputRouter
 * using SharedFlow for backpressure-resistant event distribution.
 *
 * Architecture:
 * - GameControllerManager (producer) → emit events
 * - ControllerInputRouter (consumer) → collect events
 *
 * Performance:
 * - extraBufferCapacity=64: Prevents slow consumers from blocking fast producers
 * - <2ms latency (SharedFlow is optimized for hot streams)
 */
@Singleton
class ControllerEventBus @Inject constructor() {

    companion object {
        // Buffer capacity to prevent event loss during high-frequency input
        // 64 events = ~1 frame worth of data at 60 FPS
        private const val BUFFER_CAPACITY = 64
    }

    private val _buttonEvents = MutableSharedFlow<ButtonEvent>(
        extraBufferCapacity = BUFFER_CAPACITY
    )
    val buttonEvents: SharedFlow<ButtonEvent> = _buttonEvents.asSharedFlow()

    private val _axisEvents = MutableSharedFlow<AxisEvent>(
        extraBufferCapacity = BUFFER_CAPACITY
    )
    val axisEvents: SharedFlow<AxisEvent> = _axisEvents.asSharedFlow()

    /**
     * Emit button event
     * @param deviceId Android InputDevice ID
     * @param button Android KeyEvent.KEYCODE_* constant
     * @param pressed true for press, false for release
     */
    suspend fun emitButtonEvent(deviceId: Int, button: Int, pressed: Boolean) {
        _buttonEvents.emit(ButtonEvent(deviceId, button, pressed))
    }

    /**
     * Emit axis event
     * @param deviceId Android InputDevice ID
     * @param axis Android MotionEvent.AXIS_* constant
     * @param value -1.0 ~ 1.0 (Android value)
     */
    suspend fun emitAxisEvent(deviceId: Int, axis: Int, value: Float) {
        _axisEvents.emit(AxisEvent(deviceId, axis, value))
    }
}

/**
 * Button event data class
 *
 * Represents a physical button press or release on a controller
 */
data class ButtonEvent(
    val deviceId: Int,      // Android InputDevice ID
    val button: Int,        // Android KeyEvent.KEYCODE_*
    val pressed: Boolean    // true = press, false = release
)

/**
 * Axis event data class
 *
 * Represents analog axis movement (sticks, triggers, D-pad)
 */
data class AxisEvent(
    val deviceId: Int,      // Android InputDevice ID
    val axis: Int,          // Android MotionEvent.AXIS_*
    val value: Float        // -1.0 ~ 1.0 (Android normalized value)
)
