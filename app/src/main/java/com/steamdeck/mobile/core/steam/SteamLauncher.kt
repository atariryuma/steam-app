package com.steamdeck.mobile.core.steam

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Client ランチャー
 *
 * Steam Client viagamelaunchdo
 */
@Singleton
class SteamLauncher @Inject constructor(
 private val context: Context,
 private val winlatorEmulator: WinlatorEmulator,
 private val database: SteamDeckDatabase
) {
 companion object {
  private const val TAG = "SteamLauncher"
 }

 /**
  * Steam viagamelaunch
  */
 suspend fun launchGameViaSteam(
  containerId: String,
  appId: Long
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   Log.i(TAG, "Launching game via Steam: appId=$appId, containerId=$containerId")

   // 1. Winlator containerretrieve
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

   // 2. Steam.exe path構築
   val steamExe = File(emulatorContainer.rootPath, "drive_c/Program Files (x86)/Steam/steam.exe")

   if (!steamExe.exists()) {
    return@withContext Result.failure(
     Exception("Steam not found at ${steamExe.absolutePath}. Please install Steam first.")
    )
   }

   // 3. Steam launchargument構築
   // -applaunch <appId> gamedirectlylaunch
   val arguments = listOf(
    "-applaunch",
    appId.toString()
   )

   Log.i(TAG, "Launching Steam with arguments: $arguments")

   // 4. Wine via Steam launch
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
  * Steam Client launch
  */
 suspend fun launchSteamClient(containerId: String): Result<Unit> =
  withContext(Dispatchers.IO) {
   try {
    Log.i(TAG, "Launching Steam Client for container: $containerId")

    // 1. Winlator containerretrieve
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

    // 2. Steam.exe path構築
    val steamExe = File(emulatorContainer.rootPath, "drive_c/Program Files (x86)/Steam/steam.exe")

    if (!steamExe.exists()) {
     return@withContext Result.failure(
      Exception("Steam not found at ${steamExe.absolutePath}. Please install Steam first.")
     )
    }

    Log.i(TAG, "Launching Steam from: ${steamExe.absolutePath}")

    // 3. Wine via Steam Client launch
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
  * Winlator EmulatorContainer retrieve
  */
 private suspend fun getEmulatorContainer(containerId: String): Result<com.steamdeck.mobile.domain.emulator.EmulatorContainer> =
  withContext(Dispatchers.IO) {
   try {
    // Winlatorfromcontainerリストretrieve
    val containersResult = winlatorEmulator.listContainers()
    if (containersResult.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to list containers: ${containersResult.exceptionOrNull()?.message}")
     )
    }

    val containers = containersResult.getOrNull() ?: emptyList()

    // containerID マッチング（Stringtype ID directly比較）
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
}
