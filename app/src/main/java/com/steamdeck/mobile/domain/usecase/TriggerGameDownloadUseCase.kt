package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.SteamInstallMonitorService
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trigger Steam game download use case
 *
 * @Deprecated("Use LaunchOrDownloadGameUseCase instead - provides complete download/install/launch flow")
 *
 * This use case is kept for backward compatibility but LaunchOrDownloadGameUseCase
 * provides a superior implementation with:
 * - Automatic Steam installation
 * - Better error handling
 * - Complete state management
 * - Auto-launch after installation
 *
 * Legacy functionality (for reference):
 * 1. Validates Steam.exe exists in Wine container
 * 2. Starts SteamInstallMonitorService to track download progress
 * 3. Launches Steam with `-applaunch <appId>` to trigger download
 * 4. Updates game installation status to DOWNLOADING
 *
 * Architecture:
 * - Clean separation from ViewModel (testable)
 * - Uses official Steam command (ToS compliant)
 * - FileObserver-based monitoring (minimal battery impact)
 */
@Deprecated(
    message = "Use LaunchOrDownloadGameUseCase instead",
    replaceWith = ReplaceWith(
        "LaunchOrDownloadGameUseCase",
        "com.steamdeck.mobile.domain.usecase.LaunchOrDownloadGameUseCase"
    )
)
@Singleton
class TriggerGameDownloadUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val steamLauncher: SteamLauncher
) {
    companion object {
        private const val TAG = "TriggerGameDownload"
    }

    /**
     * Trigger game download via Steam
     *
     * @param gameId Game database ID
     * @return DataResult indicating success or error
     */
    suspend operator fun invoke(gameId: Long): DataResult<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Triggering game download: gameId=$gameId")

            // Get game information
            val game = gameRepository.getGameById(gameId)
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError("Game not found: $gameId", null)
                )

            // Validate Steam App ID
            val steamAppId = game.steamAppId
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError("Game has no Steam App ID", null)
                )

            // Determine container ID
            val containerId = game.winlatorContainerId ?: "default_shared_container"
            AppLogger.d(TAG, "Using container: $containerId for game: ${game.name}")

            // Validate Steam.exe exists
            val steamExePath = File(
                context.filesDir,
                "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/Steam.exe"
            )

            if (!steamExePath.exists()) {
                AppLogger.e(TAG, "Steam.exe not found at: ${steamExePath.absolutePath}")
                return@withContext DataResult.Error(
                    AppError.FileError(
                        "Steam is not installed in Wine container. Please install Steam first.",
                        null
                    )
                )
            }

            // Start SteamInstallMonitorService for progress tracking
            // Service will monitor steamapps/ directory for appmanifest_*.acf changes
            try {
                // BUG (2025-12-26): Using gameId as downloadId causes ID collision
                // This prevents tracking multiple downloads for the same game
                // LaunchOrDownloadGameUseCase uses downloadRepository.insertDownload() instead (correct)
                //
                // IMPORTANT: This use case is DEPRECATED - do NOT fix this bug here
                // Instead, use LaunchOrDownloadGameUseCase which properly creates unique downloadId
                //
                // If you must fix: Create Download entity with downloadRepository.insertDownload()
                // and use the returned downloadId instead of gameId
                SteamInstallMonitorService.start(
                    context = context,
                    containerId = containerId,
                    steamAppId = steamAppId,
                    gameId = gameId,
                    downloadId = gameId // BUG: ID collision - use LaunchOrDownloadGameUseCase instead
                )
                AppLogger.i(TAG, "Started SteamInstallMonitorService for appId=$steamAppId")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start monitor service (non-fatal)", e)
                // Continue with download even if monitoring fails
            }

            // Update game status to DOWNLOADING
            gameRepository.updateInstallationStatus(
                gameId = gameId,
                status = InstallationStatus.DOWNLOADING,
                progress = 0
            )
            AppLogger.d(TAG, "Updated game status to DOWNLOADING")

            // Launch Steam with -applaunch to trigger download
            // This is the official Steam command (ToS compliant)
            val launchResult = steamLauncher.launchSteam(
                containerId = containerId,
                mode = com.steamdeck.mobile.core.steam.SteamLauncher.SteamLaunchMode.GAME_LAUNCH,
                appId = steamAppId
            )

            return@withContext if (launchResult.isSuccess) {
                AppLogger.i(TAG, "Steam download initiated for appId=$steamAppId")
                DataResult.Success(Unit)
            } else {
                val error = launchResult.exceptionOrNull()
                AppLogger.e(TAG, "Failed to launch Steam for download", error)

                // Revert status on failure
                gameRepository.updateInstallationStatus(
                    gameId = gameId,
                    status = InstallationStatus.NOT_INSTALLED,
                    progress = 0
                )

                DataResult.Error(
                    AppError.Unknown(error ?: Exception("Steam launch failed"))
                )
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception while triggering game download", e)
            return@withContext DataResult.Error(AppError.from(e))
        }
    }
}
