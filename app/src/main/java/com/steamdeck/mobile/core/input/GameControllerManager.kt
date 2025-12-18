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
 * ゲームコントローラー管理クラス
 *
 * Android InputDevice APIを使用してゲームコントローラーを管理:
 * - コントローラー接続/切断の監視
 * - ボタン入力とアナログスティック入力の処理
 * - 複数コントローラーのサポート
 * - Wineへの入力マッピング準備
 *
 * Best Practices:
 * - InputManager.InputDeviceListener でホットプラグ対応
 * - SOURCE_GAMEPAD と SOURCE_JOYSTICK の両方をサポート
 * - StateFlow で接続されたコントローラーを監視可能に
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
        // 既存のコントローラーを検出
        discoverControllers()

        // ホットプラグ監視を開始
        inputManager.registerInputDeviceListener(this, null)

        Log.i(TAG, "GameControllerManager initialized with ${_connectedControllers.value.size} controllers")
    }

    /**
     * 接続されているコントローラーを検出
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
     * デバイスがゲームコントローラーかどうかを判定
     */
    private fun isGameController(device: InputDevice?): Boolean {
        if (device == null) return false

        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    /**
     * GameController データクラスを生成
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
     * キー入力を処理
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

            // Face buttons (A/B/X/Y または Cross/Circle/Square/Triangle)
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

            else -> return false // 未対応のキー
        }

        _controllerState.value = _controllerState.value + (deviceId to updatedState)
        Log.v(TAG, "Controller $deviceId: KeyEvent ${event.keyCode} action=${event.action}")
        return true
    }

    /**
     * モーション入力（アナログスティック、トリガー）を処理
     */
    fun handleMotionEvent(deviceId: Int, event: MotionEvent): Boolean {
        if (!isControllerConnected(deviceId)) return false

        val currentState = _controllerState.value[deviceId] ?: ControllerState()

        // 左スティック
        val leftX = event.getAxisValue(MotionEvent.AXIS_X)
        val leftY = event.getAxisValue(MotionEvent.AXIS_Y)

        // 右スティック
        val rightX = event.getAxisValue(MotionEvent.AXIS_Z)
        val rightY = event.getAxisValue(MotionEvent.AXIS_RZ)

        // トリガー（L2/R2 アナログ）
        val triggerL = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val triggerR = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

        // ハットスイッチ（D-Pad アナログ）
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
     * コントローラーが接続されているかチェック
     */
    fun isControllerConnected(deviceId: Int): Boolean {
        return _connectedControllers.value.any { it.id == deviceId }
    }

    /**
     * デバイス追加時のコールバック
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
     * デバイス削除時のコールバック
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
     * デバイス変更時のコールバック
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
     * 特定コントローラーの現在の状態を取得
     */
    fun getControllerState(deviceId: Int): ControllerState? {
        return _controllerState.value[deviceId]
    }

    /**
     * コントローラーの振動（まだ未実装）
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
     * クリーンアップ
     */
    fun cleanup() {
        inputManager.unregisterInputDeviceListener(this)
        Log.i(TAG, "GameControllerManager cleanup complete")
    }
}

/**
 * ゲームコントローラー情報
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
 * コントローラーの入力状態
 *
 * すべてのボタンとアナログ入力を保持
 */
data class ControllerState(
    // D-Pad (デジタル)
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

    // Thumb buttons (スティック押し込み)
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

    // Hat switch (D-Pad アナログ, -1.0 to 1.0)
    val hatX: Float = 0f,
    val hatY: Float = 0f
)
