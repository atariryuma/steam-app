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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.steamdeck.mobile.R
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.GameRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        private const val LOG_FILE_NAME = "steam-install-realtime.txt"

        const val EXTRA_CONTAINER_ID = "container_id"
        const val EXTRA_STEAM_APP_ID = "steam_app_id"
        const val EXTRA_GAME_ID = "game_id"

        /**
         * Start monitoring service
         */
        fun start(
            context: Context,
            containerId: String,
            steamAppId: Long,
            gameId: Long
        ) {
            val intent = Intent(context, SteamInstallMonitorService::class.java).apply {
                putExtra(EXTRA_CONTAINER_ID, containerId)
                putExtra(EXTRA_STEAM_APP_ID, steamAppId)
                putExtra(EXTRA_GAME_ID, gameId)
            }
            context.startForegroundService(intent)
        }
    }

    @Inject
    lateinit var gameRepository: GameRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObserver: RecursiveFileObserver? = null
    private var containerId: String? = null
    private var steamAppId: Long? = null
    private var gameId: Long? = null
    private var logFile: File? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")

        // Initialize real-time log file
        initializeLogFile()

        // Start foreground service immediately (Android 8+ requirement)
        startForeground(NOTIFICATION_ID, createNotification("Monitoring installation..."))

        intent?.let {
            containerId = it.getStringExtra(EXTRA_CONTAINER_ID)
            steamAppId = it.getLongExtra(EXTRA_STEAM_APP_ID, -1L)
            gameId = it.getLongExtra(EXTRA_GAME_ID, -1L)

            writeLog("Service started with params: containerId=$containerId, steamAppId=$steamAppId, gameId=$gameId")

            if (containerId != null && steamAppId != null && steamAppId != -1L && gameId != null && gameId != -1L) {
                startMonitoring(containerId!!, steamAppId!!, gameId!!)
            } else {
                writeLog("ERROR: Invalid parameters")
                Log.e(TAG, "Invalid parameters: containerId=$containerId, steamAppId=$steamAppId, gameId=$gameId")
                stopSelf()
            }
        } ?: run {
            writeLog("ERROR: No intent provided")
            Log.e(TAG, "No intent provided")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        fileObserver?.stopWatching()
        serviceScope.cancel()
    }

    /**
     * Start monitoring steamapps directory
     */
    private fun startMonitoring(containerId: String, steamAppId: Long, gameId: Long) {
        serviceScope.launch {
            try {
                // Build steamapps directory path
                // Path: <internal storage>/winlator/containers/<containerId>/drive_c/Program Files (x86)/Steam/steamapps/
                val steamappsDir = File(
                    filesDir,
                    "winlator/containers/$containerId/drive_c/Program Files (x86)/Steam/steamapps"
                )

                writeLog("Steamapps directory path: ${steamappsDir.absolutePath}")

                if (!steamappsDir.exists()) {
                    writeLog("ERROR: Steamapps directory not found")
                    Log.e(TAG, "Steamapps directory not found: ${steamappsDir.absolutePath}")
                    stopSelf()
                    return@launch
                }

                writeLog("SUCCESS: Steamapps directory exists")
                writeLog("Starting FileObserver for Steam App ID: $steamAppId")
                Log.i(TAG, "Starting FileObserver on: ${steamappsDir.absolutePath}")
                Log.i(TAG, "Monitoring for Steam App ID: $steamAppId")

                // List existing files in steamapps directory
                val existingFiles = steamappsDir.listFiles()?.map { it.name } ?: emptyList()
                writeLog("Existing files in steamapps: ${existingFiles.joinToString(", ")}")

                // Create and start FileObserver
                fileObserver = RecursiveFileObserver(
                    path = steamappsDir.absolutePath,
                    steamAppId = steamAppId,
                    gameId = gameId,
                    onInstallComplete = { manifest ->
                        handleInstallComplete(manifest, gameId)
                    },
                    onProgressUpdate = { progress ->
                        updateNotification("Installing game... $progress%")
                    }
                )
                fileObserver?.startWatching()
                writeLog("FileObserver started successfully")

                // Set timeout to auto-stop service after 2 hours
                launch {
                    delay(TIMEOUT_MILLIS)
                    writeLog("TIMEOUT: 2 hours reached, stopping service")
                    Log.w(TAG, "Timeout reached (2 hours), stopping service")
                    stopSelf()
                }

            } catch (e: Exception) {
                writeLog("FATAL ERROR: Failed to start monitoring - ${e.message}")
                Log.e(TAG, "Failed to start monitoring", e)
                stopSelf()
            }
        }
    }

    /**
     * Handle installation complete event
     */
    private fun handleInstallComplete(manifest: AppManifest, gameId: Long) {
        serviceScope.launch {
            try {
                writeLog("SUCCESS: Installation complete for ${manifest.name} (appId=${manifest.appId})")
                Log.i(TAG, "Installation complete: ${manifest.name} (appId=${manifest.appId})")

                // Update game installation status
                gameRepository.updateInstallationStatus(
                    gameId = gameId,
                    status = InstallationStatus.INSTALLED,
                    progress = 100
                )
                writeLog("Database updated: status=INSTALLED, progress=100")

                // Show completion notification
                updateNotification("${manifest.name} is ready to play!")
                writeLog("Notification updated: Game ready to play")

                // Stop service after 5 seconds (allow user to see notification)
                delay(5000)
                writeLog("Service stopping in 5 seconds")
                stopSelf()

            } catch (e: Exception) {
                writeLog("ERROR: Failed to handle install complete - ${e.message}")
                Log.e(TAG, "Failed to handle install complete", e)
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
     */
    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Initialize real-time log file
     */
    private fun initializeLogFile() {
        try {
            logFile = File(getExternalFilesDir(null), LOG_FILE_NAME)

            // Clear previous log or create new file
            logFile?.writeText("")

            val timestamp = System.currentTimeMillis()
            val initialMessage = """
                ===== Steam Install Monitor Service Started =====
                Timestamp: $timestamp
                Log file: ${logFile?.absolutePath}
                =================================================

            """.trimIndent()

            logFile?.appendText(initialMessage)

            Log.i(TAG, "Real-time log file initialized: ${logFile?.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize log file", e)
        }
    }

    /**
     * Write message to real-time log file
     */
    private fun writeLog(message: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val formattedMessage = "[$timestamp] $message\n"

            logFile?.appendText(formattedMessage)

        } catch (e: Exception) {
            // Silent failure - don't crash service due to logging issues
            Log.e(TAG, "Failed to write to log file: $message", e)
        }
    }

    /**
     * Recursive FileObserver for monitoring steamapps directory
     *
     * Monitors appmanifest_*.acf files for CREATE and MODIFY events
     */
    private inner class RecursiveFileObserver(
        private val path: String,
        private val steamAppId: Long,
        private val gameId: Long,
        private val onInstallComplete: (AppManifest) -> Unit,
        private val onProgressUpdate: (Int) -> Unit
    ) : FileObserver(path, FileObserver.CREATE or FileObserver.MODIFY) {

        override fun onEvent(event: Int, path: String?) {
            if (path == null) return

            try {
                writeLog("FileObserver event: $path (event=$event)")

                // Check if this is an appmanifest file for our game
                if (path.startsWith("appmanifest_") && path.endsWith(".acf")) {
                    val manifestFile = File(this.path, path)

                    // Extract appId from filename (e.g., appmanifest_730.acf -> 730)
                    val fileAppId = path
                        .removePrefix("appmanifest_")
                        .removeSuffix(".acf")
                        .toLongOrNull()

                    writeLog("Detected appmanifest file: appId=$fileAppId (target=$steamAppId)")

                    if (fileAppId == steamAppId) {
                        writeLog("MATCH: This is the target game's manifest file")
                        Log.d(TAG, "Detected event on target manifest: $path (event=$event)")
                        handleManifestChange(manifestFile)
                    } else {
                        writeLog("SKIP: Different game (appId=$fileAppId)")
                    }
                }
            } catch (e: Exception) {
                writeLog("ERROR: File event handler failed - ${e.message}")
                Log.e(TAG, "Error handling file event: $path", e)
            }
        }

        /**
         * Handle manifest file change
         */
        private fun handleManifestChange(manifestFile: File) {
            serviceScope.launch {
                try {
                    // Wait a bit to ensure file write is complete
                    delay(500)

                    writeLog("Checking manifest file: ${manifestFile.absolutePath}")

                    if (!manifestFile.exists() || !manifestFile.canRead()) {
                        writeLog("WARNING: Manifest file not readable")
                        Log.w(TAG, "Manifest file not readable: ${manifestFile.absolutePath}")
                        return@launch
                    }

                    writeLog("Manifest file is readable, parsing...")

                    // Parse manifest
                    val result = AppManifestParser.parse(manifestFile)
                    if (result.isFailure) {
                        writeLog("ERROR: Failed to parse manifest - ${result.exceptionOrNull()?.message}")
                        Log.e(TAG, "Failed to parse manifest: ${result.exceptionOrNull()?.message}")
                        return@launch
                    }

                    val manifest = result.getOrThrow()
                    writeLog("Parsed manifest: appId=${manifest.appId}, name=${manifest.name}, stateFlags=${manifest.stateFlags}")
                    Log.d(TAG, "Parsed manifest: appId=${manifest.appId}, name=${manifest.name}, stateFlags=${manifest.stateFlags}")

                    when (manifest.stateFlags) {
                        2 -> {
                            // Downloading
                            writeLog("STATUS: Game is DOWNLOADING (StateFlags=2)")
                            Log.d(TAG, "Game is downloading: ${manifest.name}")
                            gameRepository.updateInstallationStatus(
                                gameId = gameId,
                                status = InstallationStatus.DOWNLOADING,
                                progress = 50 // Approximate progress
                            )
                            onProgressUpdate(50)
                        }
                        4 -> {
                            // Fully installed
                            writeLog("STATUS: Game FULLY INSTALLED (StateFlags=4)")
                            Log.i(TAG, "Game fully installed: ${manifest.name}")
                            onInstallComplete(manifest)
                        }
                        else -> {
                            val stateDesc = AppManifestParser.getStateDescription(manifest.stateFlags)
                            writeLog("STATUS: Game state=${stateDesc} (StateFlags=${manifest.stateFlags})")
                            Log.d(TAG, "Game state: $stateDesc")
                        }
                    }

                } catch (e: Exception) {
                    writeLog("ERROR: Manifest change handler failed - ${e.message}")
                    Log.e(TAG, "Error handling manifest change", e)
                }
            }
        }
    }
}
