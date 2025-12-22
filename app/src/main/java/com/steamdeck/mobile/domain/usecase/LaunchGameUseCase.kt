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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Use case for launching games
 *
 * 2025 Best Practice (FIXED):
 * - DataResult<T> for type-safe error handling
 * - AppLogger for centralized logging
 * - Flow-based process monitoring (prevents memory leaks)
 * - Pre-launch validation (executable, Steam manifest, DLLs)
 * - No background scope (monitoring Flow is collected in ViewModelScope)
 */
class LaunchGameUseCase @Inject constructor(
 @ApplicationContext private val context: Context,
 private val gameRepository: GameRepository,
 private val containerRepository: WinlatorContainerRepository,
 private val winlatorEngine: WinlatorEngine,
 private val windowsEmulator: WindowsEmulator,
 private val validateGameInstallationUseCase: ValidateGameInstallationUseCase
) {
 /**
  * Launch game and return launch info with monitoring Flow
  * @param gameId Game ID
  * @return Launch result with process monitoring Flow
  */
 suspend operator fun invoke(gameId: Long): DataResult<LaunchInfo> {
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
    AppLogger.d(TAG, ">>> Converting content:// URI to file path")
    AppLogger.d(TAG, ">>> Source URI: ${game.executablePath}")
    val copiedPath = copyContentUriToFile(game.executablePath, game.name)
    if (copiedPath.isFailure) {
     AppLogger.e(TAG, ">>> URI conversion FAILED: ${copiedPath.exceptionOrNull()?.message}")
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
    AppLogger.i(TAG, ">>> URI converted successfully: $newPath")
    game.copy(executablePath = newPath)
   } else {
    AppLogger.d(TAG, ">>> Using direct file path: ${game.executablePath}")
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

     // FIXED (2025): Return Flow for ViewModel to collect
     // No background scope - prevents memory leaks
     // FIXED (2025-12-22): Use processId string instead of integer PID for process monitoring
     val monitoringFlow = createProcessMonitoringFlow(gameId, result.processId, startTime)

     AppLogger.i(TAG, "Game launched successfully: ${game.name} (PID: ${result.pid}, ProcessId: ${result.processId})")
     DataResult.Success(
      LaunchInfo(
       processId = result.pid,
       monitoringFlow = monitoringFlow
      )
     )
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
  * Create process monitoring Flow (FIXED: No background scope)
  * ViewModel collects this Flow in viewModelScope
  *
  * @param processId Full process ID string (e.g., "1766386680416_30819")
  */
 private fun createProcessMonitoringFlow(
  gameId: Long,
  processId: String,
  startTime: Long
 ): Flow<Unit> {
  AppLogger.i(TAG, "Creating process monitoring flow for game $gameId (ProcessId: $processId)")

  // Track last checkpoint to avoid duplicate saves
  var lastCheckpointMinute = 0

  return windowsEmulator.monitorProcess(processId)
   .onCompletion { cause ->
    // Always calculate and save play time on completion
    val endTime = System.currentTimeMillis()
    val durationMinutes = ((endTime - startTime) / 60000).toInt()

    when (cause) {
     null -> {
      // Normal completion
      AppLogger.i(TAG, "Game $gameId finished normally. Play time: $durationMinutes minutes")
     }
     is kotlinx.coroutines.CancellationException -> {
      // ViewModel cancelled - save partial play time
      AppLogger.i(TAG, "Game $gameId monitoring cancelled. Partial play time: $durationMinutes minutes")
      throw cause // Re-throw to propagate cancellation
     }
     else -> {
      // Error - save partial play time
      AppLogger.e(TAG, "Game $gameId monitoring error: ${cause.message}. Partial play time: $durationMinutes minutes", cause)
     }
    }

    // Save play time in all cases (normal, cancelled, error)
    try {
     gameRepository.updatePlayTime(gameId, durationMinutes.toLong(), endTime)
     AppLogger.d(TAG, "Play time saved successfully: $durationMinutes minutes")
    } catch (e: Exception) {
     AppLogger.e(TAG, "Failed to save play time", e)
     // Non-fatal: continue completion
    }

    // Note: WinlatorEngine cleanup is handled automatically by the engine implementation
    // No manual cleanup needed as the engine manages its own resources
   }
   .map { status ->
    if (status.isRunning) {
     // Periodic checkpoint: save intermediate play time every 5 minutes
     val currentDuration = ((System.currentTimeMillis() - startTime) / 60000).toInt()
     // Only save when we cross a 5-minute boundary
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

 companion object {
  private const val TAG = "LaunchGameUseCase"
 }
}

/**
 * Launch information with process monitoring Flow
 *
 * FIXED (2025): Flow-based design prevents memory leaks
 * ViewModel collects monitoringFlow in viewModelScope
 */
data class LaunchInfo(
 val processId: Int,
 val monitoringFlow: Flow<Unit>
)
