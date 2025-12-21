package com.steamdeck.mobile.domain.usecase

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.core.error.AppError
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

/**
 * Trigger Steam game download/installation use case
 *
 * Starts Steam game download via `steam.exe -applaunch <appId>` command
 * and launches FileObserver service to monitor installation progress.
 *
 * Flow:
 * 1. Validate game has Steam App ID
 * 2. Get Wine container for the game
 * 3. Verify Steam.exe exists in container
 * 4. Start SteamInstallMonitorService (FileObserver)
 * 5. Launch steam.exe -applaunch <appId> (auto-triggers download)
 * 6. Update game status to DOWNLOADING
 *
 * Steam ToS Compliance:
 * - Uses official Steam client (no protocol emulation)
 * - Respects Steam's download infrastructure
 */
class TriggerGameDownloadUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
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
            Log.i(TAG, "Triggering game download for gameId=$gameId")

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

            Log.d(TAG, "Game: ${game.name}, Steam App ID: $steamAppId")

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

            Log.d(TAG, "Steam.exe found at: ${steamExePath.absolutePath}")

            // 5. Start FileObserver monitoring service
            try {
                SteamInstallMonitorService.start(
                    context = context,
                    containerId = containerId,
                    steamAppId = steamAppId,
                    gameId = gameId
                )
                Log.i(TAG, "Started SteamInstallMonitorService for appId=$steamAppId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start monitoring service", e)
                return@withContext DataResult.Error(
                    AppError.Unknown(e)
                )
            }

            // 6. Update game status to DOWNLOADING (optimistic update)
            gameRepository.updateInstallationStatus(
                gameId = gameId,
                status = InstallationStatus.DOWNLOADING,
                progress = 0
            )

            // 7. Launch steam.exe -applaunch <appId>
            // This will auto-trigger download if game is not installed
            val launchResult = steamLauncher.launchGameViaSteam(
                containerId = containerId,
                appId = steamAppId
            )

            if (launchResult.isFailure) {
                // Rollback status update
                gameRepository.updateInstallationStatus(
                    gameId = gameId,
                    status = InstallationStatus.NOT_INSTALLED,
                    progress = 0
                )

                return@withContext DataResult.Error(
                    AppError.Unknown(
                        launchResult.exceptionOrNull() ?: Exception("Failed to start Steam download")
                    )
                )
            }

            Log.i(TAG, "Successfully triggered download for: ${game.name}")
            DataResult.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger game download", e)
            DataResult.Error(AppError.from(e))
        }
    }
}
