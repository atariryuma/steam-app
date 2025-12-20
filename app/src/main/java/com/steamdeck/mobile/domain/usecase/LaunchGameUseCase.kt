package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.winlator.LaunchResult
import com.steamdeck.mobile.core.winlator.WinlatorEngine
import com.steamdeck.mobile.domain.emulator.WindowsEmulator
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Use case for launching games
 *
 * 2025 Best Practice:
 * - DataResult<T> for type-safe error handling
 * - AppLogger for centralized logging
 * - Process monitoring for accurate play time tracking
 */
class LaunchGameUseCase @Inject constructor(
 private val gameRepository: GameRepository,
 private val containerRepository: WinlatorContainerRepository,
 private val winlatorEngine: WinlatorEngine,
 private val windowsEmulator: WindowsEmulator
) {
 // Background scope for process monitoring (survives ViewModel lifecycle)
 private val monitoringScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 /**
  * Launch game
  * @param gameId Game ID
  * @return Launch result
  */
 suspend operator fun invoke(gameId: Long): DataResult<Int> {
  return try {
   // Get game information
   val game = gameRepository.getGameById(gameId)
    ?: return DataResult.Error(
     AppError.DatabaseError("Game not found", null)
    )

   // Get container information (if configured)
   val container = game.winlatorContainerId?.let { containerId ->
    containerRepository.getContainerById(containerId)
   }

   AppLogger.i(TAG, "Launching game: ${game.name} (ID: $gameId)")

   // Best Practice (2025): Validate game installation before launch
   // Prevents crashes from missing executables
   val validationError = validateGameInstallation(game)
   if (validationError != null) {
    AppLogger.e(TAG, "Game installation validation failed: $validationError")
    return DataResult.Error(
     AppError.FileError(validationError, null)
    )
   }

   // Launch game
   when (val result = winlatorEngine.launchGame(game, container)) {
    is LaunchResult.Success -> {
     val startTime = System.currentTimeMillis()

     // Record play start time
     try {
      gameRepository.updatePlayTime(gameId, 0, startTime)
     } catch (e: Exception) {
      AppLogger.w(TAG, "Failed to record start time", e)
     }

     // Performance optimization (2025 best practice):
     // Monitor process lifecycle for accurate play time tracking
     startProcessMonitoring(gameId, result.processId, startTime)

     AppLogger.i(TAG, "Game launched successfully: ${game.name} (PID: ${result.processId})")
     DataResult.Success(result.processId)
    }
    is LaunchResult.Error -> {
     AppLogger.e(TAG, "Game launch failed: ${result.message}", result.cause)
     DataResult.Error(
      AppError.Unknown(Exception(result.message, result.cause))
     )
    }
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Exception during game launch", e)
   DataResult.Error(AppError.from(e))
  }
 }

 /**
  * Monitor process and record play time
  */
 private fun startProcessMonitoring(gameId: Long, processId: Int, startTime: Long) {
  monitoringScope.launch {
   AppLogger.i(TAG, "Starting process monitoring for game $gameId (PID: $processId)")

   // Bug fix: Track last checkpoint to avoid duplicate saves
   var lastCheckpointMinute = 0

   windowsEmulator.monitorProcess(processId.toString())
    .onCompletion { cause ->
     if (cause == null) {
      // Normal termination - calculate play time
      val endTime = System.currentTimeMillis()
      val durationMinutes = ((endTime - startTime) / 60000).toInt()

      AppLogger.i(TAG, "Game $gameId finished. Play time: $durationMinutes minutes")

      try {
       gameRepository.updatePlayTime(gameId, durationMinutes.toLong(), endTime)
       AppLogger.d(TAG, "Play time saved successfully")
      } catch (e: Exception) {
       AppLogger.e(TAG, "Failed to save play time", e)
       // TODO: Queue for retry later
      }
     } else {
      AppLogger.w(TAG, "Process monitoring error: ${cause.message}", cause)
     }

     // CRITICAL FIX: Clean up engine resources to prevent memory leak
     // This prevents process references from accumulating over app lifetime
     try {
      winlatorEngine.cleanup()
      AppLogger.d(TAG, "Cleaned up WinlatorEngine resources after process termination")
     } catch (e: Exception) {
      AppLogger.w(TAG, "Failed to cleanup WinlatorEngine", e)
     }
    }
    .catch { e ->
     AppLogger.e(TAG, "Process monitoring exception", e)
    }
    .collect { status ->
     if (status.isRunning) {
      // Periodic checkpoint: save intermediate play time every 5 minutes
      val currentDuration = ((System.currentTimeMillis() - startTime) / 60000).toInt()
      // Bug fix: Only save when we cross a 5-minute boundary
      val currentCheckpoint = (currentDuration / 5) * 5
      if (currentCheckpoint > lastCheckpointMinute && currentCheckpoint > 0) {
       lastCheckpointMinute = currentCheckpoint
       try {
        gameRepository.updatePlayTime(gameId, currentDuration.toLong(), System.currentTimeMillis())
        AppLogger.d(TAG, "Checkpoint: Play time updated to $currentDuration minutes")
       } catch (e: Exception) {
        AppLogger.w(TAG, "Failed to save checkpoint", e)
       }
      }
     }
    }
  }
 }

 /**
  * Validate game installation status
  *
  * Best Practice (2025):
  * - Check executable file exists
  * - Check installation path exists
  * - Verify Steam AppManifest (when launching via Steam)
  *
  * @param game Game information
  * @return Error message (null if no issues)
  */
 private fun validateGameInstallation(game: com.steamdeck.mobile.domain.model.Game): String? {
  // 1. Check if executable file path is not blank
  if (game.executablePath.isBlank()) {
   return "Executable file is not configured.\nPlease install the game before launching."
  }

  // 2. Check if executable file exists
  val executableFile = java.io.File(game.executablePath)
  if (!executableFile.exists()) {
   return "Executable file not found:\n${game.executablePath}\n\nThe game may not be installed correctly."
  }

  // 3. Check if executable file is a regular file (not a directory)
  if (!executableFile.isFile) {
   return "Executable file is invalid (directory or special file):\n${game.executablePath}"
  }

  // 4. Check if installation path exists
  val installDir = java.io.File(game.installPath)
  if (!installDir.exists()) {
   return "Installation directory not found:\n${game.installPath}\n\nPlease reinstall the game."
  }

  // 5. Check if installation path is a directory
  if (!installDir.isDirectory) {
   return "Installation path is invalid (not a directory):\n${game.installPath}"
  }

  // 6. For Steam games, additional validation (optional - for future extension)
  if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM && game.steamAppId != null) {
   // TODO: Verify Steam AppManifest (future implementation)
   // Example: Check if C:/Steam/steamapps/appmanifest_${steamAppId}.acf exists
   AppLogger.d(TAG, "Steam game detected (AppID: ${game.steamAppId}) - additional validation possible in future")
  }

  // All validation passed
  AppLogger.d(TAG, "Game installation validation passed for: ${game.name}")
  return null
 }

 companion object {
  private const val TAG = "LaunchGameUseCase"
 }
}
