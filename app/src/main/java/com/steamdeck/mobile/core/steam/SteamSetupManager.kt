package com.steamdeck.mobile.core.steam

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.R
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.entity.Box64Preset
import com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus
import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Client Setup Manager
 *
 * Manages Steam installation within Winlator containers
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

 // CRITICAL FIX: Prevent race condition in concurrent container creation
 private val containerCreationMutex = Mutex()

 /**
  * Steam installation result
  */
 sealed class SteamInstallResult {
  data class Success(
   val installPath: String,
   val containerId: String
  ) : SteamInstallResult()
  data class Error(val message: String) : SteamInstallResult()
  data class Progress(
   val progress: Float,
   val message: String,
   val currentStep: Int = 0,
   val totalSteps: Int = 5,
   val elapsedTimeMs: Long = 0L
  ) : SteamInstallResult()
 }

 /**
  * Steam Client installation
  *
  * @param containerId Winlator container ID (String type)
  * @param progressCallback Installation progress callback
  */
 suspend fun installSteam(
  containerId: String,
  progressCallback: ((Float, String) -> Unit)? = null
 ): Result<SteamInstallResult> = withContext(Dispatchers.IO) {
  try {
   Log.i(TAG, "Starting Steam installation for container: $containerId")

   // 0. Winlator emulator initialization (Box64/Wine extraction)
   progressCallback?.invoke(0.0f, "Checking Winlator initialization...")
   val available = winlatorEmulator.isAvailable().getOrNull() ?: false

   if (!available) {
    Log.w(TAG, "Winlator not initialized - starting initialization (this may take 2-3 minutes)...")

    // Winlator initialization (Box64/Wine binary extraction)
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
       "Failed to initialize Winlator environment.\n\n" +
       "Error: ${error?.message}\n\n" +
       "Solutions:\n" +
       "• Check free storage space (minimum 500MB required)\n" +
       "• Restart the app\n" +
       "• Restart your device"
      )
     )
    }

    Log.i(TAG, "Winlator initialization completed successfully")
   } else {
    Log.i(TAG, "Winlator already initialized, skipping initialization")
   }

   // progress: 0.4 ~ 0.5 (10%)
   progressCallback?.invoke(0.4f, "Downloading Steam installer...")

   // 1. Download SteamSetup.exe (32-bit NSIS installer)
   val installerResult = steamInstallerService.downloadInstaller()
   if (installerResult.isFailure) {
    return@withContext Result.success(
     SteamInstallResult.Error("Failed to download Steam installer: ${installerResult.exceptionOrNull()?.message}")
    )
   }

   val installerFile = installerResult.getOrElse {
    return@withContext Result.success(
     SteamInstallResult.Error("Installer not available: ${it.message}")
    )
   }
   Log.i(TAG, "Installer downloaded: ${installerFile.absolutePath}")

   // progress: 0.5 ~ 0.6 (10%)
   progressCallback?.invoke(0.5f, "Preparing container...")

   // 2. Get or create container
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

   // Verify Wine Mono installation
   val monoDir = File(container.rootPath, "drive_c/windows/mono")
   if (monoDir.exists()) {
    Log.i(TAG, "Wine Mono installation confirmed: ${monoDir.absolutePath}")
   } else {
    Log.w(TAG, "Wine Mono not found - 32-bit apps may not work")
    Log.w(TAG, "Expected location: ${monoDir.absolutePath}")
   }

   // progress: 0.6 ~ 0.95 (35%)
   progressCallback?.invoke(0.6f, "Extracting Steam from NSIS installer...")

   // METHOD 1 (PRIORITY): Extract Steam Client directly from NSIS installer
   // This bypasses the WoW64 requirement by extracting files with 7-Zip
   Log.i(TAG, "Attempting NSIS extraction (Method 1 - Recommended)...")

   val steamDir = File(container.rootPath, "drive_c/Program Files (x86)/Steam")
   val extractResult = steamInstallerService.extractSteamFromNSIS(installerFile, steamDir)

   if (extractResult.isSuccess) {
    Log.i(TAG, "Steam Client installed successfully via NSIS extraction")
    progressCallback?.invoke(0.95f, "NSIS extraction completed successfully")
   } else {
    Log.w(TAG, "NSIS extraction failed: ${extractResult.exceptionOrNull()?.message}")
    Log.i(TAG, "Falling back to Wine installer execution (Method 2 - Requires WoW64)...")

    // METHOD 2 (FALLBACK): Run SteamSetup.exe via Wine
    // WARNING: This requires WoW64 support and may fail on 64-bit only Wine builds
    progressCallback?.invoke(0.6f, "Copying installer to container...")

    // 3. Copy installer to container
    val copyResult = copyInstallerToContainer(container, installerFile)
    if (copyResult.isFailure) {
     return@withContext Result.success(
      SteamInstallResult.Error("Failed to copy installer: ${copyResult.exceptionOrNull()?.message}")
     )
    }

    val containerInstaller = copyResult.getOrElse {
     return@withContext Result.success(
      SteamInstallResult.Error("Container installer not available: ${it.message}")
     )
    }
    Log.i(TAG, "Installer copied to container: ${containerInstaller.absolutePath}")

    // progress: 0.7 ~ 0.95 (25%)
    progressCallback?.invoke(0.7f, "Running Steam installer via Wine (WoW64 mode)...")

    // 4. Run installer via Wine with DEBUG logging enabled
    val installResult = runSteamInstaller(
     container = container,
     installerFile = containerInstaller,
     progressCallback = { progress ->
      // Map installer progress (0.0-1.0) to overall progress (0.7-0.95)
      val mappedProgress = 0.7f + (progress * 0.25f)
      progressCallback?.invoke(mappedProgress, "Installing Steam...")
     }
    )
    if (installResult.isFailure) {
     return@withContext Result.success(
      SteamInstallResult.Error("Failed to run installer: ${installResult.exceptionOrNull()?.message}")
     )
    }

    progressCallback?.invoke(0.95f, "Wine installer execution completed")
   }

   // progress: 0.95 ~ 1.0 (5%)
   progressCallback?.invoke(0.95f, "Finalizing installation...")

   // 5. Save installation information (container.id is already String type)
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
  * Get or create container
  *
  * @param containerId Winlator container ID (String type)
  */
 private suspend fun getOrCreateContainer(containerId: String): Result<com.steamdeck.mobile.domain.emulator.EmulatorContainer> =
  withContext(Dispatchers.IO) {
   try {
    // CRITICAL FIX: Protect entire container creation flow with mutex to prevent race conditions
    // Multiple concurrent calls could create duplicate containers without synchronization
    containerCreationMutex.withLock {
     // Convert container ID to Long and search database (maintains existing Long type)
     val containerIdLong = containerId.toLongOrNull()
     var containerEntity = if (containerIdLong != null) {
      database.winlatorContainerDao().getContainerById(containerIdLong)
     } else {
      null
     }

     // If container doesn't exist, create new one in database
     if (containerEntity == null) {
      Log.w(TAG, "Container $containerId not found in database, creating default container...")
      val newEntity = WinlatorContainerEntity(
       id = containerIdLong ?: 0, // Save as Long type, 0 for auto-generate
       name = "Steam Client",
       box64Preset = Box64Preset.STABILITY, // Steam prioritizes stability
       wineVersion = "9.0+"
      )

      try {
       val newId = database.winlatorContainerDao().insertContainer(newEntity)
       Log.i(TAG, "Created default container entity with ID: $newId")
       containerEntity = newEntity.copy(id = newId)
      } catch (e: Exception) {
       Log.e(TAG, "Failed to create default container entity", e)
       return@withLock Result.failure(Exception("Failed to create container in database: ${e.message}"))
      }
     }

     // Retrieve container list from Winlator
     val containersResult = winlatorEmulator.listContainers()
     if (containersResult.isFailure) {
      return@withLock Result.failure(
       Exception("Failed to list containers: ${containersResult.exceptionOrNull()?.message}")
      )
     }

     val containers = containersResult.getOrNull() ?: emptyList()

     // Try to match container ID (String type comparison)
     val container = containers.firstOrNull { it.id == containerId }

     if (container != null) {
      if (!isWinePrefixInitialized(container)) {
       return@withLock Result.failure(
        Exception(
         "Wine prefix initialization failed (system.reg missing). " +
          "Please reinitialize Winlator and try again."
        )
       )
      }
      return@withLock Result.success(container)
     }

     // If container doesn't exist, create new one
     Log.i(TAG, "Container not found, creating new container for ID: $containerId")
     val config = com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig(
      name = containerEntity!!.name,
      performancePreset = when (containerEntity.box64Preset) {
       com.steamdeck.mobile.data.local.database.entity.Box64Preset.PERFORMANCE ->
        com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_PERFORMANCE
       com.steamdeck.mobile.data.local.database.entity.Box64Preset.STABILITY ->
        com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_STABILITY
       com.steamdeck.mobile.data.local.database.entity.Box64Preset.CUSTOM ->
        com.steamdeck.mobile.domain.emulator.PerformancePreset.BALANCED
      }
     )

     val created = winlatorEmulator.createContainer(config)
     if (created.isFailure) {
      return@withLock Result.failure(
       Exception(
        "Failed to initialize Wine prefix: ${created.exceptionOrNull()?.message}"
       )
      )
     }

     val createdContainer = created.getOrNull()
      ?: return@withLock Result.failure(Exception("Container creation failed"))

     Log.d(TAG, "Created container - ID: ${createdContainer.id}, rootPath: ${createdContainer.rootPath}")

     if (!isWinePrefixInitialized(createdContainer)) {
      val systemReg = File(createdContainer.rootPath, "system.reg")
      val userReg = File(createdContainer.rootPath, "user.reg")
      Log.e(TAG, "Wine prefix check failed - system.reg exists: ${systemReg.exists()}, user.reg exists: ${userReg.exists()}")
      Log.e(TAG, "  rootPath: ${createdContainer.rootPath}")
      Log.e(TAG, "  rootPath.absolutePath: ${createdContainer.rootPath.absolutePath}")
      Log.e(TAG, "  systemReg.absolutePath: ${systemReg.absolutePath}")
      Log.e(TAG, "  systemReg.exists(): ${systemReg.exists()}")
      Log.e(TAG, "  systemReg.canRead(): ${systemReg.canRead()}")
      return@withLock Result.failure(
       Exception(
        "Wine prefix initialization failed (system.reg missing). " +
         "Please reinitialize Winlator and try again."
       )
      )
     }

     Result.success(createdContainer)
    }

   } catch (e: Exception) {
    Log.e(TAG, "Failed to get or create container", e)
    Result.failure(e)
   }
  }

 /**
  * Check if Wine prefix is properly initialized with registry files.
  *
  * IMPORTANT: Includes retry logic because file system writes may not be
  * immediately visible after wineboot completes (filesystem buffer flush delay).
  *
  * @param container Container to check
  * @return true if both system.reg and user.reg exist
  */
 private suspend fun isWinePrefixInitialized(container: com.steamdeck.mobile.domain.emulator.EmulatorContainer): Boolean = withContext(Dispatchers.IO) {
  val systemReg = File(container.rootPath, "system.reg")
  val userReg = File(container.rootPath, "user.reg")

  // Retry up to 5 times with 200ms delay to handle filesystem buffer flush
  repeat(5) { attempt ->
   if (systemReg.exists() && userReg.exists()) {
    if (attempt > 0) {
     Log.d(TAG, "Wine prefix files found after ${attempt} retries")
    }
    return@withContext true
   }

   if (attempt < 4) {
    Log.d(TAG, "Wine prefix files not found (attempt ${attempt + 1}/5), waiting 200ms...")
    delay(200)
   }
  }

  Log.e(TAG, "Wine prefix files still not found after 5 attempts (1 second total)")
  false
 }

 /**
  * Copy installer to container - CURRENT IMPLEMENTATION
  *
  * Copies SteamSetup.exe to the Wine container's Downloads directory
  * for execution via Wine.
  */
 private suspend fun copyInstallerToContainer(
  container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
  installerFile: File
 ): Result<File> = withContext(Dispatchers.IO) {
  try {
   // Copy to container drive_c/users/Public/Downloads
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
  * Execute Steam installer via Wine - CURRENT IMPLEMENTATION
  *
  * Runs SteamSetup.exe in Wine with Windows 10 registry configuration.
  * Requires WinlatorEmulator.setWindowsVersion() to have been called during container creation.
  *
  * Enhanced installation verification:
  * 1. Wait for process to finish
  * 2. Verify steam.exe exists (retry up to 3 times)
  * 3. Return error on timeout
  *
  * NOTE: This method is NOT deprecated. It's the primary installation method.
  */
 private suspend fun runSteamInstaller(
  container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
  installerFile: File,
  progressCallback: ((Float) -> Unit)? = null
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   // DEBUG MODE: Temporarily disable silent mode to see installer errors
   // In production, use /S for silent mode
   // /S - Silent installation (no user interaction required)
   // /D - Custom installation directory (MUST be last argument per NSIS spec)
   //
   // Current issue: SteamSetup.exe runs but doesn't install (even with Windows 10 + wineboot -u)
   // Trying GUI mode to see error messages
   val useSilentMode = false  // TODO: Set to true for production
   val arguments = if (useSilentMode) {
    listOf(
     "/S",  // Silent mode
     "/D=C:\\Program Files (x86)\\Steam"
    )
   } else {
    // GUI mode - installer will show errors
    listOf("/D=C:\\Program Files (x86)\\Steam")
   }

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

   // Wait for installer to complete (Timeout: 5 minutes)
   var waitTime = 0L
   val maxWaitTime = 5 * 60 * 1000L // 5 minutes
   val checkInterval = 2000L // 2 seconds
   var processCompleted = false

   while (waitTime < maxWaitTime) {
    val statusResult = winlatorEmulator.getProcessStatus(process.id)
    val isRunning = statusResult.getOrNull()?.isRunning ?: false
    if (!isRunning) {
     Log.i(TAG, "Steam installer process completed after ${waitTime / 1000}s")
     processCompleted = true
     progressCallback?.invoke(0.9f)
     break
    }

    // Update progress based on elapsed time
    val progress = (waitTime.toFloat() / maxWaitTime.toFloat()).coerceIn(0f, 0.85f)
    progressCallback?.invoke(progress)

    delay(checkInterval)
    waitTime += checkInterval
   }

   if (!processCompleted) {
    Log.e(TAG, "Steam installer timeout after 5 minutes")
    // CRITICAL FIX: Kill the process before returning failure
    try {
     winlatorEmulator.killProcess(process.id)
     Log.d(TAG, "Killed timed-out Steam installer process: ${process.id}")
    } catch (e: Exception) {
     Log.w(TAG, "Failed to kill timed-out process: ${e.message}")
    }
    return@withContext Result.failure(
     Exception(
      "Steam installer timed out (5 minutes).\n" +
      "Please check device storage space."
     )
    )
   }

   // Verify installation complete: check steam.exe exists (retry up to 3 times)
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
    delay(retryDelay)
   }

   // Verification failed - provide detailed error message
   Log.e(TAG, "Steam installation verification failed: steam.exe not found at ${steamExe.absolutePath}")

   // CRITICAL: Explain the WoW64 issue to the user
   val errorMessage = buildString {
    appendLine("Steam installation failed.")
    appendLine()
    appendLine("KNOWN ISSUE: SteamSetup.exe is a 32-bit application that requires WoW64 support.")
    appendLine()
    appendLine("Error details:")
    appendLine("• Wine could not load wow64.dll (STATUS_DLL_NOT_FOUND)")
    appendLine("• 32-bit Windows applications require additional Wine components")
    appendLine()
    appendLine("SOLUTION:")
    appendLine("This feature is currently under development.")
    appendLine("Please check for app updates that include full Wine Mono/WoW64 support.")
   }

   return@withContext Result.failure(Exception(errorMessage))

  } catch (e: Exception) {
   Log.e(TAG, "Failed to run Steam installer", e)
   Result.failure(e)
  }
 }

 /**
  * Check if Steam is already installed
  */
 suspend fun isSteamInstalled(): Boolean = withContext(Dispatchers.IO) {
  val installation = steamInstallerService.getInstallation()
  installation?.status == SteamInstallStatus.INSTALLED
 }

 /**
  * Get Steam installation information
  */
 suspend fun getSteamInstallation() = withContext(Dispatchers.IO) {
  steamInstallerService.getInstallation()
 }
}
