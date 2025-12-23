package com.steamdeck.mobile.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.util.EnvVars
import com.steamdeck.mobile.core.xenvironment.ImageFs
import com.steamdeck.mobile.core.xenvironment.RootfsInstaller
import com.steamdeck.mobile.core.xenvironment.XEnvironment
import com.steamdeck.mobile.core.xenvironment.components.WineProgramLauncherComponent
import com.steamdeck.mobile.core.xenvironment.components.XServerComponent
import com.steamdeck.mobile.core.xserver.XServer
import com.steamdeck.mobile.core.xconnector.UnixSocketConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for Steam XServer Display Screen
 *
 * Manages Steam Big Picture mode launch and lifecycle
 */
@HiltViewModel
class SteamDisplayViewModel @Inject constructor(
    private val steamLauncher: SteamLauncher,
    @ApplicationContext private val context: Context,
    private val securePreferences: com.steamdeck.mobile.domain.repository.ISecurePreferences,
    private val steamConfigManager: com.steamdeck.mobile.core.steam.SteamConfigManager,
    private val steamAuthManager: com.steamdeck.mobile.core.steam.SteamAuthManager
) : ViewModel() {

    companion object {
        private const val TAG = "SteamDisplayVM"
        private const val SOCKET_TIMEOUT_MS = 5000L
        private const val SOCKET_POLL_INTERVAL_MS = 100L
    }

    private val _uiState = MutableStateFlow<SteamDisplayUiState>(SteamDisplayUiState.Loading)
    val uiState: StateFlow<SteamDisplayUiState> = _uiState.asStateFlow()

    private var xEnvironment: XEnvironment? = null

    /**
     * Launch Steam Big Picture mode with XServer initialization
     *
     * CRITICAL FIX: Use WineProgramLauncherComponent instead of SteamLauncher.launchSteamBigPicture()
     * - SteamLauncher creates SEPARATE process environment via winlatorEmulator.launchExecutable()
     * - WineProgramLauncherComponent runs in SAME XEnvironment as XServerComponent
     * - Both components started together via environment.startEnvironmentComponents()
     *
     * INTEGRATED MODE (2025-12-22):
     * - Configure GLRenderer window filtering to hide Windows desktop
     * - Only show Steam Big Picture window (seamless Android-like UX)
     * - No virtual desktop mode (/desktop flag removed)
     *
     * @param containerId Wine container ID
     * @param xServer XServer instance
     * @param xServerView XServerView for accessing GLRenderer (window filtering)
     */
    fun launchSteam(containerId: String, xServer: XServer, xServerView: com.steamdeck.mobile.presentation.widget.XServerView) {
        android.util.Log.d(TAG, "launchSteam() called with containerId: $containerId")
        viewModelScope.launch {
            try {
                AppLogger.i(TAG, "Starting Steam launch sequence: containerId=$containerId")

                // Step 1: Initialize XServer environment WITH Steam launcher
                _uiState.value = SteamDisplayUiState.InitializingXServer
                android.util.Log.d(TAG, "Step 1: Initializing XEnvironment with WineProgramLauncherComponent")

                val initialized = initializeXEnvironment(xServer, containerId, xServerView)
                if (!initialized) {
                    AppLogger.e(TAG, "XEnvironment initialization failed")
                    _uiState.value = SteamDisplayUiState.Error("Failed to initialize display server")
                    return@launch
                }

                AppLogger.i(TAG, "XServer initialized successfully")

                // Step 2: Launch Steam via WineProgramLauncherComponent (already started with XEnvironment)
                _uiState.value = SteamDisplayUiState.Launching
                android.util.Log.d(TAG, "Step 2: Steam launching via WineProgramLauncherComponent")
                AppLogger.i(TAG, "Steam launching: containerId=$containerId")

                // WineProgramLauncherComponent automatically launches when environment.startEnvironmentComponents() is called
                // No need for separate steamLauncher.launchSteamBigPicture() call
                _uiState.value = SteamDisplayUiState.Running
                AppLogger.i(TAG, "Steam launched successfully")
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Failed to launch Steam"
                _uiState.value = SteamDisplayUiState.Error(errorMessage)
                AppLogger.e(TAG, "Exception launching Steam", e)
                android.util.Log.e(TAG, "Exception: $errorMessage", e)
            }
        }
    }

    /**
     * Initialize XEnvironment and start XServer Unix socket
     * MUST be called before launching Wine
     *
     * INTEGRATED MODE (2025-12-22):
     * - Configure GLRenderer window filtering after XEnvironment initialization
     * - Hide Windows desktop (explorer.exe, progman, shell_traywnd)
     * - Force Steam window to fullscreen
     *
     * @param xServer The XServer instance for rendering
     * @param containerId The Wine container ID to launch Steam from
     * @param xServerView XServerView for accessing GLRenderer
     */
    private suspend fun initializeXEnvironment(xServer: XServer, containerId: String, xServerView: com.steamdeck.mobile.presentation.widget.XServerView): Boolean = withContext(Dispatchers.IO) {
        try {
            // CRITICAL: Install rootfs from assets if needed
            // This provides complete Ubuntu-based Linux environment for Wine/Box64
            android.util.Log.i(TAG, "Checking rootfs installation...")
            val rootfsInstalled = RootfsInstaller.installIfNeeded(context)
            if (!rootfsInstalled) {
                AppLogger.e(TAG, "Failed to install rootfs from assets")
                return@withContext false
            }
            android.util.Log.i(TAG, "Rootfs is ready")

            // CRITICAL: Use rootfsDir (not context.filesDir) for socket creation
            // This ensures Wine in PRoot chroot can access the socket
            // Wine sees /tmp/.X11-unix/X0 → PRoot maps to rootfsDir/tmp/.X11-unix/X0
            val rootfsDir = File(context.filesDir, "winlator/rootfs")
            if (!rootfsDir.exists()) {
                AppLogger.e(TAG, "Rootfs directory not found after installation: ${rootfsDir.absolutePath}")
                return@withContext false
            }

            // Create tmp directory inside rootfs (not in Android files directory)
            val tmpDir = File(rootfsDir, "tmp")
            if (!tmpDir.exists()) {
                tmpDir.mkdirs()
                AppLogger.d(TAG, "Created tmp directory in rootfs: ${tmpDir.absolutePath}")
            }

            val x11UnixDir = File(tmpDir, ".X11-unix")
            if (!x11UnixDir.exists()) {
                x11UnixDir.mkdirs()
                AppLogger.d(TAG, "Created .X11-unix directory: ${x11UnixDir.absolutePath}")
            }

            // CRITICAL FIX: Create XAUTHORITY file for Wine X11 authentication
            // Wine requires this file to connect to XServer (even for local connections)
            val xAuthFile = File(tmpDir, ".Xauthority")
            android.util.Log.e(TAG, "XAUTHORITY check: exists=${xAuthFile.exists()}, path=${xAuthFile.absolutePath}")
            if (!xAuthFile.exists()) {
                val created = xAuthFile.createNewFile()
                xAuthFile.setReadable(true, true)
                xAuthFile.setWritable(true, true)
                android.util.Log.e(TAG, "Created XAUTHORITY file: created=$created, path=${xAuthFile.absolutePath}")
                AppLogger.d(TAG, "Created XAUTHORITY file: ${xAuthFile.absolutePath}")
            } else {
                android.util.Log.e(TAG, "XAUTHORITY file already exists: ${xAuthFile.absolutePath}")
            }

            // CRITICAL FIX: Create /etc/hosts file for localhost hostname resolution
            // Wine's gethostname() returns "localhost" which needs to resolve to 127.0.0.1
            // Without this file, Wine gets "Failed to resolve your host name IP" error
            val etcDir = File(rootfsDir, "etc")
            if (!etcDir.exists()) {
                etcDir.mkdirs()
                android.util.Log.i(TAG, "Created /etc directory in rootfs: ${etcDir.absolutePath}")
                AppLogger.d(TAG, "Created /etc directory in rootfs")
            }

            val hostsFile = File(etcDir, "hosts")
            val hostsContent = """
                127.0.0.1       localhost
                ::1             ip6-localhost

                # Steam CDN servers (Cloudflare edge) - bypass DNS lookup
                # These IPs are for cdn.steamstatic.com and related domains
                # Source: Winlator Android-only success pattern (Step 3)
                104.18.42.129   cdn.steamstatic.com
                104.18.43.129   cdn.steamstatic.com
                104.18.42.129   media.steampowered.com
                104.18.42.129   steamcdn-a.akamaihd.net
            """.trimIndent()

            // Always create/overwrite hosts file to ensure correct content
            hostsFile.writeText(hostsContent)
            hostsFile.setReadable(true, false)
            android.util.Log.i(TAG, "Created /etc/hosts file: ${hostsFile.absolutePath}")
            AppLogger.i(TAG, "Created /etc/hosts with localhost + Steam CDN direct IPs")

            // CRITICAL DNS FIX (2025-12-23): Create /etc/resolv.conf in rootfs
            // Problem: /system/etc/resolv.conf doesn't exist on Android 10+
            // Solution: Create resolv.conf in rootfs, bind it via PRoot
            val resolvConfFile = File(etcDir, "resolv.conf")
            val resolvConfContent = """
                # DNS configuration for Wine/Steam network access
                # Using Google Public DNS (reliable, low-latency)
                nameserver 8.8.8.8
                nameserver 8.8.4.4
            """.trimIndent()

            resolvConfFile.writeText(resolvConfContent)
            resolvConfFile.setReadable(true, false)
            android.util.Log.i(TAG, "Created /etc/resolv.conf file: ${resolvConfFile.absolutePath}")
            AppLogger.i(TAG, "Created /etc/resolv.conf with Google DNS (8.8.8.8, 8.8.4.4)")

            // Verify X11 libraries exist in rootfs (helps debug connection failures)
            verifyX11Libraries(rootfsDir)

            // Create ImageFs (required by XEnvironment)
            val imageFs = ImageFs.find(context)

            // Create XEnvironment orchestrator
            xEnvironment = XEnvironment(context, imageFs)
            AppLogger.d(TAG, "Created XEnvironment")

            // Create Unix socket config for XServer using rootfs path
            // Socket path: /data/.../winlator/rootfs/tmp/.X11-unix/X0 (Android filesystem)
            // Wine will access as: /tmp/.X11-unix/X0 (via PRoot bind mount)
            val socketConfig = UnixSocketConfig.createSocket(
                rootfsDir.absolutePath,  // ✅ Use rootfs instead of filesDir
                UnixSocketConfig.XSERVER_PATH  // "/tmp/.X11-unix/X0"
            )
            AppLogger.d(TAG, "Created socket config: ${socketConfig.path}")

            // Create XServerComponent (manages Unix socket lifecycle)
            val xServerComponent = XServerComponent(xServer, socketConfig)
            xEnvironment?.addComponent(xServerComponent)
            AppLogger.d(TAG, "Added XServerComponent to environment")

            // CRITICAL FIX: Create WineProgramLauncherComponent to launch Steam in SAME environment
            // This ensures Wine process shares the same XEnvironment as XServerComponent
            // Winlator architecture: XServerComponent + WineProgramLauncherComponent in ONE XEnvironment

            // Steam is installed in container directory (NOT rootfs)
            // Path: /data/.../winlator/containers/{containerId}/drive_c/Program Files (x86)/Steam/Steam.exe
            val containersDir = File(context.filesDir, "winlator/containers")
            val containerDir = File(containersDir, containerId)
            val steamExe = File(containerDir, "drive_c/Program Files (x86)/Steam/Steam.exe")

            if (!steamExe.exists()) {
                AppLogger.e(TAG, "Steam not found: ${steamExe.absolutePath}")
                xEnvironment?.stopEnvironmentComponents()
                return@withContext false
            }

            // CRITICAL: Create Steam auto-login configuration
            // 1. loginusers.vdf: User account info for auto-login
            // 2. config.vdf: CDN servers + AutoLoginUser setting
            AppLogger.i(TAG, "Configuring Steam auto-login...")
            val steamId = try {
                securePreferences.getSteamId().first()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to get Steam ID: ${e.message}")
                null
            }

            // Create loginusers.vdf if Steam ID is available
            if (steamId != null) {
                AppLogger.i(TAG, "Creating loginusers.vdf for Steam ID: $steamId")
                val authResult = steamAuthManager.createLoginUsersVdf(containerDir)
                if (authResult.isFailure) {
                    AppLogger.w(TAG, "Failed to create loginusers.vdf (non-fatal): ${authResult.exceptionOrNull()?.message}")
                    AppLogger.w(TAG, "Auto-login will not work, manual login required")
                } else {
                    AppLogger.i(TAG, "loginusers.vdf created successfully - Steam will auto-login")
                }
            } else {
                AppLogger.i(TAG, "No Steam ID found - skipping auto-login configuration")
                AppLogger.i(TAG, "User must login via QR code in Settings to enable auto-login")
            }

            // Create config.vdf with CDN servers and auto-login settings
            AppLogger.i(TAG, "Creating Steam config.vdf with pre-configured CDN servers...")
            val configResult = steamConfigManager.createConfigVdf(containerDir, steamId)
            if (configResult.isFailure) {
                AppLogger.w(TAG, "Failed to create config.vdf (non-fatal): ${configResult.exceptionOrNull()?.message}")
                AppLogger.w(TAG, "Steam may fail to download manifest during bootstrap")
            } else {
                if (steamId != null) {
                    AppLogger.i(TAG, "Steam config.vdf created with auto-login enabled for SteamID: $steamId")
                } else {
                    AppLogger.i(TAG, "Steam config.vdf created (no auto-login, manual login required)")
                }
            }

            // SIMPLE IMPLEMENTATION (2025-12-23): Minimal window filtering
            val renderer = xServerView.renderer
            renderer.setUnviewableWMClasses("progman", "shell_traywnd")  // Hide Windows desktop only
            AppLogger.i(TAG, "Simple mode: Hide desktop only, no forced fullscreen")

            // DESKTOP SHELL + STEAM AUTO-LAUNCH (2025-12-23)
            // Show Windows desktop persistently, auto-launch Steam in background
            // Desktop stays open even if Steam exits
            val steamPath = "C:/Program Files (x86)/Steam/Steam.exe"
            val screenSize = "1280x720"

            // OPTIMIZED STEAM LAUNCH FLAGS (2025-12-23 Research)
            // -tcp: Force TCP protocol (REMOVED - not officially documented, unclear benefit)
            // -lognetapi: Enable network API logging to logs/netapi_log.txt (for debugging)
            // --no-cef-sandbox: Disable CEF sandboxing (Wine compatibility)
            // -vgui: Use legacy VGUI interface (Wine compatibility, lightweight)
            //
            // REMOVED: -no-browser (prevented login screen from appearing)
            //
            // Based on:
            // - https://developer.valvesoftware.com/wiki/Command_line_options_(Steam)
            // - https://github.com/Bluscream/Steam-Client-Docs/blob/master/Command%20Line%20Arguments.MD
            val guestCommand = "wine explorer /desktop=shell,$screenSize cmd /c start \"\" \"$steamPath\" -lognetapi -vgui --no-cef-sandbox"
            AppLogger.i(TAG, "Launching desktop with auto-start Steam: $guestCommand")
            AppLogger.i(TAG, "Steam flags: -lognetapi (network debug), -vgui (legacy UI), --no-cef-sandbox (Wine compat)")

            // SIMPLE LAUNCHER CONFIGURATION (2025-12-23)
            val guestProgramLauncher = WineProgramLauncherComponent()
            guestProgramLauncher.setGuestExecutable(guestCommand)
            guestProgramLauncher.setWoW64Mode(true)  // Steam is 32-bit
            guestProgramLauncher.setBox86Preset(com.steamdeck.mobile.box86_64.Box86_64Preset.STABILITY)
            guestProgramLauncher.setBox64Preset(com.steamdeck.mobile.box86_64.Box86_64Preset.STABILITY)
            AppLogger.i(TAG, "WoW64 + STABILITY preset enabled")

            // SIMPLE ENVIRONMENT SETUP (2025-12-23)
            guestProgramLauncher.setBindingPaths(arrayOf(containerDir.absolutePath))

            val envVars = EnvVars()
            envVars.put("WINEPREFIX", "/root")
            envVars.put("DISPLAY", ":0")

            // RESTORE ORIGINAL WORKING CONFIGURATION (from first successful implementation)
            envVars.put("WINEDEBUG", "-all,+err")  // Minimal logging for performance
            envVars.put("WINEESYNC", "1")  // Enable ESYNC for better performance
            envVars.put("MESA_GL_VERSION_OVERRIDE", "4.6")
            envVars.put("MESA_GLSL_VERSION_OVERRIDE", "460")
            envVars.put("MESA_DEBUG", "silent")
            envVars.put("MESA_NO_ERROR", "1")

            guestProgramLauncher.setEnvVars(envVars)
            AppLogger.i(TAG, "Restored original working environment configuration")

            // Add termination callback (navigate back on exit)
            guestProgramLauncher.setTerminationCallback { status ->
                AppLogger.i(TAG, "Steam process terminated with status: $status")
                viewModelScope.launch {
                    _uiState.value = SteamDisplayUiState.Error("Steam closed (exit code: $status)")
                }
            }

            xEnvironment?.addComponent(guestProgramLauncher)
            AppLogger.d(TAG, "Added WineProgramLauncherComponent to environment")

            // CRITICAL FIX (2025-12-22): Start XServerComponent FIRST, THEN Wine
            // Problem: startEnvironmentComponents() launches ALL components simultaneously
            // Wine tries to connect to :0 before socket exists → "no driver could be loaded"
            // Solution: Start XServerComponent, wait for socket, THEN start Wine launcher
            AppLogger.i(TAG, "Starting XServerComponent (creating Unix socket)...")
            xServerComponent.start()
            AppLogger.d(TAG, "XServerComponent started, waiting for socket...")

            // Wait for Unix socket to be ready BEFORE launching Wine
            AppLogger.d(TAG, "Waiting for socket: ${socketConfig.path}")
            val socketReady = waitForSocket(socketConfig.path, SOCKET_TIMEOUT_MS)

            if (!socketReady) {
                // Diagnostics
                val socketFile = File(socketConfig.path)
                val parentDir = socketFile.parentFile
                AppLogger.e(TAG, "Socket timeout diagnostics:")
                AppLogger.e(TAG, "  Socket path: ${socketConfig.path}")
                AppLogger.e(TAG, "  Socket exists: ${socketFile.exists()}")
                AppLogger.e(TAG, "  Parent dir: ${parentDir?.absolutePath}")
                AppLogger.e(TAG, "  Parent exists: ${parentDir?.exists()}")
                AppLogger.e(TAG, "  Parent writable: ${parentDir?.canWrite()}")

                xEnvironment?.stopEnvironmentComponents()
                return@withContext false
            }

            AppLogger.i(TAG, "XServer socket ready: ${socketConfig.path}")

            // NOW start Wine launcher (socket is ready, Wine can connect immediately)
            AppLogger.i(TAG, "Socket verified, starting Wine launcher...")
            guestProgramLauncher.start()
            AppLogger.i(TAG, "Wine launcher started successfully")

            true

        } catch (e: Exception) {
            AppLogger.e(TAG, "XEnvironment initialization failed", e)
            android.util.Log.e(TAG, "XEnvironment error", e)
            xEnvironment?.stopEnvironmentComponents()
            false
        }
    }

    /**
     * Wait for Unix socket file to be created
     */
    private suspend fun waitForSocket(socketPath: String, timeoutMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            val socketFile = File(socketPath)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (socketFile.exists()) {
                    AppLogger.i(TAG, "Socket detected: $socketPath")
                    return@withContext true
                }
                delay(SOCKET_POLL_INTERVAL_MS)
            }

            AppLogger.e(TAG, "Socket timeout after ${timeoutMs}ms: $socketPath")
            false
        }

    /**
     * Verify X11 libraries exist in rootfs
     * Helps debug Wine X11 connection failures
     */
    private fun verifyX11Libraries(rootfsDir: File) {
        val requiredLibs = listOf(
            "usr/lib/libX11.so.6",
            "usr/lib/libxcb.so.1",
            "usr/lib/libXext.so.6",
            "usr/lib/libXfixes.so.3",
            "usr/lib/x86_64-linux-gnu/libX11.so.6",
            "lib/libX11.so.6"
        )

        var foundCount = 0
        for (libPath in requiredLibs) {
            val libFile = File(rootfsDir, libPath)
            if (libFile.exists()) {
                AppLogger.d(TAG, "✓ X11 library found: $libPath")
                foundCount++
            }
        }

        if (foundCount == 0) {
            AppLogger.w(TAG, "⚠ No X11 libraries found in rootfs - Wine may fail to connect")
        } else {
            AppLogger.i(TAG, "Found $foundCount/${requiredLibs.size} X11 libraries")
        }
    }

    /**
     * Reset state (for cleanup/retry)
     */
    fun reset() {
        _uiState.value = SteamDisplayUiState.Loading
    }

    /**
     * Cleanup XEnvironment on ViewModel destruction
     */
    override fun onCleared() {
        super.onCleared()

        try {
            xEnvironment?.stopEnvironmentComponents()
            xEnvironment = null
            AppLogger.i(TAG, "XEnvironment cleaned up")
        } catch (e: Exception) {
            AppLogger.e(TAG, "XEnvironment cleanup failed", e)
        }
    }
}

/**
 * UI State for Steam Display Screen
 */
@Immutable
sealed class SteamDisplayUiState {
    /** Initial loading state */
    data object Loading : SteamDisplayUiState()

    /** XServer is being initialized */
    data object InitializingXServer : SteamDisplayUiState()

    /** Steam is being launched */
    data object Launching : SteamDisplayUiState()

    /** Steam is running successfully */
    data object Running : SteamDisplayUiState()

    /** Error occurred during launch */
    data class Error(val message: String) : SteamDisplayUiState()
}
