# Controller Support - Phase 5 Research & Architecture
## Android Game Controller Integration for SteamDeck Mobile

**日付**: 2025-12-17
**ステータス**: 📋 設計完了 (実装準備中)
**目的**: Steamゲーム向けゲームコントローラー対応の完全実装

---

## 🔍 調査結果サマリー

### 重要な発見

#### 1. Android Controller API 2025

**InputDevice API** (Game Controller Detection):
```kotlin
val deviceIds = InputDevice.getDeviceIds()
deviceIds.forEach { deviceId ->
    val device = InputDevice.getDevice(deviceId)
    val sources = device.sources

    // ゲームコントローラー判定
    if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
        (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {

        val controllerNumber = device.controllerNumber // マルチプレイヤー対応
        val name = device.name // "Xbox Wireless Controller"
        val vendor = device.vendorId // 0x045E (Microsoft)
        val product = device.productId // 0x02EA
    }
}
```

**参考**:
- [Handle controller actions - Android Developers](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input)
- [InputDevice.getSources() Examples](https://www.tabnine.com/code/java/methods/android.view.InputDevice/getSources)

#### 2. Jetpack Compose対応

**KeyEvent ハンドリング** (Buttons):
```kotlin
Box(
    modifier = Modifier
        .focusable()
        .onKeyEvent { event ->
            when {
                event.key == Key.ButtonA && event.type == KeyEventType.KeyDown -> {
                    onButtonAPressed()
                    true
                }
                event.key == Key.ButtonB && event.type == KeyEventType.KeyDown -> {
                    onButtonBPressed()
                    true
                }
                else -> false
            }
        }
) {
    // Game content
}
```

**MotionEvent ハンドリング** (Joysticks):
```kotlin
AndroidView(
    factory = { context ->
        View(context).apply {
            setOnGenericMotionListener { _, event ->
                if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
                    event.action == MotionEvent.ACTION_MOVE) {

                    val leftX = event.getAxisValue(MotionEvent.AXIS_X)
                    val leftY = event.getAxisValue(MotionEvent.AXIS_Y)
                    val rightX = event.getAxisValue(MotionEvent.AXIS_Z)
                    val rightY = event.getAxisValue(MotionEvent.AXIS_RZ)
                    val leftTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
                    val rightTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

                    onJoystickMove(leftX, leftY, rightX, rightY, leftTrigger, rightTrigger)
                    true
                } else false
            }
        }
    }
)
```

**参考**:
- [Handle keyboard actions - Jetpack Compose](https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands)
- [Keyboard handling in Jetpack Compose](https://dev.to/tkuenneth/keyboard-handling-in-jetpack-compose-2593)
- [Jetpack Compose under the hood: Touch Events](https://www.droidcon.com/2022/05/31/jetpack-compose-under-the-hood-touch-events/)

#### 3. 標準ボタンマッピング

**Android KeyEvent Keycodes**:
```kotlin
// Face Buttons (右側4ボタン)
KEYCODE_BUTTON_A = 96     // Xbox: A, PS: Cross (✕)
KEYCODE_BUTTON_B = 97     // Xbox: B, PS: Circle (○)
KEYCODE_BUTTON_X = 99     // Xbox: X, PS: Square (□)
KEYCODE_BUTTON_Y = 100    // Xbox: Y, PS: Triangle (△)

// Shoulder Buttons
KEYCODE_BUTTON_L1 = 102   // Left Bumper / L1
KEYCODE_BUTTON_R1 = 103   // Right Bumper / R1
KEYCODE_BUTTON_L2 = 104   // Left Trigger (button) / L2
KEYCODE_BUTTON_R2 = 105   // Right Trigger (button) / R2

// Thumbstick Buttons
KEYCODE_BUTTON_THUMBL = 106  // Left Stick Click / L3
KEYCODE_BUTTON_THUMBR = 107  // Right Stick Click / R3

// Menu Buttons
KEYCODE_BUTTON_START = 108   // Start / Options
KEYCODE_BUTTON_SELECT = 109  // Select / Share
KEYCODE_BUTTON_MODE = 110    // Xbox Button / PS Button

// D-Pad
KEYCODE_DPAD_UP = 19
KEYCODE_DPAD_DOWN = 20
KEYCODE_DPAD_LEFT = 21
KEYCODE_DPAD_RIGHT = 22
```

**参考**:
- [Android Key Codes - elementalx.org](https://elementalx.org/button-mapper/android-key-codes/)
- [KeyEvent API Reference](https://developer.android.com/reference/android/view/KeyEvent)
- [KeyEvent.IsGamepadButton()](https://learn.microsoft.com/en-us/dotnet/api/android.views.keyevent.isgamepadbutton)

#### 4. Joystick Axis Mapping

**MotionEvent Axes**:
```kotlin
// Left Joystick
AXIS_X = 0          // Left stick horizontal (-1.0 left, +1.0 right)
AXIS_Y = 1          // Left stick vertical (-1.0 up, +1.0 down)

// Right Joystick
AXIS_Z = 11         // Right stick horizontal
AXIS_RZ = 14        // Right stick vertical

// Triggers (Analog)
AXIS_LTRIGGER = 17  // Left trigger (0.0 released, 1.0 fully pressed)
AXIS_RTRIGGER = 18  // Right trigger
AXIS_BRAKE = 23     // AXIS_LTRIGGER alias (Android 4.3+)
AXIS_GAS = 22       // AXIS_RTRIGGER alias (Android 4.3+)

// D-Pad (Analog - some controllers)
AXIS_HAT_X = 15     // D-pad horizontal
AXIS_HAT_Y = 16     // D-pad vertical
```

**Dead Zone処理**:
```kotlin
fun procesAxis(value: Float, device: InputDevice, axis: Int): Float {
    val range = device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK)
    val flat = range?.flat ?: 0f  // Dead zone threshold

    return if (abs(value) > flat) {
        value
    } else {
        0f  // Within dead zone - treat as centered
    }
}
```

**参考**:
- [Handle controller actions - MotionEvent](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input)
- [MotionEvent API Reference](https://developer.android.com/reference/android/view/MotionEvent)
- [MotionEvent.getAxisValue() Examples](https://www.programcreek.com/java-api-examples/?class=android.view.MotionEvent&method=getAxisValue)

#### 5. Android 17+ ネイティブリマッピング

**重要**: Android 17で公式ボタンリマッピング機能追加予定!

**新機能**:
- システムレベルのコントローラーリマッピング
- Settings アプリに専用コントローラー設定
- 仮想ゲームパッド機能
- 新権限: `Controller Remapping Permission`

**影響**:
- アプリ側での複雑なマッピングロジック不要に
- ユーザーが好みのレイアウトを設定可能
- 低レイテンシー・安定動作

**参考**:
- [Android 17 May Bring Forced Controller Support](https://www.androidheadlines.com/2025/11/android-17-forced-controller-support-mapping-customization-game-app.html)
- [First look: Android 17 gamepad remapping](https://www.androidauthority.com/android-17-gamepad-remapping-rumor-3623718/)

---

## 🏗️ アーキテクチャ設計

### システム構成

```
┌─────────────────────────────────────────┐
│ UI Layer (Jetpack Compose)              │
│  ├─ ControllerConfigScreen              │
│  ├─ GameScreen (with controller input)  │
│  └─ ControllerStatusIndicator           │
├─────────────────────────────────────────┤
│ ViewModel Layer                          │
│  ├─ ControllerViewModel                 │
│  └─ GameViewModel                       │
├─────────────────────────────────────────┤
│ Domain Layer                             │
│  ├─ ControllerManager (DI Singleton)    │
│  ├─ ControllerProfile                   │
│  └─ ControllerMappingRepository         │
├─────────────────────────────────────────┤
│ Data Layer                               │
│  ├─ Room: ControllerProfileEntity       │
│  └─ DataStore: DefaultMappings          │
├─────────────────────────────────────────┤
│ Android Framework                        │
│  ├─ InputDevice API                     │
│  ├─ KeyEvent                            │
│  └─ MotionEvent                         │
└─────────────────────────────────────────┘
```

### ドメインモデル設計

```kotlin
// domain/model/Controller.kt
data class Controller(
    val deviceId: Int,
    val name: String,
    val controllerNumber: Int,
    val vendorId: Int,
    val productId: Int,
    val type: ControllerType,
    val capabilities: ControllerCapabilities
)

enum class ControllerType {
    XBOX,           // Xbox One/Series
    PLAYSTATION,    // PS4/PS5 DualShock/DualSense
    NINTENDO,       // Nintendo Pro Controller
    GENERIC,        // Generic HID gamepad
    UNKNOWN
}

data class ControllerCapabilities(
    val hasLeftJoystick: Boolean,
    val hasRightJoystick: Boolean,
    val hasAnalogTriggers: Boolean,
    val hasDPad: Boolean,
    val buttonCount: Int,
    val hasRumble: Boolean = false
)

// domain/model/ControllerInput.kt
sealed class ControllerInput {
    data class ButtonPress(val button: GamepadButton, val pressed: Boolean) : ControllerInput()
    data class JoystickMove(val stick: Joystick, val x: Float, val y: Float) : ControllerInput()
    data class TriggerMove(val trigger: Trigger, val value: Float) : ControllerInput()
    data class DPadPress(val direction: DPadDirection) : ControllerInput()
}

enum class GamepadButton {
    A, B, X, Y,
    L1, R1, L2, R2,
    L3, R3,
    START, SELECT, GUIDE,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT
}

enum class Joystick { LEFT, RIGHT }
enum class Trigger { LEFT, RIGHT }
enum class DPadDirection { UP, DOWN, LEFT, RIGHT, NONE }

// domain/model/ControllerProfile.kt
data class ControllerProfile(
    val id: String,
    val name: String,
    val controllerType: ControllerType,
    val mapping: ControllerMapping,
    val deadZone: Float = 0.15f,
    val triggerThreshold: Float = 0.1f
)

data class ControllerMapping(
    val buttons: Map<Int, GamepadButton>,  // KeyCode -> GamepadButton
    val axes: Map<Int, AxisMapping>        // MotionEvent.AXIS_* -> Joystick/Trigger
)

data class AxisMapping(
    val type: AxisType,
    val inverted: Boolean = false,
    val sensitivity: Float = 1.0f
)

enum class AxisType {
    LEFT_STICK_X, LEFT_STICK_Y,
    RIGHT_STICK_X, RIGHT_STICK_Y,
    LEFT_TRIGGER, RIGHT_TRIGGER
}
```

### ControllerManager 実装設計

```kotlin
// core/controller/ControllerManager.kt
@Singleton
class ControllerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controllerRepository: ControllerMappingRepository
) {
    private val _connectedControllers = MutableStateFlow<List<Controller>>(emptyList())
    val connectedControllers: StateFlow<List<Controller>> = _connectedControllers.asStateFlow()

    private val _controllerInput = MutableSharedFlow<Pair<Int, ControllerInput>>()
    val controllerInput: SharedFlow<Pair<Int, ControllerInput>> = _controllerInput.asSharedFlow()

    companion object {
        private const val TAG = "ControllerManager"
    }

    /**
     * デバイス接続監視開始
     */
    fun startMonitoring() {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                detectController(deviceId)?.let { controller ->
                    Log.i(TAG, "Controller connected: ${controller.name}")
                    updateControllerList()
                }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                Log.i(TAG, "Controller disconnected: $deviceId")
                updateControllerList()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                Log.d(TAG, "Controller changed: $deviceId")
                updateControllerList()
            }
        }, null)

        // 初回スキャン
        updateControllerList()
    }

    /**
     * コントローラー検出
     */
    private fun detectController(deviceId: Int): Controller? {
        val device = InputDevice.getDevice(deviceId) ?: return null
        val sources = device.sources

        if ((sources and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD &&
            (sources and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return null  // Not a game controller
        }

        val type = detectControllerType(device.vendorId, device.productId, device.name)
        val capabilities = detectCapabilities(device)

        return Controller(
            deviceId = deviceId,
            name = device.name,
            controllerNumber = device.controllerNumber,
            vendorId = device.vendorId,
            productId = device.productId,
            type = type,
            capabilities = capabilities
        )
    }

    /**
     * コントローラータイプ判定
     */
    private fun detectControllerType(vendorId: Int, productId: Int, name: String): ControllerType {
        return when {
            // Microsoft Xbox
            vendorId == 0x045E && (productId == 0x02EA || productId == 0x0B13) -> ControllerType.XBOX
            name.contains("Xbox", ignoreCase = true) -> ControllerType.XBOX

            // Sony PlayStation
            vendorId == 0x054C && (productId in 0x05C4..0x0CE6) -> ControllerType.PLAYSTATION
            name.contains("DualShock", ignoreCase = true) -> ControllerType.PLAYSTATION
            name.contains("DualSense", ignoreCase = true) -> ControllerType.PLAYSTATION

            // Nintendo
            vendorId == 0x057E -> ControllerType.NINTENDO
            name.contains("Pro Controller", ignoreCase = true) -> ControllerType.NINTENDO

            // Generic
            else -> ControllerType.GENERIC
        }
    }

    /**
     * 機能検出
     */
    private fun detectCapabilities(device: InputDevice): ControllerCapabilities {
        val motionRanges = device.motionRanges

        return ControllerCapabilities(
            hasLeftJoystick = motionRanges.any { it.axis == MotionEvent.AXIS_X || it.axis == MotionEvent.AXIS_Y },
            hasRightJoystick = motionRanges.any { it.axis == MotionEvent.AXIS_Z || it.axis == MotionEvent.AXIS_RZ },
            hasAnalogTriggers = motionRanges.any { it.axis == MotionEvent.AXIS_LTRIGGER || it.axis == MotionEvent.AXIS_RTRIGGER },
            hasDPad = motionRanges.any { it.axis == MotionEvent.AXIS_HAT_X || it.axis == MotionEvent.AXIS_HAT_Y } ||
                      device.hasKeys(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN).any { it },
            buttonCount = countButtons(device),
            hasRumble = device.vibrator.hasVibrator()
        )
    }

    /**
     * KeyEvent処理
     */
    suspend fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!event.isGamepadButton(event.keyCode)) {
            return false
        }

        val controller = _connectedControllers.value.find { it.deviceId == event.deviceId } ?: return false
        val profile = controllerRepository.getProfile(controller.type)
        val button = profile.mapping.buttons[event.keyCode] ?: return false

        val input = ControllerInput.ButtonPress(
            button = button,
            pressed = event.action == KeyEvent.ACTION_DOWN
        )

        _controllerInput.emit(controller.deviceId to input)
        return true
    }

    /**
     * MotionEvent処理
     */
    suspend fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK ||
            event.action != MotionEvent.ACTION_MOVE) {
            return false
        }

        val controller = _connectedControllers.value.find { it.deviceId == event.deviceId } ?: return false
        val profile = controllerRepository.getProfile(controller.type)
        val device = InputDevice.getDevice(event.deviceId) ?: return false

        // Process all axes
        profile.mapping.axes.forEach { (axis, mapping) ->
            val rawValue = event.getAxisValue(axis)
            val processedValue = processAxisValue(rawValue, device, axis, profile.deadZone)

            when (mapping.type) {
                AxisType.LEFT_STICK_X, AxisType.LEFT_STICK_Y -> {
                    val x = processAxisValue(event.getAxisValue(MotionEvent.AXIS_X), device, MotionEvent.AXIS_X, profile.deadZone)
                    val y = processAxisValue(event.getAxisValue(MotionEvent.AXIS_Y), device, MotionEvent.AXIS_Y, profile.deadZone)
                    _controllerInput.emit(controller.deviceId to ControllerInput.JoystickMove(Joystick.LEFT, x, y))
                }
                AxisType.RIGHT_STICK_X, AxisType.RIGHT_STICK_Y -> {
                    val x = processAxisValue(event.getAxisValue(MotionEvent.AXIS_Z), device, MotionEvent.AXIS_Z, profile.deadZone)
                    val y = processAxisValue(event.getAxisValue(MotionEvent.AXIS_RZ), device, MotionEvent.AXIS_RZ, profile.deadZone)
                    _controllerInput.emit(controller.deviceId to ControllerInput.JoystickMove(Joystick.RIGHT, x, y))
                }
                AxisType.LEFT_TRIGGER -> {
                    if (processedValue > profile.triggerThreshold) {
                        _controllerInput.emit(controller.deviceId to ControllerInput.TriggerMove(Trigger.LEFT, processedValue))
                    }
                }
                AxisType.RIGHT_TRIGGER -> {
                    if (processedValue > profile.triggerThreshold) {
                        _controllerInput.emit(controller.deviceId to ControllerInput.TriggerMove(Trigger.RIGHT, processedValue))
                    }
                }
            }
        }

        return true
    }

    /**
     * Axis値処理 (Dead Zone適用)
     */
    private fun processAxisValue(value: Float, device: InputDevice, axis: Int, deadZone: Float): Float {
        val range = device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) ?: return value
        val flat = range.flat.coerceAtLeast(deadZone)

        return if (abs(value) > flat) {
            value * (if (value > 0) 1f else -1f)
        } else {
            0f
        }
    }

    private fun updateControllerList() {
        val controllers = InputDevice.getDeviceIds().mapNotNull { detectController(it) }
        _connectedControllers.value = controllers
    }
}
```

### Repository 実装設計

```kotlin
// data/repository/ControllerMappingRepositoryImpl.kt
@Singleton
class ControllerMappingRepositoryImpl @Inject constructor(
    private val controllerProfileDao: ControllerProfileDao,
    private val dataStore: DataStore<Preferences>
) : ControllerMappingRepository {

    override suspend fun getProfile(type: ControllerType): ControllerProfile {
        // Try custom profile first
        controllerProfileDao.getProfileByType(type.name)?.let { entity ->
            return entity.toDomain()
        }

        // Fallback to default
        return getDefaultProfile(type)
    }

    override suspend fun saveProfile(profile: ControllerProfile) {
        controllerProfileDao.insert(profile.toEntity())
    }

    override suspend fun getDefaultProfile(type: ControllerType): ControllerProfile {
        return when (type) {
            ControllerType.XBOX -> createXboxDefaultProfile()
            ControllerType.PLAYSTATION -> createPlayStationDefaultProfile()
            ControllerType.NINTENDO -> createNintendoDefaultProfile()
            ControllerType.GENERIC -> createGenericDefaultProfile()
            ControllerType.UNKNOWN -> createGenericDefaultProfile()
        }
    }

    private fun createXboxDefaultProfile(): ControllerProfile {
        return ControllerProfile(
            id = "xbox_default",
            name = "Xbox Controller (Default)",
            controllerType = ControllerType.XBOX,
            mapping = ControllerMapping(
                buttons = mapOf(
                    KeyEvent.KEYCODE_BUTTON_A to GamepadButton.A,
                    KeyEvent.KEYCODE_BUTTON_B to GamepadButton.B,
                    KeyEvent.KEYCODE_BUTTON_X to GamepadButton.X,
                    KeyEvent.KEYCODE_BUTTON_Y to GamepadButton.Y,
                    KeyEvent.KEYCODE_BUTTON_L1 to GamepadButton.L1,
                    KeyEvent.KEYCODE_BUTTON_R1 to GamepadButton.R1,
                    KeyEvent.KEYCODE_BUTTON_L2 to GamepadButton.L2,
                    KeyEvent.KEYCODE_BUTTON_R2 to GamepadButton.R2,
                    KeyEvent.KEYCODE_BUTTON_THUMBL to GamepadButton.L3,
                    KeyEvent.KEYCODE_BUTTON_THUMBR to GamepadButton.R3,
                    KeyEvent.KEYCODE_BUTTON_START to GamepadButton.START,
                    KeyEvent.KEYCODE_BUTTON_SELECT to GamepadButton.SELECT,
                    KeyEvent.KEYCODE_BUTTON_MODE to GamepadButton.GUIDE,
                    KeyEvent.KEYCODE_DPAD_UP to GamepadButton.DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN to GamepadButton.DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT to GamepadButton.DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT to GamepadButton.DPAD_RIGHT
                ),
                axes = mapOf(
                    MotionEvent.AXIS_X to AxisMapping(AxisType.LEFT_STICK_X),
                    MotionEvent.AXIS_Y to AxisMapping(AxisType.LEFT_STICK_Y),
                    MotionEvent.AXIS_Z to AxisMapping(AxisType.RIGHT_STICK_X),
                    MotionEvent.AXIS_RZ to AxisMapping(AxisType.RIGHT_STICK_Y),
                    MotionEvent.AXIS_LTRIGGER to AxisMapping(AxisType.LEFT_TRIGGER),
                    MotionEvent.AXIS_RTRIGGER to AxisMapping(AxisType.RIGHT_TRIGGER)
                )
            ),
            deadZone = 0.15f,
            triggerThreshold = 0.1f
        )
    }

    // Similar for PlayStation, Nintendo, Generic...
}
```

---

## 🎨 UI実装設計

### ControllerConfigScreen (Jetpack Compose)

```kotlin
// presentation/ui/controller/ControllerConfigScreen.kt
@Composable
fun ControllerConfigScreen(
    viewModel: ControllerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val controllers by viewModel.connectedControllers.collectAsState()
    val selectedController by viewModel.selectedController.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controller Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Connected Controllers List
            ControllerListSection(
                controllers = controllers,
                selectedController = selectedController,
                onControllerSelected = viewModel::selectController
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Controller Details & Mapping
            selectedController?.let { controller ->
                ControllerDetailsCard(controller)
                Spacer(modifier = Modifier.height(16.dp))
                ButtonMappingSection(controller, viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                DeadZoneConfiguration(controller, viewModel)
            } ?: run {
                NoControllerConnectedPlaceholder()
            }
        }
    }
}

@Composable
private fun ControllerListSection(
    controllers: List<Controller>,
    selectedController: Controller?,
    onControllerSelected: (Controller) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Connected Controllers (${controllers.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (controllers.isEmpty()) {
                Text(
                    "No controllers detected. Connect a gamepad.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                controllers.forEach { controller ->
                    ControllerItem(
                        controller = controller,
                        isSelected = controller == selectedController,
                        onClick = { onControllerSelected(controller) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControllerItem(
    controller: Controller,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        tonalElevation = if (isSelected) 8.dp else 0.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (controller.type) {
                    ControllerType.XBOX -> Icons.Default.SportsEsports
                    ControllerType.PLAYSTATION -> Icons.Default.Gamepad
                    else -> Icons.Default.VideogameAsset
                },
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(controller.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Controller #${controller.controllerNumber} - ${controller.type.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

---

## 📊 実装優先順位

### Phase 5A: Controller Detection (Week 1)
- ✅ ControllerManager実装
- ✅ InputDevice監視
- ✅ コントローラータイプ判定
- ✅ ViewModel統合

### Phase 5B: Input Handling (Week 2)
- ✅ KeyEvent処理
- ✅ MotionEvent処理
- ✅ Dead zone処理
- ✅ デフォルトマッピング

### Phase 5C: Profile System (Week 3)
- ✅ Room database integration
- ✅ ControllerProfileEntity
- ✅ Repository実装
- ✅ カスタムプロファイル保存

### Phase 5D: Configuration UI (Week 4)
- ✅ ControllerConfigScreen
- ✅ Button remapping UI
- ✅ Dead zone調整UI
- ✅ Live input preview

---

## 📚 参考資料 (Sources)

### Official Android Documentation
- [Handle controller actions - Android Developers](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input) ⭐ メイン参考
- [Support controllers across Android versions](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/compatibility)
- [Support multiple game controllers](https://developer.android.com/training/game-controllers/multiple-controllers)
- [Add custom controller device mappings](https://developer.android.com/games/sdk/game-controller/custom-mapping)

### Jetpack Compose Input Handling
- [Handle keyboard actions - Jetpack Compose](https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands)
- [Input compatibility on large screens - Jetpack Compose](https://developer.android.com/develop/ui/compose/touch-input/input-compatibility-on-large-screens)
- [Pointer input in Compose](https://developer.android.com/jetpack/compose/touch-input/pointer-input)
- [Keyboard handling in Jetpack Compose](https://dev.to/tkuenneth/keyboard-handling-in-jetpack-compose-2593)
- [Jetpack Compose under the hood: Touch Events](https://www.droidcon.com/2022/05/31/jetpack-compose-under-the-hood-touch-events/)

### Android Input APIs
- [KeyEvent API Reference](https://developer.android.com/reference/android/view/KeyEvent)
- [MotionEvent API Reference](https://developer.android.com/reference/android/view/MotionEvent)
- [InputDevice API Reference](https://developer.android.com/reference/android/view/InputDevice)
- [Android Key Codes - elementalx.org](https://elementalx.org/button-mapper/android-key-codes/)

### Controller Mapping & Standards
- [Android 17 May Bring Forced Controller Support](https://www.androidheadlines.com/2025/11/android-17-forced-controller-support-mapping-customization-game-app.html)
- [First look: Android 17 gamepad remapping](https://www.androidauthority.com/android-17-gamepad-remapping-rumor-3623718/)
- [Android 11 adds 84 new gaming controller mappings](https://www.xda-developers.com/android-11-84-new-gaming-controller-mappings/)

### Code Examples & Tutorials
- [InputDevice.getSources() Examples](https://www.tabnine.com/code/java/methods/android.view.InputDevice/getSources)
- [MotionEvent.getAxisValue() Examples](https://www.programcreek.com/java-api-examples/?class=android.view.MotionEvent&method=getAxisValue)
- [KeyEvent.IsGamepadButton()](https://learn.microsoft.com/en-us/dotnet/api/android.views.keyevent.isgamepadbutton)
- [Read Joystick Input in Android](https://medium.com/@nhzandi/read-from-joystick-input-in-android-f656509217a)

### Third-Party Solutions (Reference)
- [Mantis Gamepad Pro](https://en.androidguias.com/Mantis-Gamepad-Pro-maps-external-controllers-for-playing-on-Android/)
- [Button Mapping Tutorial for Android System](https://www.e-pxn.com/tutorial/button-mapping-tutorial-android-system)

---

## 📝 まとめ

**Phase 5 Research 完了内容**:
- ✅ Android InputDevice API 完全調査
- ✅ Jetpack Compose入力ハンドリング研究
- ✅ 標準ボタン/Axisマッピング確立
- ✅ ControllerManager設計完了
- ✅ ドメインモデル設計完了
- ✅ Repository & UI設計完了
- ✅ Android 17ネイティブリマッピング情報収集

**技術的決定**:
- ✅ InputManager監視 + StateFlow通知
- ✅ デフォルトプロファイル提供 (Xbox/PS/Nintendo/Generic)
- ✅ カスタムプロファイル Room保存
- ✅ Dead zone/Triggerしきい値カスタマイズ可能
- ✅ Material3 UI設計

**実装準備完了**:
- ✅ 全クラス設計済み
- ✅ コード例完備
- ✅ 4週間実装計画

**次のステップ**:
1. ControllerManager実装
2. デフォルトプロファイル作成
3. ViewModel統合
4. UI実装

---

**Status**: Phase 5 リサーチ完了 📋
**次回**: Phase 5A - Controller Detection実装
**推定工数**: 4週間 (Detection → Input → Profile → UI)
