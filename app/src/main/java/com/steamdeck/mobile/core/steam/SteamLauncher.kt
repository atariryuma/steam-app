package com.steamdeck.mobile.core.steam

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
   Log.i(TAG, "Launching game via Steam: appId=$appId, containerId=$containerId")

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

   Log.i(TAG, "Launching Steam with arguments: $arguments")

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
   Log.i(TAG, "Steam launched successfully: PID ${process.pid}")

   Result.success(Unit)

  } catch (e: Exception) {
   Log.e(TAG, "Failed to launch game via Steam", e)
   Result.failure(e)
  }
 }

 /**
  * Launch Steam Client
  */
 suspend fun launchSteamClient(containerId: String): Result<Unit> =
  withContext(Dispatchers.IO) {
   try {
    Log.i(TAG, "Launching Steam Client for container: $containerId")

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

    Log.i(TAG, "Launching Steam from: ${steamExe.absolutePath}")

    // 3. Launch Steam Client via Wine
    val processResult = winlatorEmulator.launchExecutable(
     container = emulatorContainer,
     executable = steamExe,
     arguments = emptyList()
    )

    if (processResult.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to launch Steam Client: ${processResult.exceptionOrNull()?.message}")
     )
    }

    val process = processResult.getOrElse {
     return@withContext Result.failure(
      Exception("Failed to get process handle: ${it.message}")
     )
    }
    Log.i(TAG, "Steam Client launched successfully: PID ${process.pid}")

    Result.success(Unit)

   } catch (e: Exception) {
    Log.e(TAG, "Failed to launch Steam Client", e)
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
    Log.e(TAG, "Failed to get emulator container", e)
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
   Log.i(TAG, "Opened Steam install page for appId=$appId")
   Result.success(Unit)

  } catch (e: Exception) {
   Log.e(TAG, "Failed to open Steam install page", e)
   Result.failure(
    Exception("Steam app not found. Please install Steam from Google Play Store.")
   )
  }
 }
}
