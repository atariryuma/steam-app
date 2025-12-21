package com.steamdeck.mobile.domain.usecase

import android.content.Context
import android.net.Uri
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.winlator.LaunchResult
import com.steamdeck.mobile.core.winlator.WinlatorEngine
import com.steamdeck.mobile.domain.emulator.WindowsEmulator
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Use case for launching games
 *
 * 2025 Best Practice:
 * - DataResult<T> for type-safe error handling
 * - AppLogger for centralized logging
 * - Process monitoring for accurate play time tracking
 * - Pre-launch validation (executable, Steam manifest, DLLs)
 */
class LaunchGameUseCase @Inject constructor(
 @ApplicationContext private val context: Context,
 private val gameRepository: GameRepository,
 private val containerRepository: WinlatorContainerRepository,
 private val winlatorEngine: WinlatorEngine,
 private val windowsEmulator: WindowsEmulator,
 private val validateGameInstallationUseCase: ValidateGameInstallationUseCase
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

   // If executable is a content URI, copy it to internal storage first
   val gameToLaunch = if (game.executablePath.startsWith("content://")) {
    AppLogger.d(TAG, "Copying executable from content URI to internal storage")
    val copiedPath = copyContentUriToFile(game.executablePath, game.name)
    if (copiedPath.isFailure) {
     return DataResult.Error(
      AppError.FileError(
       "Failed to copy game file: ${copiedPath.exceptionOrNull()?.message}",
       copiedPath.exceptionOrNull()
      )
     )
    }
    // Update game in database with new path
    val newPath = copiedPath.getOrThrow()
    gameRepository.updateGameExecutablePath(gameId, newPath)
    AppLogger.i(TAG, "Copied executable to: $newPath")
    game.copy(executablePath = newPath)
   } else {
    game
   }

   // Best Practice (2025): Comprehensive 3-level validation before launch
   // 1. Executable file exists
   // 2. Steam manifest StateFlags = 4 (fully installed)
   // 3. Required DLLs present
   val validationResult = validateGameInstallationUseCase(gameId)
   if (validationResult is DataResult.Success && !validationResult.data.isValid) {
    val errorMessage = validationResult.data.getUserMessage() ?: "Game installation validation failed"
    AppLogger.e(TAG, "Game installation validation failed: $errorMessage")
    return DataResult.Error(
     AppError.FileError(errorMessage, null)
    )
   }

   // Launch game
   when (val result = winlatorEngine.launchGame(gameToLaunch, container)) {
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
  * Copy file from content URI to internal storage
  *
  * @param contentUri Content URI from document picker
  * @param gameName Game name for file naming
  * @return Result with absolute file path
  */
 private suspend fun copyContentUriToFile(contentUri: String, gameName: String): Result<String> = withContext(Dispatchers.IO) {
  try {
   val uri = Uri.parse(contentUri)
   val fileName = gameName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") + ".exe"

   // Create imported_games directory
   val importDir = File(context.filesDir, "imported_games")
   if (!importDir.exists()) {
    importDir.mkdirs()
   }

   val destFile = File(importDir, fileName)

   // Copy file from content URI
   context.contentResolver.openInputStream(uri)?.use { input ->
    FileOutputStream(destFile).use { output ->
     val buffer = ByteArray(8192)
     var bytesRead: Int
     while (input.read(buffer).also { bytesRead = it } != -1) {
      output.write(buffer, 0, bytesRead)
     }
    }
   } ?: return@withContext Result.failure(Exception("Cannot open input stream for URI: $contentUri"))

   AppLogger.i(TAG, "Successfully copied file from content URI to: ${destFile.absolutePath}")
   Result.success(destFile.absolutePath)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to copy content URI to file", e)
   Result.failure(e)
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

     // Note: WinlatorEngine cleanup is handled automatically by the engine implementation
     // No manual cleanup needed as the engine manages its own resources
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

 companion object {
  private const val TAG = "LaunchGameUseCase"
 }
}
