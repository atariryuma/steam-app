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
 * gamelaunchdoUseCase
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
  * gamelaunch
  * @param gameId gameID
  * @return launchresult
  */
 suspend operator fun invoke(gameId: Long): DataResult<Int> {
  return try {
   // gameinformationretrieve
   val game = gameRepository.getGameById(gameId)
    ?: return DataResult.Error(
     AppError.DatabaseError("game not found", null)
    )

   // containerinformationretrieve（configurationされているcase）
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

   // gamelaunch
   when (val result = winlatorEngine.launchGame(game, container)) {
    is LaunchResult.Success -> {
     val startTime = System.currentTimeMillis()

     // プレイstart時刻記録
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
  * processmonitorしてplay time記録
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
  * gameinstallationstateverification
  *
  * Best Practice (2025):
  * - executionfile existconfirmation
  * - installationpath existconfirmation
  * - Steam AppManifestconfirmation（Steamlaunch時）
  *
  * @param game gameinformation
  * @return errorメッセージ（問題なければnull）
  */
 private fun validateGameInstallation(game: com.steamdeck.mobile.domain.model.Game): String? {
  // 1. executionfilepath 空白 ないかconfirmation
  if (game.executablePath.isBlank()) {
   return "Executable file is not configured.\nPlease install the game before launching."
  }

  // 2. executionfile existdoかconfirmation
  val executableFile = java.io.File(game.executablePath)
  if (!executableFile.exists()) {
   return "executionfile not found:\n${game.executablePath}\n\ngame 正しくinstallationされていない可能性 あります。"
  }

  // 3. executionfile 通常fileかconfirmation（directory ない）
  if (!executableFile.isFile) {
   return "executionfile 無効 す（directoryor特殊file）:\n${game.executablePath}"
  }

  // 4. installationpath existdoかconfirmation
  val installDir = java.io.File(game.installPath)
  if (!installDir.exists()) {
   return "installationdirectory not found:\n${game.installPath}\n\ngame再installationplease。"
  }

  // 5. installationpath directoryかconfirmation
  if (!installDir.isDirectory) {
   return "installationpath 無効 す（directory ありません）:\n${game.installPath}"
  }

  // 6. Steamgame case、addverification（オプション - future extension用）
  if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM && game.steamAppId != null) {
   // TODO: Steam AppManifestverification（futureimplementation）
   // Example: C:/Steam/steamapps/appmanifest_${steamAppId}.acf existconfirmation
   AppLogger.d(TAG, "Steam game detected (AppID: ${game.steamAppId}) - additional validation possible in future")
  }

  // 全て verificationpath
  AppLogger.d(TAG, "Game installation validation passed for: ${game.name}")
  return null
 }

 companion object {
  private const val TAG = "LaunchGameUseCase"
 }
}
