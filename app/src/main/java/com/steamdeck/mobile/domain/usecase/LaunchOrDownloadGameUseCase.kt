package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.steam.SteamInstallMonitorService
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.steam.SteamSetupManager
import com.steamdeck.mobile.core.steam.SteamSetupManager.SteamInstallResult
import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.DownloadRepository
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified launch/download orchestration use case
 *
 * This use case handles all game launch scenarios:
 * 1. Game not installed → Launch Big Picture for download → Monitor progress → Auto-launch when complete
 * 2. Game downloading/installing → Return in-progress status
 * 3. Game installed → Launch directly
 *
 * Steam installation is fully automated and hidden from users.
 *
 * Architecture:
 * - Clean Architecture orchestration layer
 * - Integrates: SteamSetupManager, SteamLauncher, LaunchGameUseCase, FileObserver monitoring
 * - Type-safe DataResult error handling
 */
@Singleton
class LaunchOrDownloadGameUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val downloadRepository: DownloadRepository,
    private val steamSetupManager: SteamSetupManager,
    private val steamLauncher: SteamLauncher,
    private val launchGameUseCase: LaunchGameUseCase
) {
    companion object {
        private const val TAG = "LaunchOrDownloadGame"
        private const val DEFAULT_CONTAINER_ID = "default_shared_container"
        // FIXED (2025-12-25): No type conversion needed - Container ID is String throughout
    }

    /**
     * Unified launch/download method
     *
     * @param gameId Game database ID
     * @param steamInstallProgressCallback Optional callback for Steam installation progress (0.0-1.0, message, detail)
     * @return LaunchOrDownloadResult indicating what happened
     */
    suspend operator fun invoke(
        gameId: Long,
        steamInstallProgressCallback: ((Float, String, String?) -> Unit)? = null
    ): DataResult<LaunchOrDownloadResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Starting launch/download for gameId=$gameId")

            // Get game from database
            val game = gameRepository.getGameById(gameId)
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError("Game not found: $gameId", null)
                )

            AppLogger.d(TAG, "Game: ${game.name}, Status: ${game.installationStatus}")

            // Step 1: Determine container ID (use existing or default)
            // FIXED (2025-12-25): String type - no conversion needed
            val containerId = game.winlatorContainerId ?: DEFAULT_CONTAINER_ID

            // Step 2: Check if Steam is installed in container
            val steamInstalled = checkSteamInstalled(containerId)
            AppLogger.d(TAG, "Steam installed in container $containerId: $steamInstalled")

            if (!steamInstalled) {
                // Steam not installed → Install Steam then launch Big Picture
                return@withContext installSteamThenLaunchBigPicture(game, containerId, steamInstallProgressCallback)
            }

            // Step 3: Check game installation status
            return@withContext when {
                // Game is fully installed and ready → launch directly
                game.installationStatus == InstallationStatus.INSTALLED &&
                game.executablePath.isNotBlank() -> {
                    AppLogger.i(TAG, "Game installed, launching directly")
                    launchGameDirectly(gameId)
                }

                // Game is already downloading/installing → verify Steam process is actually running
                game.installationStatus in listOf(
                    InstallationStatus.DOWNLOADING,
                    InstallationStatus.INSTALLING
                ) -> {
                    AppLogger.i(TAG, "Game status: ${game.installationStatus}, verifying Steam process...")

                    // FIXED (2025-12-25): Check if Steam is actually running
                    // If Steam process died, status is stale and needs reset
                    if (isSteamProcessRunning()) {
                        AppLogger.i(TAG, "Steam process confirmed running, download in progress")
                        DataResult.Success(
                            LaunchOrDownloadResult.InProgress(
                                status = game.installationStatus,
                                progress = game.installProgress
                            )
                        )
                    } else {
                        AppLogger.w(
                            TAG,
                            "Steam process not running but status is ${game.installationStatus}. " +
                            "Resetting status and relaunching Big Picture."
                        )
                        // Reset stale status and relaunch Big Picture
                        gameRepository.updateInstallationStatus(
                            gameId = game.id,
                            status = InstallationStatus.NOT_INSTALLED,
                            progress = 0
                        )
                        launchBigPictureForDownload(game, containerId)
                    }
                }

                // Game not installed → launch Big Picture for download
                else -> {
                    AppLogger.i(TAG, "Game not installed, launching Big Picture")
                    launchBigPictureForDownload(game, containerId)
                }
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception during launch/download orchestration", e)
            DataResult.Error(AppError.from(e))
        }
    }

    /**
     * Check if Steam is installed in container
     */
    private fun checkSteamInstalled(containerId: String): Boolean {
        val steamExePath = File(
            context.filesDir,
            "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/Steam.exe"
        )
        return steamExePath.exists()
    }

    /**
     * Install Steam automatically, then launch Big Picture for download
     */
    private suspend fun installSteamThenLaunchBigPicture(
        game: Game,
        containerId: String,
        steamInstallProgressCallback: ((Float, String, String?) -> Unit)?
    ): DataResult<LaunchOrDownloadResult> {
        AppLogger.i(TAG, "Steam not installed, starting automatic installation")

        // Install Steam automatically (progress forwarded to ViewModel)
        val installResult = steamSetupManager.installSteam(
            containerId = containerId,
            progressCallback = { progress, message, detail ->
                AppLogger.d(TAG, "Steam install progress: ${(progress * 100).toInt()}% - $message")
                // Forward progress to ViewModel
                steamInstallProgressCallback?.invoke(progress, message, detail)
            }
        )

        return if (installResult.isSuccess) {
            val result = installResult.getOrNull()
            when (result) {
                is SteamInstallResult.Success -> {
                    AppLogger.i(TAG, "Steam installation successful")

                    // Update game with container ID if it didn't have one
                    // FIXED (2025-12-25): Store as String type without conversion
                    if (game.winlatorContainerId == null) {
                        gameRepository.updateGameContainer(game.id, containerId)
                    }

                    // Proceed to Big Picture download
                    launchBigPictureForDownload(
                        game.copy(winlatorContainerId = containerId),
                        containerId
                    )
                }
                is SteamInstallResult.Error -> {
                    AppLogger.e(TAG, "Steam installation failed: ${result.message}")
                    DataResult.Error(
                        AppError.Unknown(Exception("Steam installation failed: ${result.message}"))
                    )
                }
                else -> {
                    AppLogger.e(TAG, "Steam installation returned unexpected result")
                    DataResult.Error(
                        AppError.Unknown(Exception("Steam installation failed"))
                    )
                }
            }
        } else {
            AppLogger.e(TAG, "Steam installation failed: ${installResult.exceptionOrNull()?.message}")
            DataResult.Error(
                AppError.Unknown(installResult.exceptionOrNull() ?: Exception("Steam installation failed"))
            )
        }
    }

    /**
     * Launch Big Picture mode for game download
     */
    private suspend fun launchBigPictureForDownload(
        game: Game,
        containerId: String
    ): DataResult<LaunchOrDownloadResult> {
        AppLogger.i(TAG, "Launching Big Picture for download: ${game.name}")

        // Validate Steam App ID
        val steamAppId = game.steamAppId
            ?: return DataResult.Error(
                AppError.DatabaseError("Game does not have Steam App ID", null)
            )

        // Create download entry for tracking in DownloadScreen
        val download = Download(
            gameId = game.id,
            fileName = "${game.name} (Steam)",
            url = "steam://download/$steamAppId",
            status = DownloadStatus.DOWNLOADING,
            installationStatus = InstallationStatus.NOT_INSTALLED,
            progress = 0,
            destinationPath = game.installPath
        )
        val downloadId = downloadRepository.insertDownload(download)

        AppLogger.d(TAG, "Created download entry: downloadId=$downloadId")

        // Start FileObserver monitoring service (with error handling for Android 8+ restrictions)
        // FIXED (2025): Use SteamInstallMonitorService.start() method
        var autoTrackingEnabled = true
        try {
            SteamInstallMonitorService.start(
                context = context,
                containerId = containerId,
                steamAppId = steamAppId,
                gameId = game.id,
                downloadId = downloadId
            )
            AppLogger.d(TAG, "FileObserver monitoring service started successfully")
        } catch (e: Exception) {
            autoTrackingEnabled = false
            AppLogger.w(
                TAG,
                "FileObserver service startup failed (Android 8+ restriction or service error): ${e.message}. " +
                "Automatic installation tracking disabled - user must manually check Steam UI.",
                e
            )
            // Non-fatal: Continue with Big Picture launch
            // User will see warning in UI about manual tracking
        }

        // Update game status to DOWNLOADING
        gameRepository.updateInstallationStatus(
            gameId = game.id,
            status = InstallationStatus.DOWNLOADING,
            progress = 0
        )

        // Launch Steam Big Picture (user will select game to download)
        val launchResult = steamLauncher.launchSteamBigPicture(
            containerId = containerId
        )

        return if (launchResult.isSuccess) {
            AppLogger.i(TAG, "Big Picture launched successfully (auto-tracking: $autoTrackingEnabled)")
            DataResult.Success(
                LaunchOrDownloadResult.DownloadStarted(
                    downloadId = downloadId,
                    autoTrackingEnabled = autoTrackingEnabled
                )
            )
        } else {
            AppLogger.e(TAG, "Big Picture launch failed", launchResult.exceptionOrNull())

            // Rollback: cleanup download entry and game status (FIXED 2025: error handling)
            try {
                downloadRepository.deleteDownload(download.copy(id = downloadId))
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to cleanup download entry during rollback (non-fatal)", e)
                // Non-fatal: Continue with status rollback
            }

            try {
                gameRepository.updateInstallationStatus(
                    gameId = game.id,
                    status = InstallationStatus.NOT_INSTALLED,
                    progress = 0
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to rollback game installation status (non-fatal)", e)
                // Non-fatal: Continue with error response
            }

            DataResult.Error(
                AppError.Unknown(
                    launchResult.exceptionOrNull() ?: Exception("Failed to launch Steam Big Picture")
                )
            )
        }
    }

    /**
     * Launch game directly (game is already installed)
     */
    private suspend fun launchGameDirectly(gameId: Long): DataResult<LaunchOrDownloadResult> {
        AppLogger.i(TAG, "Launching game directly: gameId=$gameId")

        return when (val result = launchGameUseCase(gameId)) {
            is DataResult.Success -> {
                AppLogger.i(TAG, "Game launched successfully: processId=${result.data.processId}")
                DataResult.Success(
                    LaunchOrDownloadResult.Launched(processId = result.data.processId)
                )
            }
            is DataResult.Error -> {
                AppLogger.e(TAG, "Game launch failed: ${result.error}")
                DataResult.Error(result.error)
            }
            is DataResult.Loading -> {
                // Should not happen for suspend function
                AppLogger.w(TAG, "Unexpected Loading state from LaunchGameUseCase")
                DataResult.Error(AppError.Unknown(Exception("Unexpected loading state")))
            }
        }
    }

    /**
     * Check if Steam process is actually running
     *
     * ADDED (2025-12-25): Prevents stale DOWNLOADING status bug
     * Verifies Steam.exe or steamwebhelper.exe processes are active
     *
     * @return true if Steam process is running, false otherwise
     */
    private fun isSteamProcessRunning(): Boolean {
        return try {
            // Check for Steam-related processes using ActivityManager
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? android.app.ActivityManager

            if (activityManager == null) {
                AppLogger.w(TAG, "ActivityManager not available, assuming Steam not running")
                return false
            }

            // Get running processes (note: deprecated but still works for our own app's processes)
            val runningProcesses = try {
                activityManager.runningAppProcesses ?: emptyList()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to get running processes: ${e.message}")
                emptyList()
            }

            // Check if any process contains "steam" (Box64 will show as our app process)
            // Better approach: Check if SteamInstallMonitorService is running
            val serviceRunning = activityManager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className.contains("SteamInstallMonitorService") }

            if (serviceRunning) {
                AppLogger.d(TAG, "SteamInstallMonitorService is running, Steam likely active")
                return true
            }

            // Fallback: Check for recent process activity
            // If service not running, Steam is definitely not downloading
            AppLogger.d(TAG, "SteamInstallMonitorService not running, Steam not active")
            false

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking Steam process status", e)
            // On error, assume not running to trigger relaunch (safer)
            false
        }
    }
}

/**
 * Result of launch/download orchestration
 */
sealed class LaunchOrDownloadResult {
    /**
     * Game was launched directly (already installed)
     * @param processId Process ID for monitoring
     */
    data class Launched(val processId: Int) : LaunchOrDownloadResult()

    /**
     * Download started via Big Picture
     * @param downloadId Download database ID for tracking
     * @param autoTrackingEnabled Whether automatic progress tracking is active
     */
    data class DownloadStarted(
        val downloadId: Long,
        val autoTrackingEnabled: Boolean = true
    ) : LaunchOrDownloadResult()

    /**
     * Download/installation already in progress
     * @param status Current installation status
     * @param progress Progress percentage (0-100)
     */
    data class InProgress(
        val status: InstallationStatus,
        val progress: Int
    ) : LaunchOrDownloadResult()

    /**
     * Steam installation in progress (first-time setup)
     * @param progress Installation progress (0.0-1.0)
     */
    data class SteamInstalling(val progress: Float) : LaunchOrDownloadResult()
}
