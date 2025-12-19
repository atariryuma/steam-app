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
 * Winlatorエンジン implementation
 * WinlatorEmulatoruseしてWindowsgameexecution
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

 /**
  * cleanupmethod（メモリリーク防止）
  *
  * Best Practice (2025):
  * - ViewModel onCleared()from呼び出す
  * - process参照nullクリアしてGC可能 do
  * - アプリend時 gameend時 呼び出すこ 推奨
  */
 fun cleanup() {
  Log.d(TAG, "Cleaning up WinlatorEngine resources")
  currentProcessId = null
  currentEmulatorProcess = null
 }

 override suspend fun launchGame(game: Game, container: WinlatorContainer?): LaunchResult {
  return try {
   Log.d(TAG, "Launching game: ${game.name}")
   Log.d(TAG, "Executable: ${game.executablePath}")
   Log.d(TAG, "Container: ${container?.name ?: "Default"}")
   Log.d(TAG, "Source: ${game.source}, SteamAppId: ${game.steamAppId}")

   // Steamgame case 、download 必要
   if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM) {
    if (game.executablePath.isBlank()) {
     Log.w(TAG, "Steam game not downloaded: ${game.name} (AppID: ${game.steamAppId})")
     return LaunchResult.Error(
      "This game has not been downloaded yet.\n\n" +
      "Steam game download functionality will be added in a future update."
     )
    }
   }

   // 1. Winlator initializationconfirmation 自動initialization
   val available = winlatorEmulator.isAvailable().getOrNull() ?: false
   if (!available) {
    Log.i(TAG, "Winlator not initialized, starting automatic initialization...")
    val initResult = winlatorEmulator.initialize { progress, status ->
     Log.d(TAG, "Initialization progress: ${(progress * 100).toInt()}% - $status")
    }
    if (initResult.isFailure) {
     val error = initResult.exceptionOrNull()
     Log.e(TAG, "Winlator initialization failed", error)
     return LaunchResult.Error(
      "Failed to initialize Winlator environment.\n\n" +
      "Error: ${error?.message}\n\n" +
      "Try the following:\n" +
      "• Check free storage space (minimum 500MB required)\n" +
      "• Restart the app\n" +
      "• Restart your device"
     )
    }
    Log.i(TAG, "Winlator initialization completed successfully")
   }

   // 2. executionfile existconfirmation
   if (game.executablePath.isBlank()) {
    return LaunchResult.Error("Executable file is not configured")
   }

   val execFile = File(game.executablePath)
   if (!execFile.exists()) {
    return LaunchResult.Error("executionfile not found:\n${game.executablePath}")
   }

   // 3. gameエンジンdetection
   val engine = detectGameEngine(game.executablePath)
   Log.d(TAG, "Detected engine: ${engine.displayName}")

   // 4. optimizationconfigurationapply
   val optimizedContainer = container ?: getOptimizedContainerSettings(engine)
   Log.d(TAG, "Using container settings: Box64Preset=${optimizedContainer.box64Preset}, Wine=${optimizedContainer.wineVersion}")

   // 5. EmulatorContainercreateorretrieve
   val emulatorContainer = getOrCreateEmulatorContainer(game, optimizedContainer)

   // 6. gamelaunch
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
     LaunchResult.Error("launchfailure: ${error?.message}", error)
    }
    else -> {
     LaunchResult.Error("Unknown error")
    }
   }
  } catch (e: Exception) {
   Log.e(TAG, "Failed to launch game", e)
   LaunchResult.Error("gamelauncherror: ${e.message}", e)
  }
 }

 override suspend fun isGameRunning(): Boolean {
  val processId = currentProcessId ?: return false

  // Check actual process status without blocking
  return try {
   val statusResult = winlatorEmulator.getProcessStatus(processId)
   statusResult.getOrNull()?.isRunning ?: false
  } catch (e: Exception) {
   Log.w(TAG, "Failed to check process status", e)
   false
  }
 }

 override suspend fun stopGame(): Result<Unit> {
  return try {
   val processId = currentProcessId
   if (processId == null) {
    return Result.failure(IllegalStateException("No game is currently running"))
   }

   Log.d(TAG, "Stopping game process: $processId")

   // Kill process via WinlatorEmulator
   val killResult = winlatorEmulator.killProcess(processId, force = false)

   if (killResult.isSuccess) {
    Log.i(TAG, "Game stopped successfully")
    cleanup() // Clean up resources to prevent memory leaks
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
   // TODO: Winlatorcontainercreate
   Result.success(container)
  } catch (e: Exception) {
   Log.e(TAG, "Failed to create container", e)
   Result.failure(e)
  }
 }

 override suspend fun deleteContainer(containerId: Long): Result<Unit> {
  return try {
   Log.d(TAG, "Deleting container: $containerId")
   // TODO: Winlatorcontainerdelete
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

   // Unity Enginedetection
   if (parentDir.list()?.any { it.contains("UnityPlayer.dll", ignoreCase = true) } == true) {
    return GameEngine.UNITY
   }

   // Unreal Enginedetection
   if (parentDir.list()?.any { it.contains("UE4", ignoreCase = true) || it.contains("UE5", ignoreCase = true) } == true) {
    return GameEngine.UNREAL
   }

   // .NET Frameworkdetection（.exeextension子 みcheck）
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
  * WinlatorContainerEmulatorContainer conversionしてcreateorretrieve
  */
 private suspend fun getOrCreateEmulatorContainer(
  game: Game,
  winlatorContainer: WinlatorContainer
 ): com.steamdeck.mobile.domain.emulator.EmulatorContainer {
  // Container IDgameIDfromgenerate
  val containerId = "game_${game.id}"

  // existingcontainerリストretrieve
  val existingContainers = winlatorEmulator.listContainers().getOrNull() ?: emptyList()
  val existingContainer = existingContainers.firstOrNull { it.id == containerId }

  if (existingContainer != null) {
   Log.d(TAG, "Using existing container: ${existingContainer.name}")
   return existingContainer
  }

  // 新規containercreate
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
  * customargument解析
  */
 private fun parseCustomArgs(customArgs: String): List<String> {
  return if (customArgs.isBlank()) {
   emptyList()
  } else {
   customArgs.split(" ").filter { it.isNotBlank() }
  }
 }

 /**
  * 解像度文字列from幅解析（Example: "1280x720" -> 1280）
  */
 private fun parseResolutionWidth(resolution: String): Int {
  return try {
   resolution.split("x").firstOrNull()?.toIntOrNull() ?: 1280
  } catch (e: Exception) {
   1280
  }
 }

 /**
  * 解像度文字列from高さ解析（Example: "1280x720" -> 720）
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
    // Unity Enginerecommendedconfiguration
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
    // Unreal Enginerecommendedconfiguration
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
    // .NET Frameworkrecommendedconfiguration
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
    // defaultconfiguration
    WinlatorContainer.createDefault()
   }
  }
 }
}
