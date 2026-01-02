package com.steamdeck.mobile.core.steam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.steamdeck.mobile.R
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.domain.model.DownloadStatus
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.DownloadRepository
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Steam game installation monitor service
 *
 * Monitors steamapps/ directory using FileObserver to detect when games finish downloading.
 * Runs as a foreground service to ensure persistent monitoring during downloads.
 *
 * Technical details:
 * - FileObserver uses Linux inotify (kernel-level events, CPU <1%)
 * - Monitors: appmanifest_*.acf files (CREATE, MODIFY events)
 * - Auto-stops after 2 hours if installation not detected
 * - Updates game installation status in database
 */
@AndroidEntryPoint
class SteamInstallMonitorService : Service() {

    companion object {
        private const val TAG = "SteamInstallMonitor"
        private const val NOTIFICATION_CHANNEL_ID = "steam_install_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val TIMEOUT_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
        private const val MIN_NOTIFICATION_UPDATE_INTERVAL_MS = 500L // Base interval
        private const val MAX_NOTIFICATION_UPDATE_INTERVAL_MS = 8000L // Max 8 seconds

        const val EXTRA_CONTAINER_ID = "container_id"
        const val EXTRA_STEAM_APP_ID = "steam_app_id"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        /**
         * Start monitoring service
         */
        fun start(
            context: Context,
            containerId: String,
            steamAppId: Long,
            gameId: Long,
            downloadId: Long
        ) {
            val intent = Intent(context, SteamInstallMonitorService::class.java).apply {
                putExtra(EXTRA_CONTAINER_ID, containerId)
                putExtra(EXTRA_STEAM_APP_ID, steamAppId)
                putExtra(EXTRA_GAME_ID, gameId)
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }
    }

    @Inject
    lateinit var gameRepository: GameRepository

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObserver: RecursiveFileObserver? = null
    private var containerId: String? = null
    private var steamAppId: Long? = null
    private var gameId: Long? = null
    private var downloadId: Long? = null

    // FIXED (2025-12-26): Store timeout job reference to prevent memory leak
    private var timeoutJob: Job? = null

    // FIXED (2025-12-26): Notification update synchronization with exponential backoff
    private val notificationLock = Any()
    private var lastNotificationUpdateTime = 0L
    private var consecutiveSecurityExceptions = 0
    private var currentUpdateInterval = 500L // Start at 500ms

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground service immediately (Android 8+ requirement)
        startForeground(NOTIFICATION_ID, createNotification("Monitoring installation..."))

        intent?.let {
            containerId = it.getStringExtra(EXTRA_CONTAINER_ID)
            steamAppId = it.getLongExtra(EXTRA_STEAM_APP_ID, -1L)
            gameId = it.getLongExtra(EXTRA_GAME_ID, -1L)
            downloadId = it.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)

            AppLogger.i(TAG, "Service started: containerId=$containerId, steamAppId=$steamAppId, gameId=$gameId, downloadId=$downloadId")

            // Copy mutable properties to local variables for smart cast
            val localContainerId = containerId
            val localSteamAppId = steamAppId
            val localGameId = gameId
            val localDownloadId = downloadId

            if (localContainerId != null && localSteamAppId != null && localSteamAppId != -1L && localGameId != null && localGameId != -1L && localDownloadId != null && localDownloadId != -1L) {
                startMonitoring(localContainerId, localSteamAppId, localGameId, localDownloadId)
            } else {
                AppLogger.e(TAG, "Invalid parameters: containerId=$containerId, steamAppId=$steamAppId, gameId=$gameId, downloadId=$downloadId")
                stopSelf()
            }
        } ?: run {
            AppLogger.e(TAG, "No intent provided")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.d(TAG, "Service destroyed - starting cleanup")

        // FIXED (2025-12-26): Comprehensive cleanup with job cancellation
        // Critical: Must cancel all jobs before cancelling scope to prevent zombie coroutines

        // Step 1: Stop FileObserver first to prevent new events
        // FIXED (2025-12-26): Use new stopWatchingAndCleanup() to prevent race condition
        fileObserver?.stopWatchingAndCleanup()
        AppLogger.d(TAG, "FileObserver stopped and cleaned up")

        // Step 2: Cancel timeout job (prevents 2-hour delayed error marking)
        timeoutJob?.cancel()
        timeoutJob = null
        AppLogger.d(TAG, "Timeout job cancelled")

        // Step 3: Cancel all coroutines in service scope
        // This cancels: startMonitoring, handleInstallComplete, handleManifestChange
        serviceScope.cancel()
        AppLogger.d(TAG, "Service scope cancelled - all cleanup complete")
    }

    /**
     * Start monitoring steamapps directory
     */
    private fun startMonitoring(containerId: String, steamAppId: Long, gameId: Long, downloadId: Long) {
        serviceScope.launch {
            try {
                // Build steamapps directory path
                // Path: <internal storage>/winlator/containers/<containerId>/drive_c/Program Files (x86)/Steam/steamapps/
                val steamappsDir = File(
                    filesDir,
                    "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/steamapps"
                )


                if (!steamappsDir.exists()) {
                    AppLogger.e(TAG, "Steamapps directory not found: ${steamappsDir.absolutePath}")

                    // Mark download as failed
                    downloadRepository.markDownloadError(
                        downloadId = downloadId,
                        status = DownloadStatus.FAILED,
                        errorMessage = "Steamapps directory not found"
                    )

                    stopSelf()
                    return@launch
                }

                AppLogger.i(TAG, "Starting FileObserver on: ${steamappsDir.absolutePath}")
                AppLogger.i(TAG, "Monitoring for Steam App ID: $steamAppId, downloadId: $downloadId")

                // List existing files in steamapps directory
                val existingFiles = steamappsDir.listFiles()?.map { it.name } ?: emptyList()

                // Create and start FileObserver
                fileObserver = RecursiveFileObserver(
                    path = steamappsDir.absolutePath,
                    steamAppId = steamAppId,
                    gameId = gameId,
                    downloadId = downloadId,
                    onInstallComplete = { manifest ->
                        handleInstallComplete(manifest, gameId, downloadId)
                    },
                    onProgressUpdate = { progress ->
                        updateNotification("Installing game... $progress%")
                        // Update download progress
                        serviceScope.launch {
                            downloadRepository.updateDownloadProgress(
                                downloadId = downloadId,
                                progress = progress,
                                downloadedBytes = 0, // Steam doesn't provide byte progress
                                status = DownloadStatus.DOWNLOADING
                            )
                        }
                    }
                )
                fileObserver?.startWatching()

                // FIXED (2025-12-26): Store timeout job reference for proper cancellation
                // Set timeout to auto-stop service after 2 hours
                timeoutJob = launch {
                    delay(TIMEOUT_MILLIS)
                    AppLogger.w(TAG, "Timeout reached (2 hours), stopping service")

                    // Mark download as failed due to timeout
                    downloadRepository.markDownloadError(
                        downloadId = downloadId,
                        status = DownloadStatus.FAILED,
                        errorMessage = "Installation timeout (2 hours)"
                    )

                    stopSelf()
                }

            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start monitoring", e)

                // Mark download as failed
                downloadRepository.markDownloadError(
                    downloadId = downloadId,
                    status = DownloadStatus.FAILED,
                    errorMessage = "Failed to start monitoring: ${e.message}"
                )

                stopSelf()
            }
        }
    }

    /**
     * Handle installation complete event
     *
     * FIXED (2025-12-26): Added lifecycle safety with isActive check
     * - Prevents zombie service if app/service destroyed during 5s delay
     * - Cancels delay if service already stopped
     */
    private fun handleInstallComplete(manifest: AppManifest, gameId: Long, downloadId: Long) {
        serviceScope.launch {
            try {
                AppLogger.i(TAG, "Installation complete: ${manifest.name} (appId=${manifest.appId})")

                // Update game installation status
                gameRepository.updateInstallationStatus(
                    gameId = gameId,
                    status = InstallationStatus.INSTALLED,
                    progress = 100
                )

                // Mark download as completed
                downloadRepository.markDownloadCompleted(
                    downloadId = downloadId,
                    status = DownloadStatus.COMPLETED,
                    completedTimestamp = System.currentTimeMillis()
                )

                // Show completion notification
                updateNotification("${manifest.name} is ready to play!")

                // FIXED (2025-12-26): Check if coroutine is still active before delay
                // Stop service after 5 seconds (allow user to see notification)
                if (isActive) {
                    delay(5000)
                    if (isActive) {
                        stopSelf()
                    }
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    AppLogger.d(TAG, "Install complete handler cancelled (service destroyed)")
                    throw e  // Re-throw to propagate cancellation
                } else {
                    AppLogger.e(TAG, "Failed to handle install complete", e)
                }
            }
        }
    }

    /**
     * Create notification channel (Android 8+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Game Installation Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors Steam game downloads and installations"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification
     */
    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Steam Game Installation")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * Update notification message
     *
     * FIXED (2025-12-26): Rate-limited notification updates to prevent Android 8+ restrictions
     * - Prevents SecurityException from high-frequency updates
     * - Limits updates to max 2 per second (500ms interval)
     * - Thread-safe with synchronized block
     */
    private fun updateNotification(message: String) {
        synchronized(notificationLock) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastUpdate = currentTime - lastNotificationUpdateTime

            // FIXED (2025-12-26): Use dynamic interval with exponential backoff
            if (timeSinceLastUpdate < currentUpdateInterval) {
                AppLogger.d(TAG, "Skipping notification update (rate limit: ${timeSinceLastUpdate}ms < ${currentUpdateInterval}ms)")
                return
            }

            try {
                val notification = createNotification(message)
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIFICATION_ID, notification)
                lastNotificationUpdateTime = currentTime

                // FIXED (2025-12-26): Reset backoff on success
                consecutiveSecurityExceptions = 0
                currentUpdateInterval = MIN_NOTIFICATION_UPDATE_INTERVAL_MS
                AppLogger.d(TAG, "Notification updated: $message")
            } catch (e: SecurityException) {
                // FIXED (2025-12-26): Exponential backoff on SecurityException
                // Android 10+ can throw this under load or in Doze mode
                consecutiveSecurityExceptions++
                currentUpdateInterval = minOf(
                    MIN_NOTIFICATION_UPDATE_INTERVAL_MS * (1 shl consecutiveSecurityExceptions), // 2^n backoff
                    MAX_NOTIFICATION_UPDATE_INTERVAL_MS
                )
                AppLogger.w(TAG, "Notification update failed (SecurityException #${consecutiveSecurityExceptions}), backing off to ${currentUpdateInterval}ms")
            } catch (e: Exception) {
                // Non-fatal: Log error but continue monitoring
                AppLogger.w(TAG, "Failed to update notification: ${e.message}")
            }
        }
    }


    /**
     * Recursive FileObserver for monitoring steamapps directory
     *
     * Monitors appmanifest_*.acf files for CREATE and MODIFY events
     *
     * FIXED (2025-12-26): Comprehensive concurrent event handling
     * - Uses synchronized block to prevent race conditions during Job check
     * - Cancels AND joins previous job to ensure proper cleanup
     * - Prevents multiple coroutines from accessing the same file concurrently
     * - CRITICAL FIX: Tracks stopped state to prevent processing queued events after stopWatching()
     */
    private inner class RecursiveFileObserver(
        private val path: String,
        private val steamAppId: Long,
        private val gameId: Long,
        private val downloadId: Long,
        private val onInstallComplete: (AppManifest) -> Unit,
        private val onProgressUpdate: (Int) -> Unit
    ) : FileObserver(path, FileObserver.CREATE or FileObserver.MODIFY) {

        // FIXED (2025-12-26): Track ongoing manifest parsing to prevent concurrent execution
        private var manifestParsingJob: Job? = null
        private val jobLock = Any() // Synchronization lock for Job access

        // FIXED (2025-12-26): Use AtomicBoolean for thread-safe stopped flag
        // Bug: @Volatile ensures atomicity but not memory barrier ordering
        // Impact: Kernel inotify thread may read stale `false` from cache after stopWatching()
        // Result: Rare crash "Cannot access database, already closed"
        // Fix: AtomicBoolean guarantees visibility across threads
        private val isStopped = java.util.concurrent.atomic.AtomicBoolean(false)

        /**
         * Stop watching and cancel all ongoing jobs
         * FIXED (2025-12-26): Sets isStopped flag BEFORE calling super.stopWatching()
         */
        fun stopWatchingAndCleanup() {
            synchronized(jobLock) {
                isStopped.set(true) // FIXED: AtomicBoolean with memory barrier
                manifestParsingJob?.cancel() // Cancel ongoing parsing job
                stopWatching() // Release inotify file descriptor
            }
        }

        override fun onEvent(event: Int, path: String?) {
            // FIXED (2025-12-26): Early exit if observer has been stopped
            // Prevents processing kernel-buffered inotify events after stopWatching()
            if (isStopped.get() || path == null) return // FIXED: AtomicBoolean.get()

            try {

                // Check if this is an appmanifest file for our game
                if (path.startsWith("appmanifest_") && path.endsWith(".acf")) {
                    val manifestFile = File(this.path, path)

                    // Extract appId from filename (e.g., appmanifest_730.acf -> 730)
                    val fileAppId = path
                        .removePrefix("appmanifest_")
                        .removeSuffix(".acf")
                        .toLongOrNull()


                    if (fileAppId == steamAppId) {
                        AppLogger.d(TAG, "Detected event on target manifest: $path (event=$event)")

                        // FIXED (2025-12-26): Synchronized check to prevent race condition
                        // Multiple events can arrive during the delay(500) window
                        synchronized(jobLock) {
                            if (manifestParsingJob?.isActive == true) {
                                AppLogger.d(TAG, "Skipping event: manifest parsing already in progress")
                                return
                            }

                            // Start new parsing job immediately to block subsequent events
                            handleManifestChange(manifestFile)
                        }
                    } else {
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling file event: $path", e)
            }
        }

        /**
         * Handle manifest file change
         *
         * FIXED (2025-12-26): Comprehensive error handling and lifecycle safety
         * - Cancels previous job with join() to ensure cleanup completes
         * - Handles file deletion/permission errors gracefully
         * - Checks service lifecycle before accessing repositories
         * - CRITICAL FIX: Checks if serviceScope is active before launching coroutine
         */
        private fun handleManifestChange(manifestFile: File) {
            // FIXED (2025-12-26): Check if service is still active before launching coroutine
            // Prevents launching coroutines after service is destroyed
            if (!serviceScope.isActive) {
                AppLogger.d(TAG, "Service scope cancelled, skipping manifest parsing for ${manifestFile.name}")
                return
            }

            // Cancel previous job and wait for cleanup (called within synchronized block)
            val oldJob = manifestParsingJob
            manifestParsingJob = serviceScope.launch {
                try {
                    // FIXED (2025-12-26): Wait for old job to fully complete cancellation
                    // This prevents two jobs from accessing the same file concurrently
                    oldJob?.cancelAndJoin()

                    // Wait a bit to ensure file write is complete
                    delay(500)

                    // FIXED (2025-12-26): Check if service is still active before file access
                    if (!isActive) {
                        AppLogger.d(TAG, "Service destroyed during delay, aborting manifest parsing")
                        return@launch
                    }

                    if (!manifestFile.exists() || !manifestFile.canRead()) {
                        AppLogger.w(TAG, "Manifest file not readable: ${manifestFile.absolutePath}")
                        return@launch
                    }


                    // Parse manifest
                    val result = AppManifestParser.parse(manifestFile)
                    if (result.isFailure) {
                        AppLogger.e(TAG, "Failed to parse manifest: ${result.exceptionOrNull()?.message}")
                        return@launch
                    }

                    val manifest = result.getOrThrow()
                    AppLogger.d(TAG, "Parsed manifest: appId=${manifest.appId}, name=${manifest.name}, stateFlags=${manifest.stateFlags}")

                    // FIXED (2025-12-26): Check service lifecycle before repository access
                    if (!isActive) {
                        AppLogger.d(TAG, "Service destroyed during parsing, skipping repository updates")
                        return@launch
                    }

                    when (manifest.stateFlags) {
                        2 -> {
                            // Downloading
                            AppLogger.d(TAG, "Game is downloading: ${manifest.name}")
                            gameRepository.updateInstallationStatus(
                                gameId = gameId,
                                status = InstallationStatus.DOWNLOADING,
                                progress = 50 // Approximate progress
                            )
                            // onProgressUpdate will update downloadRepository (avoid double update)
                            onProgressUpdate(50)
                        }
                        4 -> {
                            // Fully installed
                            AppLogger.i(TAG, "Game fully installed: ${manifest.name}")
                            onInstallComplete(manifest)
                        }
                        else -> {
                            val stateDesc = AppManifestParser.getStateDescription(manifest.stateFlags)
                            AppLogger.d(TAG, "Game state: $stateDesc")
                        }
                    }

                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        AppLogger.d(TAG, "Manifest parsing cancelled (expected during cleanup)")
                        throw e // Re-throw to propagate cancellation
                    } else {
                        AppLogger.e(TAG, "Error handling manifest change", e)
                    }
                }
            }
        }
    }
}
