package com.steamdeck.mobile.core.steam

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
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
 private val winlatorEmulator: WinlatorEmulator
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

   // 1. Get Winlator container
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

   // 2. Build Steam.exe path (case-sensitive on Android)
   val steamExe = File(emulatorContainer.rootPath, "drive_c/Program Files (x86)/Steam/Steam.exe")

   if (!steamExe.exists()) {
    return@withContext Result.failure(
     Exception("Steam not found at ${steamExe.absolutePath}. Please install Steam first.")
    )
   }

   // 3. Build Steam launch arguments
   // -applaunch <appId> launches game directly
   val arguments = listOf(
    "-applaunch",
    appId.toString()
   )

   AppLogger.i(TAG, "Launching Steam with arguments: $arguments")

   // 4. Launch Steam via Wine
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
  */
 suspend fun launchSteamBigPicture(containerId: String): Result<Unit> =
  withContext(Dispatchers.IO) {
   try {
    AppLogger.i(TAG, "Launching Steam Big Picture for container=$containerId")

    // 1. Get Winlator container
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

    // 2. Build Steam.exe path (case-sensitive on Android)
    val steamExe = File(emulatorContainer.rootPath, "drive_c/Program Files (x86)/Steam/Steam.exe")

    if (!steamExe.exists()) {
     return@withContext Result.failure(
      Exception("Steam not found at ${steamExe.absolutePath}. Please install Steam first.")
     )
    }

    // 3. Launch Steam Big Picture mode
    // -bigpicture launches fullscreen console-like UI
    val arguments = listOf("-bigpicture")

    AppLogger.i(TAG, "Launching Steam Big Picture mode")

    // 4. Launch Steam with Big Picture argument
    val processResult = winlatorEmulator.launchExecutable(
     container = emulatorContainer,
     executable = steamExe,
     arguments = arguments
    )

    if (processResult.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to launch Steam Big Picture: ${processResult.exceptionOrNull()?.message}")
     )
    }

    val process = processResult.getOrElse {
     return@withContext Result.failure(
      Exception("Failed to get process handle: ${it.message}")
     )
    }
    AppLogger.i(TAG, "Steam Big Picture launched successfully: PID ${process.pid}")

    Result.success(Unit)

   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to launch Steam Big Picture", e)
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

    // 1. Get Winlator container
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

    // 2. Verify Steam.exe exists
    val steamExe = File(emulatorContainer.rootPath, "drive_c/Program Files (x86)/Steam/Steam.exe")
    if (!steamExe.exists()) {
     return@withContext Result.failure(
      Exception("Steam not found at ${steamExe.absolutePath}. Please install Steam first.")
     )
    }

    // 3. Get Wine builtin explorer.exe path
    // Explorer.exe is a Wine builtin, located in Wine's library directory
    val rootfsDir = File(winlatorEmulator.getRootfsPath())
    val explorerExe = File(rootfsDir, "opt/wine/lib/wine/x86_64-windows/explorer.exe")

    if (!explorerExe.exists()) {
     return@withContext Result.failure(
      Exception("Wine explorer.exe not found at ${explorerExe.absolutePath}")
     )
    }

    // 4. Build command: wine explorer.exe /desktop=shell,1280x720 "C:\Program Files (x86)\Steam\Steam.exe" -no-cef-sandbox -noreactlogin -tcp -console
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

    // 5. Launch via Winlator
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
