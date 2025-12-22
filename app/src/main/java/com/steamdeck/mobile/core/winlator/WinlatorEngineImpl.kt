package com.steamdeck.mobile.core.winlator

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.domain.model.Box64Preset
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.WinlatorContainer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Winlator Engine Implementation
 * Executes Windows games using WinlatorEmulator
 */
@Singleton
class WinlatorEngineImpl @Inject constructor(
 @ApplicationContext private val context: Context,
 private val winlatorEmulator: WinlatorEmulator,
 private val wineMonoInstaller: WineMonoInstaller
) : WinlatorEngine {

 companion object {
  private const val TAG = "WinlatorEngine"
 }

 private var currentProcessId: String? = null
 private var currentEmulatorProcess: com.steamdeck.mobile.domain.emulator.EmulatorProcess? = null

 // OPTIMIZATION: Cache default container to avoid repeated file system scans
 // Cleared on cleanup() to ensure fresh state - Thread-safe with AtomicReference
 private val cachedDefaultContainer = AtomicReference<com.steamdeck.mobile.domain.emulator.EmulatorContainer?>(null)

 /**
  * Cleanup method (prevents memory leaks)
  *
  * Best Practice (2025):
  * - Call from ViewModel onCleared()
  * - Clear process references to null to enable GC
  * - Recommended to call when app exits or game terminates
  */
 override fun cleanup() {
  AppLogger.d(TAG, "Cleaning up WinlatorEngine resources")
  currentProcessId = null
  currentEmulatorProcess = null
  cachedDefaultContainer.set(null) // Clear container cache (thread-safe)
 }

 override suspend fun launchGame(game: Game, container: WinlatorContainer?): LaunchResult {
  return try {
   AppLogger.d(TAG, "=== GAME LAUNCH START ===")
   AppLogger.d(TAG, "Game: ${game.name}, ID: ${game.id}")
   AppLogger.d(TAG, "Executable: ${game.executablePath}")
   AppLogger.d(TAG, "Container: ${container?.name ?: "Default"}")
   AppLogger.d(TAG, "Source: ${game.source}, SteamAppId: ${game.steamAppId}")

   // For Steam games, download is required
   if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM) {
    if (game.executablePath.isBlank()) {
     AppLogger.w(TAG, "Steam game not downloaded: ${game.name} (AppID: ${game.steamAppId})")
     return LaunchResult.Error(
      "This game has not been downloaded yet.\n\n" +
      "Steam game download functionality will be added in a future update."
     )
    }
   }

   // 1. Check Winlator initialization and auto-initialize if needed
   val available = winlatorEmulator.isAvailable().getOrNull() ?: false
   AppLogger.d(TAG, "Winlator available: $available")

   if (!available) {
    AppLogger.i(TAG, ">>> Starting Winlator initialization (30-60s expected)...")
    val initResult = winlatorEmulator.initialize { progress, status ->
     // CRITICAL: 初期化進捗を必ずログ出力
     AppLogger.i(TAG, ">>> Init: ${(progress * 100).toInt()}% - $status")
    }
    if (initResult.isFailure) {
     val error = initResult.exceptionOrNull()
     AppLogger.e(TAG, ">>> Initialization FAILED", error)
     return LaunchResult.Error(
      "Failed to initialize Winlator environment.\n\n" +
      "Error: ${error?.message}\n\n" +
      "Details: ${error?.stackTraceToString()?.take(500)}\n\n" +
      "Try the following:\n" +
      "• Check free storage space (minimum 500MB required)\n" +
      "• Restart the app\n" +
      "• Restart your device"
     )
    }
    AppLogger.i(TAG, ">>> Initialization SUCCESS")
   }

   // 2. Check executable file exists
   if (game.executablePath.isBlank()) {
    return LaunchResult.Error("Executable file is not configured")
   }

   val execFile = File(game.executablePath)
   if (!execFile.exists()) {
    return LaunchResult.Error("Executable file not found:\n${game.executablePath}")
   }

   // 3. Detect game engine
   val engine = detectGameEngine(game.executablePath)
   AppLogger.d(TAG, "Detected engine: ${engine.displayName}")

   // 4. Apply optimization configuration
   val optimizedContainer = container ?: getOptimizedContainerSettings(engine)
   AppLogger.d(TAG, "Using container settings: Box64Preset=${optimizedContainer.box64Preset}, Wine=${optimizedContainer.wineVersion}")

   // 5. Create or get EmulatorContainer
   val emulatorContainer = getOrCreateEmulatorContainer(game, optimizedContainer)

   // 6. Launch game
   AppLogger.i(TAG, "Launching executable via Winlator: ${execFile.absolutePath}")
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
     AppLogger.i(TAG, "Game launched successfully: ProcessId=${emulatorProcess.id}, PID=${emulatorProcess.pid}")
     LaunchResult.Success(
      pid = emulatorProcess.pid ?: -1,
      processId = emulatorProcess.id
     )
    }
    launchResult.isFailure -> {
     val error = launchResult.exceptionOrNull()
     AppLogger.e(TAG, "Game launch failed", error)
     LaunchResult.Error("Launch failed: ${error?.message}", error)
    }
    else -> {
     LaunchResult.Error("Unknown error")
    }
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to launch game", e)
   LaunchResult.Error("Game launch error: ${e.message}", e)
  }
 }

 override suspend fun isGameRunning(): Boolean {
  val processId = currentProcessId ?: return false

  // Check actual process status without blocking
  return try {
   val statusResult = winlatorEmulator.getProcessStatus(processId)
   statusResult.getOrNull()?.isRunning ?: false
  } catch (e: Exception) {
   AppLogger.w(TAG, "Failed to check process status", e)
   false
  }
 }

 override suspend fun stopGame(): Result<Unit> {
  return try {
   val processId = currentProcessId
   if (processId == null) {
    return Result.failure(IllegalStateException("No game is currently running"))
   }

   AppLogger.d(TAG, "Stopping game process: $processId")

   // Kill process via WinlatorEmulator
   val killResult = winlatorEmulator.killProcess(processId, force = false)

   if (killResult.isSuccess) {
    AppLogger.i(TAG, "Game stopped successfully")
    cleanup() // Clean up resources to prevent memory leaks
    Result.success(Unit)
   } else {
    val error = killResult.exceptionOrNull()
    AppLogger.e(TAG, "Failed to stop game", error)
    Result.failure(error ?: Exception("Unknown error"))
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to stop game", e)
   Result.failure(e)
  }
 }

 override suspend fun createContainer(container: WinlatorContainer): Result<WinlatorContainer> {
  return try {
   AppLogger.d(TAG, "Creating container: ${container.name}")
   // TODO: Create Winlator container

   // Invalidate cache when new container is created
   cachedDefaultContainer.set(null)

   Result.success(container)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to create container", e)
   Result.failure(e)
  }
 }

 override suspend fun deleteContainer(containerId: Long): Result<Unit> {
  return try {
   AppLogger.d(TAG, "Deleting container: $containerId")
   // TODO: Delete Winlator container

   // Invalidate cache when container is deleted
   cachedDefaultContainer.set(null)

   Result.success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to delete container", e)
   Result.failure(e)
  }
 }

 override suspend fun detectGameEngine(executablePath: String): GameEngine {
  return try {
   val file = File(executablePath)
   val parentDir = file.parentFile ?: return GameEngine.UNKNOWN

   // Detect Unity Engine
   if (parentDir.list()?.any { it.contains("UnityPlayer.dll", ignoreCase = true) } == true) {
    return GameEngine.UNITY
   }

   // Detect Unreal Engine
   if (parentDir.list()?.any { it.contains("UE4", ignoreCase = true) || it.contains("UE5", ignoreCase = true) } == true) {
    return GameEngine.UNREAL
   }

   // Detect .NET Framework (check .exe extension only)
   if (file.extension.equals("exe", ignoreCase = true)) {
    return GameEngine.DOTNET
   }

   GameEngine.UNKNOWN
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to detect game engine", e)
   GameEngine.UNKNOWN
  }
 }

 /**
  * Convert WinlatorContainer to EmulatorContainer and create or get
  *
  * Best Practice (2025): Shared default container pattern + caching for efficiency
  * - Most games share a single "Default Container" (saves 90% disk space)
  * - Container created only once (60s), reused for all games (instant launch)
  * - Custom containers used only when explicitly assigned to a game
  * - OPTIMIZATION: Cache default container to avoid repeated file system scans
  *
  * Benefits:
  * - Disk usage: 500MB × 10 games = 5GB → 500MB (single shared container)
  * - First launch time: 60s × 10 games → 60s once (10x faster)
  * - Launch overhead: Multiple file scans → Single cached lookup (instant)
  * - Simpler UX: Users don't need to manage containers manually
  */
 private suspend fun getOrCreateEmulatorContainer(
  game: Game,
  winlatorContainer: WinlatorContainer
 ): com.steamdeck.mobile.domain.emulator.EmulatorContainer {
  // PRIORITY 1: Check if game has custom container assigned (advanced use case)
  // This allows power users to create game-specific containers when needed
  // (e.g., different Wine versions, conflicting DLLs, etc.)
  if (game.winlatorContainerId != null) {
   // Custom containers are not cached (rare use case)
   val existingContainers = winlatorEmulator.listContainers().getOrNull() ?: emptyList()
   val customContainer = existingContainers.firstOrNull {
    it.id == "container_${game.winlatorContainerId}"
   }
   if (customContainer != null) {
    AppLogger.d(TAG, "Using custom container for ${game.name}: ${customContainer.name}")
    return customContainer
   } else {
    AppLogger.w(TAG, "Custom container ${game.winlatorContainerId} not found, falling back to default")
   }
  }

  // PRIORITY 2: Use cached default container (OPTIMIZATION)
  // Most games use the default container, so cache it for instant lookup
  cachedDefaultContainer.get()?.let { cached ->
   AppLogger.d(TAG, "Using cached default container for: ${game.name}")
   return cached
  }

  // PRIORITY 3: Fetch and cache default container
  // This only happens once per app session after cache miss
  val existingContainers = winlatorEmulator.listContainers().getOrNull() ?: emptyList()
  val defaultContainerId = "default_shared_container"
  val defaultContainer = existingContainers.firstOrNull { it.id == defaultContainerId }

  if (defaultContainer != null) {
   AppLogger.d(TAG, "Fetched and cached default container for: ${game.name}")
   cachedDefaultContainer.set(defaultContainer) // Cache for future lookups (thread-safe)
   return defaultContainer
  }

  // PRIORITY 4: Create default container on first game launch
  // This happens only once per app installation, all subsequent games reuse it
  AppLogger.i(TAG, "Creating shared default container (first-time setup)")
  AppLogger.i(TAG, "All games will share this container for optimal disk usage")
  val config = com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig(
   name = "Default Container", // Shared by all games
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
  val createdContainer = createResult.getOrThrow()

  // Install Wine Mono for .NET and Windows DLL support
  installWineMonoIfNeeded(createdContainer)

  cachedDefaultContainer.set(createdContainer) // Cache newly created container (thread-safe)
  return createdContainer
 }

 /**
  * Parse custom arguments
  */
 private fun parseCustomArgs(customArgs: String): List<String> {
  return if (customArgs.isBlank()) {
   emptyList()
  } else {
   customArgs.split(" ").filter { it.isNotBlank() }
  }
 }

 /**
  * Parse width from resolution string (Example: "1280x720" -> 1280)
  */
 private fun parseResolutionWidth(resolution: String): Int {
  return try {
   resolution.split("x").firstOrNull()?.toIntOrNull() ?: 1280
  } catch (e: Exception) {
   1280
  }
 }

 /**
  * Parse height from resolution string (Example: "1280x720" -> 720)
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
    // Unity Engine recommended configuration
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
    // Unreal Engine recommended configuration
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
    // .NET Framework recommended configuration
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
    // Default configuration
    WinlatorContainer.createDefault()
   }
  }
 }

 /**
  * Install Wine Mono if not already installed in the container
  * This provides .NET Framework support and common Windows DLLs
  */
 private suspend fun installWineMonoIfNeeded(container: com.steamdeck.mobile.domain.emulator.EmulatorContainer) {
  try {
   // Check if Wine Mono is already installed
   val alreadyInstalled = wineMonoInstaller.isWineMonoInstalled(container)
   if (alreadyInstalled) {
    AppLogger.d(TAG, "Wine Mono already installed in container: ${container.name}")
    return
   }

   AppLogger.i(TAG, "Wine Mono not found, installing to container: ${container.name}")

   // Download Wine Mono (cached if already downloaded)
   val downloadResult = wineMonoInstaller.downloadWineMono { progress, message ->
    AppLogger.d(TAG, "Wine Mono download: $message ($progress)")
   }

   if (downloadResult.isFailure) {
    AppLogger.e(TAG, "Failed to download Wine Mono", downloadResult.exceptionOrNull())
    return // Non-fatal: continue without Wine Mono
   }

   val monoTarball = downloadResult.getOrThrow()

   // Install Wine Mono to container (extract tarball directly)
   val installResult = wineMonoInstaller.installWineMonoToContainer(
    container = container,
    monoTarball = monoTarball
   ) { progress, message ->
    AppLogger.d(TAG, "Wine Mono install: $message ($progress)")
   }

   if (installResult.isSuccess) {
    AppLogger.i(TAG, "Wine Mono installed successfully to container: ${container.name}")
   } else {
    AppLogger.e(TAG, "Failed to install Wine Mono", installResult.exceptionOrNull())
    // Non-fatal: continue without Wine Mono
   }

  } catch (e: Exception) {
   AppLogger.e(TAG, "Wine Mono installation error (non-fatal)", e)
   // Non-fatal: game may still work without Wine Mono
  }
 }
}
