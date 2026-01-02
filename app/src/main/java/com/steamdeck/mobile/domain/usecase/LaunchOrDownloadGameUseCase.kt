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

        // Launch Steam in background mode (no UI, automatic download handling)
        val launchResult = steamLauncher.launchSteam(
            containerId = containerId,
            mode = com.steamdeck.mobile.core.steam.SteamLauncher.SteamLaunchMode.BACKGROUND
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

            // FIXED (2025-12-26): Improved rollback error handling
            // Try to cleanup but track failures to report comprehensive error
            val rollbackErrors = mutableListOf<String>()

            // Rollback 1: Delete download entry
            try {
                downloadRepository.deleteDownload(download.copy(id = downloadId))
                AppLogger.d(TAG, "Rollback: Download entry deleted successfully")
            } catch (e: Exception) {
                val errorMsg = "Failed to cleanup download entry: ${e.message}"
                AppLogger.w(TAG, errorMsg, e)
                rollbackErrors.add(errorMsg)
            }

            // Rollback 2: Reset game installation status
            try {
                gameRepository.updateInstallationStatus(
                    gameId = game.id,
                    status = InstallationStatus.NOT_INSTALLED,
                    progress = 0
                )
                AppLogger.d(TAG, "Rollback: Game status reset successfully")
            } catch (e: Exception) {
                val errorMsg = "Failed to rollback game installation status: ${e.message}"
                AppLogger.w(TAG, errorMsg, e)
                rollbackErrors.add(errorMsg)
            }

            // Build comprehensive error message
            val primaryError = launchResult.exceptionOrNull() ?: Exception("Failed to launch Steam Big Picture")
            val errorMessage = if (rollbackErrors.isNotEmpty()) {
                "Launch failed: ${primaryError.message}. Rollback errors: ${rollbackErrors.joinToString("; ")}"
            } else {
                primaryError.message ?: "Failed to launch Steam Big Picture"
            }

            DataResult.Error(
                AppError.Unknown(Exception(errorMessage, primaryError))
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
     * FIXED (2025-12-26): Reliable Steam process detection with proper error handling
     * - Uses /proc filesystem to detect Wine/Box64 processes
     * - Checks for Steam.exe, steamwebhelper.exe in process cmdline
     * - Fallback to SteamInstallMonitorService check
     * - Smart error handling: FileNotFoundException vs SecurityException
     *
     * @return true if Steam process is running, false otherwise
     */
    private fun isSteamProcessRunning(): Boolean {
        return try {
            // FIXED (2025-12-26): Android 13+ restricts /proc access, skip if detected
            // Android 13 (API 33) restricts access to /proc for privacy
            // ~80-90% of processes will throw SecurityException on Android 13+
            val isAndroid13Plus = android.os.Build.VERSION.SDK_INT >= 33 // API 33 = Android 13

            if (isAndroid13Plus) {
                AppLogger.d(TAG, "Android 13+ detected, skipping /proc check (restricted)")
                // Fall through to Method 2 (service check)
            } else {
                // Method 1: Check /proc filesystem for Steam processes (Android 12 and below)
                // This works on Android 8-12 where getRunningServices is restricted
                val procDir = File("/proc")
                if (procDir.exists() && procDir.isDirectory) {
                    val steamProcessFound = procDir.listFiles()?.any { processDir ->
                        if (!processDir.isDirectory) return@any false

                        try {
                            val cmdlineFile = File(processDir, "cmdline")
                            if (!cmdlineFile.exists() || !cmdlineFile.canRead()) {
                                return@any false
                            }

                            val cmdline = cmdlineFile.readText().lowercase()
                            // Check for Steam-related executables
                            cmdline.contains("steam.exe") ||
                            cmdline.contains("steamwebhelper") ||
                            cmdline.contains("steamservice")
                        } catch (e: Exception) {
                            // Process check failed (terminated or permission denied)
                            false
                        }
                    } ?: false

                    if (steamProcessFound) {
                        AppLogger.d(TAG, "Steam process detected via /proc filesystem")
                        return true
                    }
                }
            }

            // Method 2: Check if SteamInstallMonitorService is running
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? android.app.ActivityManager

            if (activityManager != null) {
                try {
                    val serviceRunning = activityManager.getRunningServices(Integer.MAX_VALUE)
                        .any { it.service.className.contains("SteamInstallMonitorService") }

                    if (serviceRunning) {
                        AppLogger.d(TAG, "SteamInstallMonitorService is running, Steam likely active")
                        return true
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to check running services (Android 8+ restriction): ${e.message}")
                    // Non-fatal: continue to next check
                }
            }

            // No Steam process detected
            AppLogger.d(TAG, "No Steam process detected")
            false

        } catch (e: Exception) {
            // FIXED (2025-12-26): Improved error handling - conservative approach
            // Rationale: If we can't detect Steam reliably, user should re-launch manually
            when (e) {
                is java.io.FileNotFoundException -> {
                    // /proc directory not found - unusual but assume NOT running
                    AppLogger.w(TAG, "Process check failed (/proc unavailable): ${e.message}")
                    false
                }
                is SecurityException -> {
                    // FIXED (2025-12-26): Global /proc access denied
                    // This is different from per-process SecurityException (handled above)
                    // If we can't access /proc at all, rely on service check result
                    AppLogger.w(TAG, "Global /proc access denied, relying on service check: ${e.message}")
                    // Service check already completed above - if we reached here, service not found
                    false
                }
                else -> {
                    // Unknown error - assume NOT running (safer for UX)
                    // User will see "Not Installed" and can manually re-trigger download
                    AppLogger.e(TAG, "Unknown error checking Steam process status (assuming not running)", e)
                    false
                }
            }
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
