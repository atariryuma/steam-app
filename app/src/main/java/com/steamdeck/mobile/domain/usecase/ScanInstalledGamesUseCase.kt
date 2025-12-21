package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.SteamGameScanner
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Installed Steam game scanner use case
 *
 * Scans Steam folders to automatically detect and update executable paths for installed games
 */
class ScanInstalledGamesUseCase @Inject constructor(
 private val gameRepository: GameRepository,
 private val steamGameScanner: SteamGameScanner
) {
 companion object {
  private const val TAG = "ScanInstalledGamesUseCase"
 }

 /**
  * Scan specific game
  *
  * @param gameId Game ID
  * @return Scan result (true if successful, false otherwise)
  */
 suspend operator fun invoke(gameId: Long): DataResult<Boolean> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Scanning game: gameId=$gameId")

   // 1. Get game information
   val game = gameRepository.getGameById(gameId)
    ?: return@withContext DataResult.Error(
     com.steamdeck.mobile.core.error.AppError.DatabaseError("Game not found: $gameId")
    )

   // 2. Check if game is from Steam
   if (game.source != GameSource.STEAM) {
    AppLogger.w(TAG, "Game is not from Steam: ${game.name}")
    return@withContext DataResult.Success(false)
   }

   // 3. Validate Steam App ID
   if (game.steamAppId == null) {
    AppLogger.w(TAG, "Game has no Steam App ID: ${game.name}")
    return@withContext DataResult.Success(false)
   }

   // 4. Validate container ID
   if (game.winlatorContainerId == null) {
    AppLogger.w(TAG, "Game has no container ID: ${game.name}")
    return@withContext DataResult.Success(false)
   }

   // 5. Execute scan
   val scanResult = steamGameScanner.findGameExecutable(
    containerId = game.winlatorContainerId.toString(),
    steamAppId = game.steamAppId
   )

   if (scanResult.isFailure) {
    AppLogger.e(TAG, "Scan failed: ${scanResult.exceptionOrNull()?.message}")
    return@withContext DataResult.Error(
     com.steamdeck.mobile.core.error.AppError.Unknown(
      scanResult.exceptionOrNull()
     )
    )
   }

   val executablePath = scanResult.getOrNull()

   // 6. Update game info if executable found
   if (executablePath != null) {
    // Bug fix: Set install path as well (parent folder of executable)
    // Handle cases where path separator might not be present
    val installPath = if (executablePath.contains("\\")) {
     executablePath.substringBeforeLast("\\")
    } else if (executablePath.contains("/")) {
     executablePath.substringBeforeLast("/")
    } else {
     // No path separator - use the path as-is (edge case)
     executablePath
    }

    val updatedGame = game.copy(
     executablePath = executablePath,
     installPath = installPath
    )

    gameRepository.updateGame(updatedGame)
    AppLogger.i(TAG, "Game executable found and updated: ${game.name} -> $executablePath")
    DataResult.Success(true)
   } else {
    AppLogger.i(TAG, "Game executable not found (not installed): ${game.name}")
    DataResult.Success(false)
   }

  } catch (e: Exception) {
   AppLogger.e(TAG, "Exception during scan", e)
   DataResult.Error(
    com.steamdeck.mobile.core.error.AppError.Unknown(e)
   )
  }
 }
}
