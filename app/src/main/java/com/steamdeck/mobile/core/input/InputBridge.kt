package com.steamdeck.mobile.core.input

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wine入力マッピングブリッジ
 *
 * Research findings実装:
 * - 戦略1: Native uinput injection (将来実装)
 * - 戦略2: SDL event injection (実験的)
 * - 戦略3: InputBridge app integration (現在の実装) ✅
 *
 * InputBridge アプリとの統合により、Android入力をWine/Protonゲームに
 * DirectInput/XInput形式で送信します。
 *
 * Best Practices:
 * - Zero root requirement
 * - User-friendly configuration
 * - Works with Mobox/Winlator pattern
 */
interface InputBridge {
    /**
     * 初期化処理
     */
    fun initialize(): Result<Unit>

    /**
     * InputBridgeがインストールされているか確認
     */
    fun isInstalled(): Boolean

    /**
     * InputBridgeアプリを起動
     */
    fun launch(): Result<Unit>

    /**
     * クリーンアップ
     */
    fun cleanup()
}

/**
 * InputBridge アプリ統合実装
 *
 * InputBridge app: https://inputbridge.net/
 * - 仮想ゲームパッド作成
 * - ユーザーフレンドリーなボタンマッピングUI
 * - Wine/Protonゲームで自動認識
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
                Log.i(TAG, "InputBridge detected: $installedPackage")
                Result.success(Unit)
            } else {
                Log.w(TAG, "InputBridge not installed")
                Result.failure(InputBridgeNotInstalledException())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize InputBridge", e)
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
                Log.i(TAG, "Launched InputBridge: $packageName")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to create launch intent for $packageName"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch InputBridge", e)
            Result.failure(e)
        }
    }

    override fun cleanup() {
        // No cleanup needed for app integration
        Log.d(TAG, "InputBridge cleanup (no-op)")
    }

    /**
     * InputBridgeパッケージを検出
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
     * InputBridge設定ガイドを表示（将来実装）
     */
    fun showSetupGuide(): String {
        return """
            ## InputBridge セットアップガイド

            1. **インストール**
               - Google Playストアから「InputBridge」をインストール
               - または APK: https://inputbridge.net/download

            2. **設定**
               - InputBridgeアプリを開く
               - 「Create Virtual Controller」をタップ
               - コントローラータイプを選択（Xbox 360推奨）

            3. **ゲームでの使用**
               - SteamDeck Mobileでゲームを起動
               - InputBridgeが自動的にコントローラーを検出
               - ゲーム内設定でボタンマッピングを確認

            ## トラブルシューティング

            - コントローラーが認識されない場合
              → InputBridgeアプリでコントローラーを再作成

            - ボタンが反応しない場合
              → ゲーム内設定でコントローラーを再検出

            - Wine設定での確認
              → `wine control joy.cpl` でコントローラーテスト
        """.trimIndent()
    }
}

/**
 * Native uinput実装（将来実装）
 *
 * Research findings:
 * - /dev/uinput経由で仮想Xbox 360コントローラー作成
 * - rootまたはinputグループ権限必要
 * - 最低レイテンシ、Wine完全互換
 *
 * TODO: NDK native library実装
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
                Log.w(TAG, "Native library not available: ${e.message}")
            }
        }
    }

    // Native methods (未実装)
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
            android.view.MotionEvent.AXIS_X -> 0x00  // ABS_X
            android.view.MotionEvent.AXIS_Y -> 0x01  // ABS_Y
            android.view.MotionEvent.AXIS_Z -> 0x03  // ABS_RX
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
 * カスタム例外: InputBridge未インストール
 */
class InputBridgeNotInstalledException : Exception(
    "InputBridge app not installed. Please install from Google Play or https://inputbridge.net/"
)

/**
 * 入力ブリッジ設定
 */
data class InputBridgeConfig(
    val preferredStrategy: InputBridgeStrategy = InputBridgeStrategy.INPUT_BRIDGE_APP,
    val autoLaunchInputBridge: Boolean = true,
    val showSetupGuideOnFirstLaunch: Boolean = true
)

/**
 * 入力ブリッジ戦略
 */
enum class InputBridgeStrategy {
    INPUT_BRIDGE_APP,  // Strategy 3: App integration (current)
    NATIVE_UINPUT,     // Strategy 1: Native uinput (future)
    SDL_VIRTUAL,       // Strategy 2: SDL virtual joystick (experimental)
    NONE               // No bridge (direct pass-through, may not work)
}
