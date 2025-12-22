package com.steamdeck.mobile.core.steam

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
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
  progressCallback: ((Float, String, String?) -> Unit)? = null
 ): Result<SteamInstallResult> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Starting Steam installation for container: $containerId")

   // 0. Winlator emulator initialization (Box64/Wine extraction)
   progressCallback?.invoke(0.0f, "Checking Winlator initialization...", null)
   val available = winlatorEmulator.isAvailable().getOrNull() ?: false

   if (!available) {
    AppLogger.w(TAG, "Winlator not initialized - starting initialization (this may take 2-3 minutes)...")

    // Winlator initialization (Box64/Wine binary extraction)
    // progress: 0.0 ~ 0.2 (20%) - First time only, 2-3 minutes
    val initResult = winlatorEmulator.initialize { progress, message ->
     // Map 0.0-1.0 progress to 0.0-0.2 range
     progressCallback?.invoke(progress * 0.2f, message, null)
    }

    if (initResult.isFailure) {
     val error = initResult.exceptionOrNull()
     AppLogger.e(TAG, "Winlator initialization failed", error)
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

    AppLogger.i(TAG, "Winlator initialization completed successfully")
   } else {
    AppLogger.i(TAG, "Winlator already initialized, skipping initialization")
   }

   // progress: 0.0 ~ 0.15 (15%) - Download installer
   progressCallback?.invoke(0.0f, "Downloading Steam installer...", null)

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
   AppLogger.i(TAG, "Installer downloaded: ${installerFile.absolutePath}")

   // progress: 0.15 ~ 0.60 (45%) - Container creation
   progressCallback?.invoke(0.15f, "Creating Wine container...", null)

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
   AppLogger.i(TAG, "Using container: ${container.name} (${container.id})")

   // Verify Wine Mono installation
   val monoDir = File(container.rootPath, "drive_c/windows/mono")
   if (monoDir.exists()) {
    AppLogger.i(TAG, "Wine Mono installation confirmed: ${monoDir.absolutePath}")
   } else {
    AppLogger.w(TAG, "Wine Mono not found - 32-bit apps may not work")
    AppLogger.w(TAG, "Expected location: ${monoDir.absolutePath}")
   }

   // progress: 0.60 ~ 0.75 (15%) - NSIS extraction
   progressCallback?.invoke(0.60f, "Extracting Steam from NSIS installer...", null)

   // METHOD 1 (PRIORITY): Extract Steam Client directly from NSIS installer
   // This bypasses the WoW64 requirement by extracting files with 7-Zip
   AppLogger.i(TAG, "Attempting NSIS extraction (Method 1 - Recommended)...")

   val steamDir = File(container.rootPath, "drive_c/Program Files (x86)/Steam")
   val extractResult = steamInstallerService.extractSteamFromNSIS(
    installerFile,
    steamDir,
    onProgress = { filesExtracted, totalFiles ->
     // Map file progress to overall progress (0.60 ~ 0.75 range)
     val fileProgress = filesExtracted.toFloat() / totalFiles.toFloat()
     val overallProgress = 0.60f + (fileProgress * 0.15f)
     val detail = "File $filesExtracted/$totalFiles"
     progressCallback?.invoke(overallProgress, "Extracting Steam files", detail)
    }
   )

   if (extractResult.isSuccess) {
    AppLogger.i(TAG, "Steam Client installed successfully via NSIS extraction")
    progressCallback?.invoke(0.75f, "NSIS extraction completed successfully", null)
   } else {
    AppLogger.w(TAG, "NSIS extraction failed: ${extractResult.exceptionOrNull()?.message}")
    AppLogger.i(TAG, "Falling back to Wine installer execution (Method 2 - Requires WoW64)...")

    // METHOD 2 (FALLBACK): Run SteamSetup.exe via Wine
    // WARNING: This requires WoW64 support and may fail on 64-bit only Wine builds
    progressCallback?.invoke(0.60f, "Copying installer to container...", null)

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
    AppLogger.i(TAG, "Installer copied to container: ${containerInstaller.absolutePath}")

    // progress: 0.60 ~ 0.75 (15%)
    progressCallback?.invoke(0.62f, "Running Steam installer via Wine (WoW64 mode)...", null)

    // 4. Run installer via Wine with DEBUG logging enabled
    val installResult = runSteamInstaller(
     container = container,
     installerFile = containerInstaller,
     progressCallback = { progress ->
      // Map installer progress (0.0-1.0) to overall progress (0.62-0.75)
      val mappedProgress = 0.62f + (progress * 0.13f)
      progressCallback?.invoke(mappedProgress, "Installing Steam...", null)
     }
    )
    if (installResult.isFailure) {
     return@withContext Result.success(
      SteamInstallResult.Error("Failed to run installer: ${installResult.exceptionOrNull()?.message}")
     )
    }

    progressCallback?.invoke(0.75f, "Wine installer execution completed", null)
   }

   // progress: 0.75 ~ 0.95 (20%) - Steam initialization
   progressCallback?.invoke(0.75f, "Initializing Steam client...", null)

   // 5. Initialize Steam client in background (creates steamapps/, config.vdf, etc.)
   val initResult = initializeSteamClient(container)
   if (initResult.isFailure) {
    AppLogger.w(TAG, "Steam initialization failed (non-fatal): ${initResult.exceptionOrNull()?.message}")
    // Continue anyway - steamapps directory will be created manually if needed
   } else {
    AppLogger.i(TAG, "Steam client initialized successfully")
   }

   // progress: 0.95 ~ 1.0 (5%)
   progressCallback?.invoke(0.95f, "Finalizing installation...", null)

   // 6. Save installation information (container.id is already String type)
   steamInstallerService.saveInstallation(
    containerId = container.id,
    installPath = DEFAULT_STEAM_PATH,
    status = SteamInstallStatus.INSTALLED
   )

   progressCallback?.invoke(1.0f, "Installation complete", null)
   AppLogger.i(TAG, "Steam installation completed successfully")

   Result.success(SteamInstallResult.Success(
    installPath = DEFAULT_STEAM_PATH,
    containerId = container.id
   ))

  } catch (e: Exception) {
   AppLogger.e(TAG, "Steam installation failed", e)
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
      AppLogger.w(TAG, "Container $containerId not found in database, creating default container...")
      val newEntity = WinlatorContainerEntity(
       id = containerIdLong ?: 0, // Save as Long type, 0 for auto-generate
       name = "Steam Client",
       box64Preset = Box64Preset.STABILITY, // Steam prioritizes stability
       wineVersion = "9.0+"
      )

      try {
       val newId = database.winlatorContainerDao().insertContainer(newEntity)
       AppLogger.i(TAG, "Created default container entity with ID: $newId")
       containerEntity = newEntity.copy(id = newId)
      } catch (e: Exception) {
       AppLogger.e(TAG, "Failed to create default container entity", e)
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
     // At this point containerEntity is guaranteed to be non-null (created in L274-291 block)
     AppLogger.i(TAG, "Container not found, creating new container for ID: $containerId")
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

     AppLogger.d(TAG, "Created container - ID: ${createdContainer.id}, rootPath: ${createdContainer.rootPath}")

     if (!isWinePrefixInitialized(createdContainer)) {
      val systemReg = File(createdContainer.rootPath, "system.reg")
      val userReg = File(createdContainer.rootPath, "user.reg")
      AppLogger.e(TAG, "Wine prefix check failed - system.reg exists: ${systemReg.exists()}, user.reg exists: ${userReg.exists()}")
      AppLogger.e(TAG, "  rootPath: ${createdContainer.rootPath}")
      AppLogger.e(TAG, "  rootPath.absolutePath: ${createdContainer.rootPath.absolutePath}")
      AppLogger.e(TAG, "  systemReg.absolutePath: ${systemReg.absolutePath}")
      AppLogger.e(TAG, "  systemReg.exists(): ${systemReg.exists()}")
      AppLogger.e(TAG, "  systemReg.canRead(): ${systemReg.canRead()}")
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
    AppLogger.e(TAG, "Failed to get or create container", e)
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
     AppLogger.d(TAG, "Wine prefix files found after ${attempt} retries")
    }
    return@withContext true
   }

   if (attempt < 4) {
    AppLogger.d(TAG, "Wine prefix files not found (attempt ${attempt + 1}/5), waiting 200ms...")
    delay(200)
   }
  }

  AppLogger.e(TAG, "Wine prefix files still not found after 5 attempts (1 second total)")
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

   AppLogger.i(TAG, "Copied installer to: ${containerInstaller.absolutePath}")
   Result.success(containerInstaller)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to copy installer to container", e)
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
  * 2. Verify Steam.exe exists (retry up to 3 times, case-sensitive on Android)
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

   AppLogger.i(TAG, "Launching Steam installer with arguments: $arguments")

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
   AppLogger.i(TAG, "Steam installer process started: PID ${process.pid}")

   // Wait for installer to complete (Timeout: 5 minutes)
   var waitTime = 0L
   val maxWaitTime = 5 * 60 * 1000L // 5 minutes
   val checkInterval = 2000L // 2 seconds
   var processCompleted = false

   while (waitTime < maxWaitTime) {
    val statusResult = winlatorEmulator.getProcessStatus(process.id)
    val isRunning = statusResult.getOrNull()?.isRunning ?: false
    if (!isRunning) {
     AppLogger.i(TAG, "Steam installer process completed after ${waitTime / 1000}s")
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
    AppLogger.e(TAG, "Steam installer timeout after 5 minutes")
    // CRITICAL FIX: Kill the process before returning failure
    try {
     winlatorEmulator.killProcess(process.id)
     AppLogger.d(TAG, "Killed timed-out Steam installer process: ${process.id}")
    } catch (e: Exception) {
     AppLogger.w(TAG, "Failed to kill timed-out process: ${e.message}")
    }
    return@withContext Result.failure(
     Exception(
      "Steam installer timed out (5 minutes).\n" +
      "Please check device storage space."
     )
    )
   }

   // Verify installation complete: check Steam.exe exists (retry up to 3 times)
   // Note: Case-sensitive on Android, SteamSetup.exe extracts as "Steam.exe" (capital S)
   val steamExe = File(container.rootPath, "drive_c/Program Files (x86)/Steam/Steam.exe")
   var retryCount = 0
   val maxRetries = 3
   val retryDelay = 2000L // 2 seconds

   while (retryCount < maxRetries) {
    if (steamExe.exists()) {
     AppLogger.i(TAG, "Steam installation verified: ${steamExe.absolutePath}")
     progressCallback?.invoke(1.0f)
     return@withContext Result.success(Unit)
    }

    retryCount++
    AppLogger.w(TAG, "Steam.exe not found, retry $retryCount/$maxRetries")
    progressCallback?.invoke(0.9f + (retryCount.toFloat() / maxRetries.toFloat()) * 0.1f)
    delay(retryDelay)
   }

   // Verification failed - provide detailed error message
   AppLogger.e(TAG, "Steam installation verification failed: Steam.exe not found at ${steamExe.absolutePath}")

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
   AppLogger.e(TAG, "Failed to run Steam installer", e)
   Result.failure(e)
  }
 }

 /**
  * Initialize Steam client in background
  *
  * Launches Steam.exe with -silent -noreactlogin flags to trigger first-run
  * initialization, which creates steamapps/, config.vdf, loginusers.vdf, etc.
  *
  * Technical details:
  * - Uses Wine container to execute Steam.exe
  * - Command: Steam.exe -silent -noreactlogin
  * - Timeout: 30 seconds (sufficient for directory creation)
  * - Creates: steamapps/, config/, config.vdf, loginusers.vdf
  *
  * @param container Wine container where Steam is installed
  * @return Result indicating success or failure (non-fatal)
  */
 private suspend fun initializeSteamClient(
  container: com.steamdeck.mobile.domain.emulator.EmulatorContainer
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Starting Steam client background initialization")

   // 1. Verify Steam.exe exists
   val steamExePath = "C:\\Program Files (x86)\\Steam\\Steam.exe"
   val steamExeFile = File(
    context.filesDir,
    "winlator/containers/${container.id}/drive_c/Program Files (x86)/Steam/Steam.exe"
   )

   if (!steamExeFile.exists()) {
    AppLogger.e(TAG, "Steam.exe not found at: ${steamExeFile.absolutePath}")
    return@withContext Result.failure(
     Exception("Steam.exe not found in container")
    )
   }

   AppLogger.d(TAG, "Steam.exe found: ${steamExeFile.absolutePath} (${steamExeFile.length()} bytes)")

   // 2. Launch Steam.exe with initialization flags
   // -silent: Run without UI
   // -noreactlogin: Disable React-based login UI (Wine compatibility)
   val arguments = listOf("-silent", "-noreactlogin")
   AppLogger.i(TAG, "Launching: $steamExePath ${arguments.joinToString(" ")}")

   val launchResult = winlatorEmulator.launchExecutable(
    container = container,
    executable = steamExeFile,
    arguments = arguments
   )

   if (launchResult.isFailure) {
    AppLogger.e(TAG, "Failed to launch Steam for initialization: ${launchResult.exceptionOrNull()?.message}")
    return@withContext Result.failure(
     launchResult.exceptionOrNull() ?: Exception("Failed to launch Steam")
    )
   }

   AppLogger.i(TAG, "Steam initialization launched successfully")

   // 3. Wait for initialization to complete (30 seconds timeout)
   // Steam creates directories within first few seconds
   val initTimeout = 30_000L // 30 seconds
   val startTime = System.currentTimeMillis()

   while (System.currentTimeMillis() - startTime < initTimeout) {
    delay(2000) // Check every 2 seconds

    // Check if steamapps directory was created
    val steamappsDir = File(
     context.filesDir,
     "winlator/containers/${container.id}/drive_c/Program Files (x86)/Steam/steamapps"
    )

    if (steamappsDir.exists()) {
     AppLogger.i(TAG, "Steamapps directory created successfully: ${steamappsDir.absolutePath}")

     // List directory contents for verification
     val contents = steamappsDir.listFiles()?.joinToString(", ") { it.name } ?: "empty"
     AppLogger.d(TAG, "Steamapps contents: $contents")

     return@withContext Result.success(Unit)
    }

    AppLogger.d(TAG, "Waiting for steamapps directory... (${(System.currentTimeMillis() - startTime) / 1000}s elapsed)")
   }

   // 4. Timeout - manually create steamapps directory structure
   AppLogger.w(TAG, "Steam initialization timeout (30s) - creating steamapps directory manually")

   // Create required Steam directory structure
   val steamappsDir = File(
    context.filesDir,
    "winlator/containers/${container.id}/drive_c/Program Files (x86)/Steam/steamapps"
   )
   val commonDir = File(steamappsDir, "common")

   try {
    if (!steamappsDir.exists() && !steamappsDir.mkdirs()) {
     AppLogger.e(TAG, "Failed to create steamapps directory")
     return@withContext Result.failure(
      Exception("Failed to create steamapps directory")
     )
    }

    if (!commonDir.exists() && !commonDir.mkdirs()) {
     AppLogger.e(TAG, "Failed to create common directory")
     return@withContext Result.failure(
      Exception("Failed to create common directory")
     )
    }

    AppLogger.i(TAG, "Successfully created Steam directory structure manually")
    AppLogger.d(TAG, "Created: ${steamappsDir.absolutePath}")
    AppLogger.d(TAG, "Created: ${commonDir.absolutePath}")

    return@withContext Result.success(Unit)

   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to create Steam directories", e)
    return@withContext Result.failure(e)
   }

  } catch (e: Exception) {
   AppLogger.e(TAG, "Steam initialization failed", e)
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
