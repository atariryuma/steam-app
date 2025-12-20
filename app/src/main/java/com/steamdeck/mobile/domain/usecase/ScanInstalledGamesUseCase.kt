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
 * インストール済み Steam ゲームスキャン UseCase
 *
 * Steam フォルダをスキャンして、インストール済みゲームの実行ファイルパスを自動検出・更新する
 */
class ScanInstalledGamesUseCase @Inject constructor(
 private val gameRepository: GameRepository,
 private val steamGameScanner: SteamGameScanner
) {
 companion object {
  private const val TAG = "ScanInstalledGamesUseCase"
 }

 /**
  * 特定のゲームをスキャン
  *
  * @param gameId ゲーム ID
  * @return スキャン結果（成功した場合は true、失敗した場合は false）
  */
 suspend operator fun invoke(gameId: Long): DataResult<Boolean> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Scanning game: gameId=$gameId")

   // 1. ゲーム情報を取得
   val game = gameRepository.getGameById(gameId)
    ?: return@withContext DataResult.Error(
     com.steamdeck.mobile.core.error.AppError.DatabaseError("Game not found: $gameId")
    )

   // 2. Steam ゲームかチェック
   if (game.source != GameSource.STEAM) {
    AppLogger.w(TAG, "Game is not from Steam: ${game.name}")
    return@withContext DataResult.Success(false)
   }

   // 3. Steam App ID チェック
   if (game.steamAppId == null) {
    AppLogger.w(TAG, "Game has no Steam App ID: ${game.name}")
    return@withContext DataResult.Success(false)
   }

   // 4. コンテナ ID チェック
   if (game.winlatorContainerId == null) {
    AppLogger.w(TAG, "Game has no container ID: ${game.name}")
    return@withContext DataResult.Success(false)
   }

   // 5. スキャン実行
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

   // 6. 実行ファイルが見つかった場合、ゲーム情報を更新
   if (executablePath != null) {
    // Bug fix: インストールパスも設定（実行ファイルの親フォルダ）
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
