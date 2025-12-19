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
 * ゲームを起動するUseCase
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
     * ゲームを起動
     * @param gameId ゲームID
     * @return 起動結果
     */
    suspend operator fun invoke(gameId: Long): DataResult<Int> {
        return try {
            // ゲーム情報を取得
            val game = gameRepository.getGameById(gameId)
                ?: return DataResult.Error(
                    AppError.DatabaseError("ゲームが見つかりません", null)
                )

            // コンテナ情報を取得（設定されている場合）
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

            // ゲームを起動
            when (val result = winlatorEngine.launchGame(game, container)) {
                is LaunchResult.Success -> {
                    val startTime = System.currentTimeMillis()

                    // プレイ開始時刻を記録
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
     * プロセスを監視してプレイ時間を記録
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
     * ゲームインストール状態を検証
     *
     * Best Practice (2025):
     * - 実行ファイルの存在確認
     * - インストールパスの存在確認
     * - Steam AppManifest確認（Steam起動時）
     *
     * @param game ゲーム情報
     * @return エラーメッセージ（問題なければnull）
     */
    private fun validateGameInstallation(game: com.steamdeck.mobile.domain.model.Game): String? {
        // 1. 実行ファイルパスが空白でないか確認
        if (game.executablePath.isBlank()) {
            return "実行ファイルが設定されていません。\nゲームをインストールしてから起動してください。"
        }

        // 2. 実行ファイルが存在するか確認
        val executableFile = java.io.File(game.executablePath)
        if (!executableFile.exists()) {
            return "実行ファイルが見つかりません:\n${game.executablePath}\n\nゲームが正しくインストールされていない可能性があります。"
        }

        // 3. 実行ファイルが通常ファイルか確認（ディレクトリでない）
        if (!executableFile.isFile) {
            return "実行ファイルが無効です（ディレクトリまたは特殊ファイル）:\n${game.executablePath}"
        }

        // 4. インストールパスが存在するか確認
        val installDir = java.io.File(game.installPath)
        if (!installDir.exists()) {
            return "インストールディレクトリが見つかりません:\n${game.installPath}\n\nゲームを再インストールしてください。"
        }

        // 5. インストールパスがディレクトリか確認
        if (!installDir.isDirectory) {
            return "インストールパスが無効です（ディレクトリではありません）:\n${game.installPath}"
        }

        // 6. Steamゲームの場合、追加検証（オプション - 将来の拡張用）
        if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM && game.steamAppId != null) {
            // TODO: Steam AppManifest検証（将来実装）
            // 例: C:/Steam/steamapps/appmanifest_${steamAppId}.acf の存在確認
            AppLogger.d(TAG, "Steam game detected (AppID: ${game.steamAppId}) - additional validation possible in future")
        }

        // 全ての検証をパス
        AppLogger.d(TAG, "Game installation validation passed for: ${game.name}")
        return null
    }

    companion object {
        private const val TAG = "LaunchGameUseCase"
    }
}
