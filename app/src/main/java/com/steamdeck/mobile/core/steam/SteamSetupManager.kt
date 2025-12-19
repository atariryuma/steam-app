package com.steamdeck.mobile.core.steam

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.entity.Box64Preset
import com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus
import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Client セットアップマネージャー
 *
 * Winlatorcontainer内 Steaminstallationmanagementdo
 */
@Singleton
class SteamSetupManager @Inject constructor(
 private val context: Context,
 private val winlatorEmulator: WinlatorEmulator,
 private val steamInstallerService: SteamInstallerService,
 private val database: SteamDeckDatabase
) {
 companion object {
  private const val TAG = "SteamSetupManager"
  private const val DEFAULT_STEAM_PATH = "C:\\Program Files (x86)\\Steam"
 }

 /**
  * Steam installationresult
  */
 sealed class SteamInstallResult {
  data class Success(
   val installPath: String,
   val containerId: String
  ) : SteamInstallResult()
  data class Error(val message: String) : SteamInstallResult()
  data class Progress(val progress: Float, val message: String) : SteamInstallResult()
 }

 /**
  * Steam Client installation
  *
  * @param containerId Winlatorcontainer ID (Stringtype)
  * @param progressCallback installationprogressコールバック
  */
 suspend fun installSteam(
  containerId: String,
  progressCallback: ((Float, String) -> Unit)? = null
 ): Result<SteamInstallResult> = withContext(Dispatchers.IO) {
  try {
   Log.i(TAG, "Starting Steam installation for container: $containerId")

   // 0. Winlatorエミュレータinitialization（Box64/Wine展開）
   progressCallback?.invoke(0.0f, "Checking Winlator initialization...")
   val available = winlatorEmulator.isAvailable().getOrNull() ?: false

   if (!available) {
    Log.w(TAG, "Winlator not initialized - starting initialization (this may take 2-3 minutes)...")

    // Winlatorinitialization（Box64/Wineバイナリ展開）
    // progress: 0.0 ~ 0.4 (40%)
    val initResult = winlatorEmulator.initialize { progress, message ->
     // Map 0.0-1.0 progress to 0.0-0.4 range
     progressCallback?.invoke(progress * 0.4f, message)
    }

    if (initResult.isFailure) {
     val error = initResult.exceptionOrNull()
     Log.e(TAG, "Winlator initialization failed", error)
     return@withContext Result.success(
      SteamInstallResult.Error(
       "Winlatorenvironment initialization failuredid。\n\n" +
       "error: ${error?.message}\n\n" +
       "解決方法:\n" +
       "• ストレージ空き容量confirmation（最低500MB必要）\n" +
       "• アプリ再launchplease\n" +
       "• 端末再launchplease"
      )
     )
    }

    Log.i(TAG, "Winlator initialization completed successfully")
   } else {
    Log.i(TAG, "Winlator already initialized, skipping initialization")
   }

   // progress: 0.4 ~ 0.5 (10%)
   progressCallback?.invoke(0.4f, "Downloading Steam installer...")

   // 1. Steam installerdownload
   // progress: 0.4 ~ 0.5 (10%)
   val installerResult = steamInstallerService.downloadInstaller()
   if (installerResult.isFailure) {
    return@withContext Result.success(
     SteamInstallResult.Error("Failed to download installer: ${installerResult.exceptionOrNull()?.message}")
    )
   }

   val installerFile = installerResult.getOrElse {
    return@withContext Result.success(
     SteamInstallResult.Error("Installer file not found after download: ${it.message}")
    )
   }
   Log.i(TAG, "Installer downloaded: ${installerFile.absolutePath}")

   // progress: 0.5 ~ 0.6 (10%)
   progressCallback?.invoke(0.5f, "Preparing container...")

   // 2. containerretrieveorcreate
   val containerResult = getOrCreateContainer(containerId)
   if (containerResult.isFailure) {
    return@withContext Result.success(
     SteamInstallResult.Error("Failed to get container: ${containerResult.exceptionOrNull()?.message}")
    )
   }

   val container = containerResult.getOrElse {
    return@withContext Result.success(
     SteamInstallResult.Error("Container not available: ${it.message}")
    )
   }
   Log.i(TAG, "Using container: ${container.name} (${container.id})")

   // progress: 0.6 ~ 0.65 (5%)
   progressCallback?.invoke(0.6f, "Copying installer to container...")

   // 3. installercontainercopy to
   val containerInstallerPath = copyInstallerToContainer(container, installerFile)
   if (containerInstallerPath.isFailure) {
    return@withContext Result.success(
     SteamInstallResult.Error("Failed to copy installer: ${containerInstallerPath.exceptionOrNull()?.message}")
    )
   }

   val containerInstaller = containerInstallerPath.getOrElse {
    return@withContext Result.success(
     SteamInstallResult.Error("Failed to get installer path in container: ${it.message}")
    )
   }
   Log.i(TAG, "Installer copied to container: ${containerInstaller.absolutePath}")

   // progress: 0.65 ~ 0.95 (30%)
   progressCallback?.invoke(0.65f, "Running Steam installer (this may take a few minutes)...")

   // 4. Wine via Steam installerexecution
   val installResult = runSteamInstaller(
    container = container,
    installerFile = containerInstaller,
    progressCallback = { installProgress ->
     // Map installer progress (0.0-1.0) to 0.65-0.95 range
     progressCallback?.invoke(0.65f + installProgress * 0.3f, "Installing Steam Client...")
    }
   )
   if (installResult.isFailure) {
    return@withContext Result.success(
     SteamInstallResult.Error("Failed to run installer: ${installResult.exceptionOrNull()?.message}")
    )
   }

   // progress: 0.95 ~ 1.0 (5%)
   progressCallback?.invoke(0.95f, "Finalizing installation...")

   // 5. installationinformationsave (container.id 既 Stringtype)
   steamInstallerService.saveInstallation(
    containerId = container.id,
    installPath = DEFAULT_STEAM_PATH,
    status = SteamInstallStatus.INSTALLED
   )

   progressCallback?.invoke(1.0f, "Installation complete")
   Log.i(TAG, "Steam installation completed successfully")

   Result.success(SteamInstallResult.Success(
    installPath = DEFAULT_STEAM_PATH,
    containerId = container.id
   ))

  } catch (e: Exception) {
   Log.e(TAG, "Steam installation failed", e)
   Result.success(SteamInstallResult.Error(e.message ?: "Unknown error"))
  }
 }

 /**
  * containerretrieveorcreate
  *
  * @param containerId Winlatorcontainer ID (Stringtype)
  */
 private suspend fun getOrCreateContainer(containerId: String): Result<com.steamdeck.mobile.domain.emulator.EmulatorContainer> =
  withContext(Dispatchers.IO) {
   try {
    // containerIDLong conversionしてdataベース検索（dataベース existingLongtype維持）
    val containerIdLong = containerId.toLongOrNull()
    var containerEntity = if (containerIdLong != null) {
     database.winlatorContainerDao().getContainerById(containerIdLong)
    } else {
     null
    }

    // container existしないcase dataベース 新規create
    if (containerEntity == null) {
     Log.w(TAG, "Container $containerId not found in database, creating default container...")
     val newEntity = WinlatorContainerEntity(
      id = containerIdLong ?: 0, // Longtype save、0 case auto-generate
      name = "Steam Client",
      box64Preset = Box64Preset.STABILITY, // Steam 安定性重視
      wineVersion = "9.0+"
     )

     try {
      val newId = database.winlatorContainerDao().insertContainer(newEntity)
      Log.i(TAG, "Created default container entity with ID: $newId")
      containerEntity = newEntity.copy(id = newId)
     } catch (e: Exception) {
      Log.e(TAG, "Failed to create default container entity", e)
      return@withContext Result.failure(Exception("Failed to create container in database: ${e.message}"))
     }
    }

    // Winlatorfromcontainerリストretrieve
    val containersResult = winlatorEmulator.listContainers()
    if (containersResult.isFailure) {
     return@withContext Result.failure(
      Exception("Failed to list containers: ${containersResult.exceptionOrNull()?.message}")
     )
    }

    val containers = containersResult.getOrNull() ?: emptyList()

    // containerID マッチング試みる (Stringtype 比較)
    val container = containers.firstOrNull { it.id == containerId }

    if (container != null) {
     return@withContext Result.success(container)
    }

    // container existしないcase 新規create
    Log.i(TAG, "Container not found, creating new container for ID: $containerId")
    val config = com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig(
     name = containerEntity.name,
     performancePreset = when (containerEntity.box64Preset) {
      com.steamdeck.mobile.data.local.database.entity.Box64Preset.PERFORMANCE ->
       com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_PERFORMANCE
      com.steamdeck.mobile.data.local.database.entity.Box64Preset.STABILITY ->
       com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_STABILITY
      com.steamdeck.mobile.data.local.database.entity.Box64Preset.CUSTOM ->
       com.steamdeck.mobile.domain.emulator.PerformancePreset.BALANCED
     }
    )

    winlatorEmulator.createContainer(config)

   } catch (e: Exception) {
    Log.e(TAG, "Failed to get or create container", e)
    Result.failure(e)
   }
  }

 /**
  * installercontainercopy to
  */
 private suspend fun copyInstallerToContainer(
  container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
  installerFile: File
 ): Result<File> = withContext(Dispatchers.IO) {
  try {
   // container drive_c/users/Public/Downloads copy to
   val downloadsDir = File(container.rootPath, "drive_c/users/Public/Downloads")
   if (!downloadsDir.exists()) {
    downloadsDir.mkdirs()
   }

   val containerInstaller = File(downloadsDir, "SteamSetup.exe")
   installerFile.copyTo(containerInstaller, overwrite = true)

   Log.i(TAG, "Copied installer to: ${containerInstaller.absolutePath}")
   Result.success(containerInstaller)

  } catch (e: Exception) {
   Log.e(TAG, "Failed to copy installer to container", e)
   Result.failure(e)
  }
 }

 /**
  * Wine via Steam installerexecution
  *
  * installationcompletedverification強化:
  * 1. processend待機
  * 2. steam.exe existconfirmation (maximum3回retry)
  * 3. Timeout時 error返す
  */
 private suspend fun runSteamInstaller(
  container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
  installerFile: File,
  progressCallback: ((Float) -> Unit)? = null
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   // Steam installer interactive mode (silent mode /S incompatible with Wine on Android)
   // /D = installation directory (specify path even without /S)
   // User must click through installer wizard in Wine window
   val arguments = listOf(
    "/D=C:\\Program Files (x86)\\Steam"
   )

   Log.i(TAG, "Launching Steam installer with arguments: $arguments")

   val processResult = winlatorEmulator.launchExecutable(
    container = container,
    executable = installerFile,
    arguments = arguments
   )

   if (processResult.isFailure) {
    return@withContext Result.failure(
     Exception("Failed to launch installer: ${processResult.exceptionOrNull()?.message}")
    )
   }

   val process = processResult.getOrElse {
    return@withContext Result.failure(
     Exception("Failed to get process handle: ${it.message}")
    )
   }
   Log.i(TAG, "Steam installer process started: PID ${process.pid}")

   // インストーラー completed待つ (Timeout: 5minutes)
   var waitTime = 0L
   val maxWaitTime = 5 * 60 * 1000L // 5 minutes
   val checkInterval = 2000L // 2 seconds
   var processCompleted = false

   while (waitTime < maxWaitTime) {
    val statusResult = winlatorEmulator.getProcessStatus(process.id)
    val isRunning = statusResult.getOrNull()?.isRunning ?: false
    if (!isRunning) {
     Log.i(TAG, "Steam installer process completed")
     processCompleted = true
     progressCallback?.invoke(0.9f)
     break
    }

    // Update progress based on elapsed time
    val progress = (waitTime.toFloat() / maxWaitTime.toFloat()).coerceIn(0f, 0.85f)
    progressCallback?.invoke(progress)

    kotlinx.coroutines.delay(checkInterval)
    waitTime += checkInterval
   }

   if (!processCompleted) {
    Log.e(TAG, "Steam installer timeout after 5 minutes")
    return@withContext Result.failure(
     Exception(
      "Steam インストーラー Timeoutdid (5minutes)。\n" +
      "端末 ストレージ容量confirmationplease。"
     )
    )
   }

   // installationcompletedverification: steam.exe existconfirmation (maximum3回retry)
   val steamExe = File(container.rootPath, "drive_c/Program Files (x86)/Steam/steam.exe")
   var retryCount = 0
   val maxRetries = 3
   val retryDelay = 2000L // 2 seconds

   while (retryCount < maxRetries) {
    if (steamExe.exists()) {
     Log.i(TAG, "Steam installation verified: ${steamExe.absolutePath}")
     progressCallback?.invoke(1.0f)
     return@withContext Result.success(Unit)
    }

    retryCount++
    Log.w(TAG, "steam.exe not found, retry $retryCount/$maxRetries")
    progressCallback?.invoke(0.9f + (retryCount.toFloat() / maxRetries.toFloat()) * 0.1f)
    kotlinx.coroutines.delay(retryDelay)
   }

   // verificationfailure
   Log.e(TAG, "Steam installation verification failed: steam.exe not found at ${steamExe.absolutePath}")
   return@withContext Result.failure(
    Exception(
     "Steam installation verification failuredid。\n" +
     "steam.exe not found: ${steamExe.absolutePath}\n\n" +
     "retryplease。"
    )
   )

  } catch (e: Exception) {
   Log.e(TAG, "Failed to run Steam installer", e)
   Result.failure(e)
  }
 }

 /**
  * Steam 既 installationされているかcheck
  */
 suspend fun isSteamInstalled(): Boolean = withContext(Dispatchers.IO) {
  val installation = steamInstallerService.getInstallation()
  installation?.status == SteamInstallStatus.INSTALLED
 }

 /**
  * Steam installationinformationretrieve
  */
 suspend fun getSteamInstallation() = withContext(Dispatchers.IO) {
  steamInstallerService.getInstallation()
 }
}
