package com.steamdeck.mobile.core.xserver

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.xconnector.UnixSocketConfig
import com.steamdeck.mobile.core.xenvironment.ImageFs
import com.steamdeck.mobile.core.xenvironment.XEnvironment
import com.steamdeck.mobile.core.xenvironment.components.XServerComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages XServer lifecycle for Wine/Steam.exe X11 display support
 *
 * This manager handles:
 * 1. XServer startup for Steam.exe and game execution
 * 2. XEnvironment component lifecycle
 * 3. Thread-safe singleton XServer instance
 * 4. Unix socket management (/tmp/.X11-unix/X0)
 *
 * Architecture:
 * - Singleton per app (1 XServer, DISPLAY=:0)
 * - Headless operation (no visual output to Android UI)
 * - Steam.exe and Wine processes connect via X11 protocol
 * - Socket path: rootfs/tmp/.X11-unix/X0
 *
 * Usage:
 * ```kotlin
 * // Start XServer before launching Steam.exe
 * xServerManager.startXServer()
 *
 * // Steam.exe connects to DISPLAY=:0
 * launchSteam()
 *
 * // Stop XServer when no longer needed
 * xServerManager.stopXServer()
 * ```
 */
@Singleton
class XServerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "XServerManager"

        // X11 Display configuration
        private const val DISPLAY_NUMBER = 0  // DISPLAY=:0
        private const val SCREEN_WIDTH = 2400  // Landscape 1080p
        private const val SCREEN_HEIGHT = 1080
        private const val DPI = 160

        // Unix socket path (inside rootfs)
        private const val SOCKET_PATH = "/tmp/.X11-unix/X0"
    }

    private var xEnvironment: XEnvironment? = null
    private val mutex = Mutex()

    /**
     * Starts XServer for X11 display support
     *
     * This method:
     * 1. Creates XServer instance (headless)
     * 2. Sets up Unix socket for X11 connections
     * 3. Starts XEnvironment components
     * 4. Returns immediately (XServer runs in background)
     *
     * Thread-safe: Multiple calls return success if already running
     *
     * @return Result.success if XServer started or already running
     * @return Result.failure if startup failed
     */
    suspend fun startXServer(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Return success if already running
            if (xEnvironment != null) {
                AppLogger.d(TAG, "XServer already running")
                return@withContext Result.success(Unit)
            }

            try {
                AppLogger.i(TAG, "Starting XServer (DISPLAY=:$DISPLAY_NUMBER, ${SCREEN_WIDTH}x${SCREEN_HEIGHT}@${DPI}dpi)")

                // 1. Find rootfs directory (matches WinlatorEmulator configuration)
                val imageFs = ImageFs.find(context)
                if (!imageFs.isValid) {
                    val error = Exception("Winlator rootfs not initialized: ${imageFs.rootDir.absolutePath}")
                    AppLogger.e(TAG, "Failed to start XServer", error)
                    return@withContext Result.failure(error)
                }

                // 2. Create XServer instance with screen configuration
                val screenInfo = ScreenInfo(SCREEN_WIDTH, SCREEN_HEIGHT)
                val xServer = XServer(screenInfo)

                // 3. Create XEnvironment and add XServer component
                val env = XEnvironment(context, imageFs)

                // CRITICAL: Socket path must be inside rootfs/tmp for PRoot bind mount
                // Wine expects socket at /tmp/.X11-unix/X0 (inside chroot)
                // Actual file: rootfs/tmp/.X11-unix/X0 (Android filesystem)
                val socketConfig = UnixSocketConfig.createSocket(
                    imageFs.rootDir.absolutePath,
                    SOCKET_PATH
                )

                val xServerComponent = XServerComponent(xServer, socketConfig)
                env.addComponent(xServerComponent)

                // 4. Start XEnvironment components (creates Unix socket, starts epoll loop)
                env.startEnvironmentComponents()

                xEnvironment = env
                AppLogger.i(TAG, "XServer started successfully (socket=${socketConfig.path})")
                Result.success(Unit)

            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start XServer", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Stops XServer and cleans up resources
     *
     * This method:
     * 1. Stops XEnvironment components
     * 2. Closes Unix socket
     * 3. Releases XServer resources
     *
     * Thread-safe: Multiple calls are safe (no-op if not running)
     *
     * @return Result.success if stopped or already stopped
     * @return Result.failure if cleanup failed
     */
    suspend fun stopXServer(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val env = xEnvironment ?: run {
                AppLogger.d(TAG, "XServer not running, nothing to stop")
                return@withContext Result.success(Unit)
            }

            try {
                AppLogger.i(TAG, "Stopping XServer")
                env.stopEnvironmentComponents()
                xEnvironment = null
                AppLogger.i(TAG, "XServer stopped successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to stop XServer", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Checks if XServer is currently running
     *
     * @return true if XServer is running, false otherwise
     */
    fun isRunning(): Boolean = xEnvironment != null

    /**
     * Gets XServer instance (for advanced use cases)
     *
     * @return XServer instance if running, null otherwise
     */
    fun getXServer(): XServer? {
        return xEnvironment?.getComponent(XServerComponent::class.java)?.xServer
    }
}
