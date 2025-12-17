package com.steamdeck.mobile.core.winlator

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.domain.model.Box64Preset
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.WinlatorContainer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Winlatorエンジンの実装
 * WinlatorEmulatorを使用してWindowsゲームを実行
 */
@Singleton
class WinlatorEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val winlatorEmulator: WinlatorEmulator
) : WinlatorEngine {

    companion object {
        private const val TAG = "WinlatorEngine"
    }

    private var currentProcessId: String? = null
    private var currentEmulatorProcess: com.steamdeck.mobile.domain.emulator.EmulatorProcess? = null

    override suspend fun launchGame(game: Game, container: WinlatorContainer?): LaunchResult {
        return try {
            Log.d(TAG, "Launching game: ${game.name}")
            Log.d(TAG, "Executable: ${game.executablePath}")
            Log.d(TAG, "Container: ${container?.name ?: "Default"}")
            Log.d(TAG, "Source: ${game.source}, SteamAppId: ${game.steamAppId}")

            // Steamゲームの場合は、ダウンロードが必要
            if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM) {
                if (game.executablePath.isBlank()) {
                    Log.w(TAG, "Steam game not downloaded: ${game.name} (AppID: ${game.steamAppId})")
                    return LaunchResult.Error(
                        "このゲームはまだダウンロードされていません。\n\n" +
                        "今後のアップデートで、Steamゲームの\n" +
                        "ダウンロード機能を追加予定です。"
                    )
                }
            }

            // 1. Winlatorの初期化確認
            val available = winlatorEmulator.isAvailable().getOrNull() ?: false
            if (!available) {
                Log.w(TAG, "Winlator not initialized, attempting initialization...")
                val initResult = winlatorEmulator.initialize { progress, status ->
                    Log.d(TAG, "Initialization: $status ($progress)")
                }
                if (initResult.isFailure) {
                    return LaunchResult.Error(
                        "Winlatorの初期化に失敗しました:\n${initResult.exceptionOrNull()?.message}"
                    )
                }
            }

            // 2. 実行ファイルの存在確認
            if (game.executablePath.isBlank()) {
                return LaunchResult.Error("実行ファイルが設定されていません")
            }

            val execFile = File(game.executablePath)
            if (!execFile.exists()) {
                return LaunchResult.Error("実行ファイルが見つかりません:\n${game.executablePath}")
            }

            // 3. ゲームエンジンを検出
            val engine = detectGameEngine(game.executablePath)
            Log.d(TAG, "Detected engine: ${engine.displayName}")

            // 4. 最適化設定を適用
            val optimizedContainer = container ?: getOptimizedContainerSettings(engine)
            Log.d(TAG, "Using container settings: Box64Preset=${optimizedContainer.box64Preset}, Wine=${optimizedContainer.wineVersion}")

            // 5. EmulatorContainerを作成または取得
            val emulatorContainer = getOrCreateEmulatorContainer(game, optimizedContainer)

            // 6. ゲームを起動
            Log.i(TAG, "Launching executable via Winlator: ${execFile.absolutePath}")
            val launchResult = winlatorEmulator.launchExecutable(
                container = emulatorContainer,
                executable = execFile,
                arguments = parseCustomArgs(optimizedContainer.customArgs)
            )

            when {
                launchResult.isSuccess -> {
                    val emulatorProcess = launchResult.getOrThrow()
                    currentProcessId = emulatorProcess.id
                    currentEmulatorProcess = emulatorProcess
                    Log.i(TAG, "Game launched successfully: ProcessId=${emulatorProcess.id}, PID=${emulatorProcess.pid}")
                    LaunchResult.Success(emulatorProcess.pid ?: -1)
                }
                launchResult.isFailure -> {
                    val error = launchResult.exceptionOrNull()
                    Log.e(TAG, "Game launch failed", error)
                    LaunchResult.Error("起動失敗: ${error?.message}", error)
                }
                else -> {
                    LaunchResult.Error("不明なエラー")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch game", e)
            LaunchResult.Error("ゲーム起動エラー: ${e.message}", e)
        }
    }

    override fun isGameRunning(): Boolean {
        if (currentProcessId == null) return false

        // Check actual process status
        return try {
            val statusResult = kotlinx.coroutines.runBlocking {
                winlatorEmulator.getProcessStatus(currentProcessId!!)
            }
            statusResult.getOrNull()?.isRunning ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check process status", e)
            false
        }
    }

    override suspend fun stopGame(): Result<Unit> {
        return try {
            if (currentProcessId == null) {
                return Result.failure(IllegalStateException("実行中のゲームがありません"))
            }

            Log.d(TAG, "Stopping game process: $currentProcessId")

            // Kill process via WinlatorEmulator
            val killResult = winlatorEmulator.killProcess(currentProcessId!!, force = false)

            if (killResult.isSuccess) {
                Log.i(TAG, "Game stopped successfully")
                currentProcessId = null
                currentEmulatorProcess = null
                Result.success(Unit)
            } else {
                val error = killResult.exceptionOrNull()
                Log.e(TAG, "Failed to stop game", error)
                Result.failure(error ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop game", e)
            Result.failure(e)
        }
    }

    override suspend fun createContainer(container: WinlatorContainer): Result<WinlatorContainer> {
        return try {
            Log.d(TAG, "Creating container: ${container.name}")
            // TODO: Winlatorコンテナを作成
            Result.success(container)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create container", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteContainer(containerId: Long): Result<Unit> {
        return try {
            Log.d(TAG, "Deleting container: $containerId")
            // TODO: Winlatorコンテナを削除
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete container", e)
            Result.failure(e)
        }
    }

    override suspend fun detectGameEngine(executablePath: String): GameEngine {
        return try {
            val file = File(executablePath)
            val parentDir = file.parentFile ?: return GameEngine.UNKNOWN

            // Unity Engine検出
            if (parentDir.list()?.any { it.contains("UnityPlayer.dll", ignoreCase = true) } == true) {
                return GameEngine.UNITY
            }

            // Unreal Engine検出
            if (parentDir.list()?.any { it.contains("UE4", ignoreCase = true) || it.contains("UE5", ignoreCase = true) } == true) {
                return GameEngine.UNREAL
            }

            // .NET Framework検出（.exe拡張子のみチェック）
            if (file.extension.equals("exe", ignoreCase = true)) {
                return GameEngine.DOTNET
            }

            GameEngine.UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect game engine", e)
            GameEngine.UNKNOWN
        }
    }

    /**
     * WinlatorContainerをEmulatorContainerに変換して作成または取得
     */
    private suspend fun getOrCreateEmulatorContainer(
        game: Game,
        winlatorContainer: WinlatorContainer
    ): com.steamdeck.mobile.domain.emulator.EmulatorContainer {
        // Container IDをゲームIDから生成
        val containerId = "game_${game.id}"

        // 既存のコンテナリストを取得
        val existingContainers = winlatorEmulator.listContainers().getOrNull() ?: emptyList()
        val existingContainer = existingContainers.firstOrNull { it.id == containerId }

        if (existingContainer != null) {
            Log.d(TAG, "Using existing container: ${existingContainer.name}")
            return existingContainer
        }

        // 新規コンテナを作成
        Log.d(TAG, "Creating new container for game: ${game.name}")
        val config = com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig(
            name = "${game.name} Container",
            screenWidth = parseResolutionWidth(winlatorContainer.screenResolution),
            screenHeight = parseResolutionHeight(winlatorContainer.screenResolution),
            directXWrapper = if (winlatorContainer.enableDXVK) {
                com.steamdeck.mobile.domain.emulator.DirectXWrapperType.DXVK
            } else {
                com.steamdeck.mobile.domain.emulator.DirectXWrapperType.WINED3D
            },
            performancePreset = when (winlatorContainer.box64Preset) {
                Box64Preset.PERFORMANCE -> com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_PERFORMANCE
                Box64Preset.STABILITY -> com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_STABILITY
                else -> com.steamdeck.mobile.domain.emulator.PerformancePreset.BALANCED
            },
            customEnvVars = winlatorContainer.environmentVars
        )

        val createResult = winlatorEmulator.createContainer(config)
        return createResult.getOrThrow()
    }

    /**
     * カスタム引数を解析
     */
    private fun parseCustomArgs(customArgs: String): List<String> {
        return if (customArgs.isBlank()) {
            emptyList()
        } else {
            customArgs.split(" ").filter { it.isNotBlank() }
        }
    }

    /**
     * 解像度文字列から幅を解析（例: "1280x720" -> 1280）
     */
    private fun parseResolutionWidth(resolution: String): Int {
        return try {
            resolution.split("x").firstOrNull()?.toIntOrNull() ?: 1280
        } catch (e: Exception) {
            1280
        }
    }

    /**
     * 解像度文字列から高さを解析（例: "1280x720" -> 720）
     */
    private fun parseResolutionHeight(resolution: String): Int {
        return try {
            resolution.split("x").lastOrNull()?.toIntOrNull() ?: 720
        } catch (e: Exception) {
            720
        }
    }

    override fun getOptimizedContainerSettings(engine: GameEngine): WinlatorContainer {
        return when (engine) {
            GameEngine.UNITY -> {
                // Unity Engineの推奨設定
                WinlatorContainer(
                    name = "Unity Optimized",
                    box64Preset = Box64Preset.STABILITY,
                    wineVersion = "8.0",
                    environmentVars = mapOf(
                        "MESA_EXTENSION_MAX_YEAR" to "2003"
                    ),
                    screenResolution = "1280x720",
                    enableDXVK = true,
                    enableD3DExtras = false,
                    customArgs = "-force-gfx-direct"
                )
            }
            GameEngine.UNREAL -> {
                // Unreal Engineの推奨設定
                WinlatorContainer(
                    name = "Unreal Optimized",
                    box64Preset = Box64Preset.PERFORMANCE,
                    wineVersion = "8.0",
                    environmentVars = emptyMap(),
                    screenResolution = "1920x1080",
                    enableDXVK = true,
                    enableD3DExtras = true
                )
            }
            GameEngine.DOTNET -> {
                // .NET Frameworkの推奨設定
                WinlatorContainer(
                    name = ".NET Optimized",
                    box64Preset = Box64Preset.STABILITY,
                    wineVersion = "8.0",
                    environmentVars = mapOf(
                        "WINE_MONO_OVERRIDES" to "1"
                    ),
                    screenResolution = "1280x720",
                    enableDXVK = false,
                    enableD3DExtras = false
                )
            }
            GameEngine.UNKNOWN -> {
                // デフォルト設定
                WinlatorContainer.createDefault()
            }
        }
    }
}
