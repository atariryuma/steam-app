package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.SteamInstallMonitorService
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Trigger Steam game download/installation use case
 *
 * Launches Steam Big Picture mode where users can browse and install games,
 * and starts FileObserver service to monitor installation progress.
 *
 * Flow:
 * 1. Validate game has Steam App ID
 * 2. Get Wine container for the game
 * 3. Verify Steam.exe exists in container
 * 4. Create DownloadEntity for DownloadScreen tracking
 * 5. Start SteamInstallMonitorService (FileObserver)
 * 6. Update GameEntity status to DOWNLOADING
 * 7. Launch Steam Big Picture mode (fullscreen console-like UI)
 *
 * Steam ToS Compliance:
 * - Uses official Steam client Big Picture mode
 * - Users manually select games to download (respects Steam UX)
 * - Respects Steam's download infrastructure
 */
class TriggerGameDownloadUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val downloadRepository: DownloadRepository,
    private val steamLauncher: SteamLauncher
) {
    companion object {
        private const val TAG = "TriggerGameDownload"
    }

    /**
     * Trigger game download
     *
     * @param gameId Game database ID
     * @return DataResult.Success if download started, DataResult.Error otherwise
     */
    suspend operator fun invoke(gameId: Long): DataResult<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Triggering game download for gameId=$gameId")

            // 1. Get game from database
            val game = gameRepository.getGameById(gameId)
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError("Game not found: $gameId", null)
                )

            // 2. Validate Steam App ID
            val steamAppId = game.steamAppId
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError("Game does not have Steam App ID", null)
                )

            AppLogger.d(TAG, "Game: ${game.name}, Steam App ID: $steamAppId")

            // 3. Validate Wine container ID
            val containerId = game.winlatorContainerId?.toString()
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError(
                        "This game was synced before Steam container was created. " +
                        "Please re-sync your Steam library in Settings.",
                        null
                    )
                )

            // 4. Verify Steam.exe exists in container
            val steamExePath = File(
                context.filesDir,
                "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/Steam.exe"
            )

            if (!steamExePath.exists()) {
                return@withContext DataResult.Error(
                    AppError.FileError("Steam client not found in container. Please install Steam first.", null)
                )
            }

            AppLogger.d(TAG, "Steam.exe found at: ${steamExePath.absolutePath}")

            // 5. Create DownloadEntity for tracking in DownloadScreen (start as DOWNLOADING)
            val download = Download(
                gameId = gameId,
                fileName = "${game.name} (Steam)",
                url = "steam://download/$steamAppId",
                status = DownloadStatus.DOWNLOADING,  // Start as DOWNLOADING (no PENDING state)
                installationStatus = InstallationStatus.NOT_INSTALLED,
                progress = 0,
                downloadedBytes = 0,
                totalBytes = 0, // Steam doesn't provide total size upfront
                destinationPath = steamExePath.parent ?: "",
                startedTimestamp = System.currentTimeMillis()
            )
            val downloadId = downloadRepository.insertDownload(download)
            AppLogger.d(TAG, "Created download entry: downloadId=$downloadId for ${game.name}")

            // 6. Start FileObserver monitoring service
            try {
                SteamInstallMonitorService.start(
                    context = context,
                    containerId = containerId,
                    steamAppId = steamAppId,
                    gameId = gameId,
                    downloadId = downloadId
                )
                AppLogger.i(TAG, "Started SteamInstallMonitorService for appId=$steamAppId, downloadId=$downloadId")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start monitoring service", e)
                // Clean up download entry (ignore cleanup errors)
                try {
                    downloadRepository.deleteDownload(download.copy(id = downloadId))
                } catch (deleteError: Exception) {
                    AppLogger.w(TAG, "Failed to clean up download entry", deleteError)
                }
                return@withContext DataResult.Error(
                    AppError.Unknown(e)
                )
            }

            // 7. Update game status to DOWNLOADING
            gameRepository.updateInstallationStatus(
                gameId = gameId,
                status = InstallationStatus.DOWNLOADING,
                progress = 0
            )

            // 8. Launch Steam Big Picture mode
            // This opens fullscreen Steam UI where users can browse and install games
            val launchResult = steamLauncher.launchSteamBigPicture(
                containerId = containerId
            )

            if (launchResult.isFailure) {
                // Rollback status updates
                gameRepository.updateInstallationStatus(
                    gameId = gameId,
                    status = InstallationStatus.NOT_INSTALLED,
                    progress = 0
                )
                downloadRepository.markDownloadError(
                    downloadId = downloadId,
                    status = DownloadStatus.FAILED,
                    errorMessage = "Failed to start Steam download: ${launchResult.exceptionOrNull()?.message}"
                )

                return@withContext DataResult.Error(
                    AppError.Unknown(
                        launchResult.exceptionOrNull() ?: Exception("Failed to start Steam download")
                    )
                )
            }

            AppLogger.i(TAG, "Successfully triggered download for: ${game.name}")
            DataResult.Success(Unit)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to trigger game download", e)
            DataResult.Error(AppError.from(e))
        }
    }
}
