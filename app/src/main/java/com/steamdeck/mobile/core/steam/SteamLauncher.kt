package com.steamdeck.mobile.core.steam

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.core.xserver.XServerManager
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Client Launcher
 *
 * Launches games through Steam Client
 */
@Singleton
class SteamLauncher @Inject constructor(
 @ApplicationContext private val context: Context,
 private val winlatorEmulator: WinlatorEmulator,
 private val xServerManager: XServerManager
) {
 companion object {
  private const val TAG = "SteamLauncher"
 }

 /**
  * Launch game via Steam
  */
 suspend fun launchGameViaSteam(
  containerId: String,
  appId: Long
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Launching game via Steam: appId=$appId, containerId=$containerId")

   // 1. Ensure XServer is running (Steam.exe requires X11/DISPLAY=:0)
   if (!xServerManager.isRunning()) {
    AppLogger.i(TAG, "Starting XServer for Steam game launch")
    val xServerResult = xServerManager.startXServer()
    if (xServerResult.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to start XServer: ${xServerResult.exceptionOrNull()?.message}")
     )
    }
   } else {
    AppLogger.d(TAG, "XServer already running")
   }

   // 2. Get Winlator container
   val container = getEmulatorContainer(containerId)
   if (container.isFailure) {
    return@withContext Result.failure(
     Exception("Failed to get container: ${container.exceptionOrNull()?.message}")
    )
   }

   val emulatorContainer = container.getOrElse {
    return@withContext Result.failure(
     Exception("Container not available: ${it.message}")
    )
   }

   // 3. Build Steam.exe path (case-sensitive on Android)
   val steamExe = File(emulatorContainer.rootPath, "drive_c/Program Files (x86)/Steam/Steam.exe")

   if (!steamExe.exists()) {
    return@withContext Result.failure(
     Exception("Steam not found at ${steamExe.absolutePath}. Please install Steam first.")
    )
   }

   // 4. Build Steam launch arguments
   // -applaunch <appId> launches game directly
   val arguments = listOf(
    "-applaunch",
    appId.toString()
   )

   AppLogger.i(TAG, "Launching Steam with arguments: $arguments")

   // 5. Launch Steam via Wine
   val processResult = winlatorEmulator.launchExecutable(
    container = emulatorContainer,
    executable = steamExe,
    arguments = arguments
   )

   if (processResult.isFailure) {
    return@withContext Result.failure(
     Exception("Failed to launch Steam: ${processResult.exceptionOrNull()?.message}")
    )
   }

   val process = processResult.getOrElse {
    return@withContext Result.failure(
     Exception("Failed to get process handle: ${it.message}")
    )
   }
   AppLogger.i(TAG, "Steam launched successfully: PID ${process.pid}")

   Result.success(Unit)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to launch game via Steam", e)
   Result.failure(e)
  }
 }

 /**
  * Launch Steam Big Picture mode
  *
  * Opens Steam in Big Picture mode (fullscreen console-like UI)
  * Users can browse their library and install games
  * FileObserver will detect installations automatically
  *
  * @param backgroundMode If true, launches Steam in background without UI (for download monitoring)
  */
 suspend fun launchSteamBigPicture(containerId: String, backgroundMode: Boolean = false): Result<Unit> =
  withContext(Dispatchers.IO) {
   try {
    AppLogger.i(TAG, "Launching Steam ${if (backgroundMode) "in background" else "Big Picture"} for container=$containerId")

    // 1. Ensure XServer is running (Steam.exe requires X11/DISPLAY=:0)
    if (!xServerManager.isRunning()) {
     AppLogger.i(TAG, "Starting XServer for Steam.exe (background=$backgroundMode)")
     val xServerResult = xServerManager.startXServer()
     if (xServerResult.isFailure) {
      return@withContext Result.failure(
       Exception("Failed to start XServer: ${xServerResult.exceptionOrNull()?.message}")
      )
     }
    } else {
     AppLogger.d(TAG, "XServer already running")
    }

    // 2. Get Winlator container
    val container = getEmulatorContainer(containerId)
    if (container.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to get container: ${container.exceptionOrNull()?.message}")
     )
    }

    val emulatorContainer = container.getOrElse {
     return@withContext Result.failure(
      Exception("Container not available: ${it.message}")
     )
    }

    // 3. Build Steam.exe path (case-sensitive on Android)
    val steamExe = File(emulatorContainer.rootPath, "drive_c/Program Files (x86)/Steam/Steam.exe")

    if (!steamExe.exists()) {
     return@withContext Result.failure(
      Exception("Steam not found at ${steamExe.absolutePath}. Please install Steam first.")
     )
    }

    // 4. Build Steam launch arguments
    val arguments = if (backgroundMode) {
     // Background mode: no UI, silent operation
     // -silent: Runs Steam without showing the UI
     // -no-browser: Disables the built-in web browser (reduces UI dependencies)
     // NOTE: XServer is still required even in background mode (Wine X11 driver dependency)
     listOf("-silent", "-no-browser")
    } else {
     // Big Picture mode: fullscreen console-like UI
     listOf("-bigpicture")
    }

    AppLogger.i(TAG, "Launching Steam with arguments: $arguments")

    // 5. Launch Steam with specified arguments
    val processResult = winlatorEmulator.launchExecutable(
     container = emulatorContainer,
     executable = steamExe,
     arguments = arguments
    )

    if (processResult.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to launch Steam: ${processResult.exceptionOrNull()?.message}")
     )
    }

    val process = processResult.getOrElse {
     return@withContext Result.failure(
      Exception("Failed to get process handle: ${it.message}")
     )
    }
    AppLogger.i(TAG, "Steam launched successfully: PID ${process.pid} (background=$backgroundMode)")

    Result.success(Unit)

   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to launch Steam", e)
    Result.failure(e)
   }
  }

 /**
  * Launch Steam Client using explorer /desktop=shell method
  *
  * PROVEN WINLATOR APPROACH:
  * - wine explorer.exe /desktop=shell,1280x720 Steam.exe
  * - explorer.exe acts as parent process → prevents premature exit
  * - Stable Steam execution (same method as Winlator 10.1)
  *
  * Resolution: 1280x720 (720p, 16:9 aspect ratio)
  * XServer will scale this to device screen (2320x1036)
  */
 suspend fun launchSteamClient(containerId: String): Result<Unit> =
  withContext(Dispatchers.IO) {
   try {
    AppLogger.i(TAG, "Launching Steam Client for container: $containerId")

    // 1. Ensure XServer is running (Steam.exe requires X11/DISPLAY=:0)
    if (!xServerManager.isRunning()) {
     AppLogger.i(TAG, "Starting XServer for Steam Client")
     val xServerResult = xServerManager.startXServer()
     if (xServerResult.isFailure) {
      return@withContext Result.failure(
       Exception("Failed to start XServer: ${xServerResult.exceptionOrNull()?.message}")
      )
     }
    } else {
     AppLogger.d(TAG, "XServer already running")
    }

    // 2. Get Winlator container
    val container = getEmulatorContainer(containerId)
    if (container.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to get container: ${container.exceptionOrNull()?.message}")
     )
    }

    val emulatorContainer = container.getOrElse {
     return@withContext Result.failure(
      Exception("Container not available: ${it.message}")
     )
    }

    // 3. Verify Steam.exe exists
    val steamExe = File(emulatorContainer.rootPath, "drive_c/Program Files (x86)/Steam/Steam.exe")
    if (!steamExe.exists()) {
     return@withContext Result.failure(
      Exception("Steam not found at ${steamExe.absolutePath}. Please install Steam first.")
     )
    }

    // 4. Get Wine builtin explorer.exe path
    // Explorer.exe is a Wine builtin, located in Wine's library directory
    val rootfsDir = File(winlatorEmulator.getRootfsPath())
    val explorerExe = File(rootfsDir, "opt/wine/lib/wine/x86_64-windows/explorer.exe")

    if (!explorerExe.exists()) {
     return@withContext Result.failure(
      Exception("Wine explorer.exe not found at ${explorerExe.absolutePath}")
     )
    }

    // 5. Build command: wine explorer.exe /desktop=shell,1280x720 "C:\Program Files (x86)\Steam\Steam.exe" -no-cef-sandbox -noreactlogin -tcp -console
    // This keeps explorer.exe as parent process → prevents premature Steam exit
    // CRITICAL: Must use Windows path, not Android filesystem path
    // CEF workarounds:
    // - -no-cef-sandbox: Disable CEF sandbox (Wine kernel hooking incompatibility)
    // - -noreactlogin: Disable React UI login screen (WebView rendering issues)
    // - -tcp: Use TCP instead of UDP for network (Wine compatibility)
    // - -console: Enable debug console for diagnostics
    val arguments = buildList {
     add("/desktop=shell,1280x720")
     add("C:\\Program Files (x86)\\Steam\\Steam.exe")
     add("-no-cef-sandbox")
     add("-noreactlogin")
     add("-tcp")
     add("-console")
    }

    AppLogger.i(TAG, "Launching Steam via explorer /desktop=shell method")

    // 6. Launch via Winlator
    val processResult = winlatorEmulator.launchExecutable(
     container = emulatorContainer,
     executable = explorerExe,
     arguments = arguments
    )

    if (processResult.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to launch Steam: ${processResult.exceptionOrNull()?.message}")
     )
    }

    val process = processResult.getOrElse {
     return@withContext Result.failure(
      Exception("Failed to get process handle: ${it.message}")
     )
    }
    AppLogger.i(TAG, "Steam launched successfully via explorer /desktop=shell: PID ${process.pid}")

    Result.success(Unit)

   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to launch Steam Client", e)
    Result.failure(e)
   }
  }

 /**
  * Get Winlator EmulatorContainer
  */
 private suspend fun getEmulatorContainer(containerId: String): Result<com.steamdeck.mobile.domain.emulator.EmulatorContainer> =
  withContext(Dispatchers.IO) {
   try {
    // Get container list from Winlator
    val containersResult = winlatorEmulator.listContainers()
    if (containersResult.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to list containers: ${containersResult.exceptionOrNull()?.message}")
     )
    }

    val containers = containersResult.getOrNull() ?: emptyList()

    // Match container ID (String type ID direct comparison)
    val container = containers.firstOrNull { it.id == containerId }
     ?: return@withContext Result.failure(
      Exception("Container not found in Winlator: $containerId")
     )

    Result.success(container)

   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to get emulator container", e)
    Result.failure(e)
   }
  }

 /**
  * Open game installation page using Steam Deep Link
  *
  * Steam ToS Compliance: Uses the steam:// protocol of the Android Steam app
  * to prompt game download/installation in the official Steam app
  *
  * @param appId Steam App ID
  * @return Result.success if successful, Result.failure if failed
  */
 fun openSteamInstallPage(appId: Long): Result<Unit> {
  return try {
   // Open Steam app install page with steam://install/<appId>
   val intent = Intent(Intent.ACTION_VIEW).apply {
    data = Uri.parse("steam://install/$appId")
    // Add FLAG_ACTIVITY_NEW_TASK to enable launch from Application Context
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   }

   context.startActivity(intent)
   AppLogger.i(TAG, "Opened Steam install page for appId=$appId")
   Result.success(Unit)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to open Steam install page", e)
   Result.failure(
    Exception("Steam app not found. Please install Steam from Google Play Store.")
   )
  }
 }

 /**
  * Check if Steam process is currently running
  *
  * Checks for Steam.exe process in Wine/Box64 environment
  *
  * @return true if Steam is running, false otherwise
  */
 suspend fun isSteamRunning(): Boolean = withContext(Dispatchers.IO) {
  try {
   // Check if any Wine process named "Steam.exe" is running
   val process = ProcessBuilder("ps", "-A")
    .redirectErrorStream(true)
    .start()

   val output = process.inputStream.bufferedReader().use { it.readText() }
   val isSteamRunning = output.contains("Steam.exe", ignoreCase = true) ||
                        output.contains("steam.exe", ignoreCase = true)

   AppLogger.d(TAG, "Steam running status: $isSteamRunning")
   isSteamRunning

  } catch (e: Exception) {
   AppLogger.w(TAG, "Failed to check Steam process status", e)
   false
  }
 }

 /**
  * Open Winlator app
  *
  * Opens the Winlator app so users can manually launch Steam from there.
  * This is useful when direct Steam.exe launch via Wine doesn't work.
  *
  * @return Result.success if Winlator app opened, Result.failure otherwise
  */
 fun openWinlatorApp(): Result<Unit> {
  return winlatorEmulator.openWinlatorApp()
 }
}
