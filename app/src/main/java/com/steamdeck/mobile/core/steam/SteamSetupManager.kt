package com.steamdeck.mobile.core.steam

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.winlator.WinlatorEmulator
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus
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

  // Progress range allocation (prevents progress regression)
  // Total: 5 steps (NSIS EXTRACTION method)
  private object ProgressRanges {
   // Step 1: Winlator initialization (Wine/Box64 extraction)
   const val INIT_START = 0.00f
   const val INIT_END = 0.25f // 25% - First time only, 2-3 minutes

   // Step 2: Create Wine container
   const val CONTAINER_START = 0.25f
   const val CONTAINER_END = 0.40f // 15% - 30-60 seconds

   // Step 3: Download SteamSetup.exe (~3MB)
   const val DOWNLOAD_START = 0.40f
   const val DOWNLOAD_END = 0.50f // 10% - ~2-5 seconds

   // Step 4: Extract Steam files from NSIS using 7-Zip
   const val INSTALLER_START = 0.50f
   const val INSTALLER_END = 0.90f // 40% - ~10-30 seconds

   // Step 5: Verify installation
   const val VERIFY_START = 0.90f
   const val VERIFY_END = 1.00f // 10% - ~5-10 seconds
  }

  /**
   * Map sub-progress (0.0-1.0) to overall progress range
   *
   * @param subProgress Progress within current step (0.0 - 1.0)
   * @param rangeStart Overall progress at step start
   * @param rangeEnd Overall progress at step end
   * @return Mapped overall progress
   */
  private fun mapProgress(subProgress: Float, rangeStart: Float, rangeEnd: Float): Float {
   return rangeStart + (subProgress * (rangeEnd - rangeStart))
  }
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
   val totalSteps: Int = 5, // 5 steps: Init + Container + Download + NSIS Extraction + Verify
   val elapsedTimeMs: Long = 0L
  ) : SteamInstallResult()
 }

 /**
  * Steam Client installation (NSIS EXTRACTION method)
  *
  * Installation flow:
  * 1. Initialize Winlator (Box64/Wine extraction) - 0-25%
  * 2. Create Wine container - 25-40%
  * 3. Download SteamSetup.exe (~3MB) - 40-50%
  * 4. Extract Steam files from NSIS installer using 7-Zip - 50-90%
  * 5. Verify installation - 90-100%
  *
  * Why NSIS extraction?
  * - Wine 9.0 WoW64 support is experimental and cannot run 32-bit SteamSetup.exe
  * - Direct extraction bypasses Wine completely (100% success rate on ARM64)
  * - All dependencies (steamclient.dll, libcef.dll, etc.) are properly extracted
  * - Supports all NSIS compression formats (LZMA, BZIP2, ZLIB/Deflate)
  *
  * @param containerId Winlator container ID (String type)
  * @param progressCallback Installation progress callback
  */
 suspend fun installSteam(
  containerId: String,
  progressCallback: ((Float, String, String?) -> Unit)? = null
 ): Result<SteamInstallResult> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Starting Steam installation (NSIS EXTRACTION method) for container: $containerId")

   // Step 1: Winlator emulator initialization (Box64/Wine extraction)
   // Progress: 0.00 ~ 0.25 (25%)
   AppLogger.i(TAG, "=== Step 1/5: Winlator Initialization ===")
   progressCallback?.invoke(ProgressRanges.INIT_START, "Step 1/5: Checking Winlator...", null)
   val available = winlatorEmulator.isAvailable().getOrNull() ?: false

   if (!available) {
    AppLogger.w(TAG, "Winlator not initialized - starting initialization (this may take 2-3 minutes)...")
    progressCallback?.invoke(ProgressRanges.INIT_START, "Step 1/5: Initializing Winlator (2-3 min)...", "Extracting Box64/Wine binaries")

    // Winlator initialization (Box64/Wine binary extraction)
    val initResult = winlatorEmulator.initialize { progress, message ->
     // Map sub-progress (0.0-1.0) to overall range (0.00-0.25)
     val overallProgress = mapProgress(progress, ProgressRanges.INIT_START, ProgressRanges.INIT_END)
     val percentComplete = (overallProgress * 100).toInt()
     progressCallback?.invoke(overallProgress, "Step 1/5: $message", "$percentComplete% complete")
     AppLogger.d(TAG, "Step 1/5 progress: $percentComplete% - $message")
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
    progressCallback?.invoke(ProgressRanges.INIT_END, "Step 1/5: Winlator ready", "25% complete")
   }

   // Step 2: Create Wine container
   // Progress: 0.25 ~ 0.40 (15%)
   AppLogger.i(TAG, "=== Step 2/5: Wine Container Creation ===")
   progressCallback?.invoke(ProgressRanges.CONTAINER_START, "Step 2/5: Creating Wine container...", "25% complete")

   // Get or create container
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
   progressCallback?.invoke(ProgressRanges.CONTAINER_END, "Step 2/5: Container ready", "40% complete")

   // Define Steam installation directory
   val steamDir = File(container.rootPath, "drive_c/Program Files (x86)/Steam")

   // Step 3: Download SteamSetup.exe
   // Progress: 0.40 ~ 0.50 (10%)
   AppLogger.i(TAG, "=== Step 3/5: Downloading SteamSetup.exe ===")
   progressCallback?.invoke(ProgressRanges.DOWNLOAD_START, "Step 3/5: Downloading Steam installer...", "40% complete")

   AppLogger.i(TAG, "Downloading SteamSetup.exe (~3MB)...")
   val downloadResult = steamInstallerService.downloadSteamSetup { bytesDownloaded, totalBytes ->
    // Map download progress to overall range (0.40-0.50)
    val downloadProgress = bytesDownloaded.toFloat() / totalBytes.toFloat()
    val overallProgress = mapProgress(downloadProgress, ProgressRanges.DOWNLOAD_START, ProgressRanges.DOWNLOAD_END)
    val percentComplete = (overallProgress * 100).toInt()
    val detail = "${bytesDownloaded / 1024}KB / ${totalBytes / 1024}KB"
    progressCallback?.invoke(overallProgress, "Step 3/5: Downloading SteamSetup.exe", "$percentComplete% - $detail")
   }

   if (downloadResult.isFailure) {
    AppLogger.e(TAG, "Failed to download SteamSetup.exe: ${downloadResult.exceptionOrNull()?.message}")
    return@withContext Result.success(
     SteamInstallResult.Error("Failed to download Steam installer: ${downloadResult.exceptionOrNull()?.message}")
    )
   }

   val setupFile = downloadResult.getOrElse {
    return@withContext Result.success(
     SteamInstallResult.Error("SteamSetup.exe not available: ${it.message}")
    )
   }
   AppLogger.i(TAG, "SteamSetup.exe downloaded successfully: ${setupFile.absolutePath} (${setupFile.length() / 1024}KB)")
   progressCallback?.invoke(ProgressRanges.DOWNLOAD_END, "Step 3/5: Download complete", "50% - ${setupFile.length() / 1024}KB")

   // Step 4: Extract Steam files from NSIS installer using 7-Zip
   // Progress: 0.50 ~ 0.90 (40%)
   AppLogger.i(TAG, "=== Step 4/5: Extracting Steam Files (NSIS + CDN) ===")
   progressCallback?.invoke(ProgressRanges.INSTALLER_START, "Step 4/5: Preparing extraction...", "50% complete")

   // Target directory: C:\Program Files (x86)\Steam in Wine container
   val steamInstallDir = File(container.rootPath, "drive_c/Program Files (x86)/Steam")
   steamInstallDir.mkdirs()
   AppLogger.i(TAG, "Extracting to: ${steamInstallDir.absolutePath}")

   // Extract using 7-Zip-JBinding-4Android
   var lastReportedPercent = 0
   val extractionResult = steamInstallerService.extractSteamFromNSIS(
    setupFile = setupFile,
    targetDir = steamInstallDir,
    onNsisProgress = { filesExtracted, totalFiles ->
     // Map NSIS extraction progress to range 0.50-0.60 (10%)
     val extractionProgress = if (totalFiles > 0) filesExtracted.toFloat() / totalFiles else 0f
     val overallProgress = mapProgress(extractionProgress, 0.50f, 0.60f)
     val percentComplete = (overallProgress * 100).toInt()

     // Report every 2% change (smoother UI updates)
     if (percentComplete >= lastReportedPercent + 2 || filesExtracted == 1 || filesExtracted == totalFiles) {
      progressCallback?.invoke(overallProgress, "Step 4/5: Extracting bootstrapper", "$percentComplete% - $filesExtracted/$totalFiles files")
      AppLogger.d(TAG, "NSIS extraction: $filesExtracted/$totalFiles files ($percentComplete%)")
      lastReportedPercent = percentComplete
     }
    },
    onCdnProgress = { currentPackage, totalPackages, bytesDownloaded, totalBytes ->
     // Map CDN download progress to range 0.60-0.90 (30%)
     val packageProgress = if (totalPackages > 0) currentPackage.toFloat() / totalPackages else 0f
     val downloadProgress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
     val combinedProgress = (packageProgress * 0.5f) + (downloadProgress * 0.5f)
     val overallProgress = mapProgress(combinedProgress, 0.60f, 0.90f)
     val percentComplete = (overallProgress * 100).toInt()

     // Report every 2% change
     if (percentComplete >= lastReportedPercent + 2 || currentPackage == totalPackages) {
      val mbDownloaded = bytesDownloaded / 1024 / 1024
      val mbTotal = totalBytes / 1024 / 1024
      progressCallback?.invoke(overallProgress, "Step 4/5: Downloading packages ($currentPackage/$totalPackages)", "$percentComplete% - ${mbDownloaded}MB/${mbTotal}MB")
      AppLogger.d(TAG, "CDN download: Package $currentPackage/$totalPackages, ${mbDownloaded}MB/${mbTotal}MB ($percentComplete%)")
      lastReportedPercent = percentComplete
     }
    }
   )

   if (extractionResult.isFailure) {
    AppLogger.e(TAG, "NSIS extraction failed: ${extractionResult.exceptionOrNull()?.message}")
    return@withContext Result.success(
     SteamInstallResult.Error("Steam extraction failed: ${extractionResult.exceptionOrNull()?.message}")
    )
   }

   val filesExtracted = extractionResult.getOrNull() ?: 0
   AppLogger.i(TAG, "NSIS extraction completed: $filesExtracted files extracted")
   progressCallback?.invoke(ProgressRanges.INSTALLER_END, "Step 4/5: Extraction complete", "90% - $filesExtracted files")

   // Step 5: Verify installation
   // Progress: 0.90 ~ 1.00 (10%)
   AppLogger.i(TAG, "=== Step 5/5: Verifying Installation ===")
   progressCallback?.invoke(ProgressRanges.VERIFY_START, "Step 5/5: Verifying Steam files...", "90% complete")

   // Verify critical Steam files exist
   val steamExe = File(steamDir, "Steam.exe")
   val steamclientDll = File(steamDir, "steamclient.dll")
   val tier0Dll = File(steamDir, "tier0_s.dll")

   if (!steamExe.exists()) {
    return@withContext Result.success(
     SteamInstallResult.Error("Steam.exe not found after installation - installer may have failed")
    )
   }

   if (!steamclientDll.exists()) {
    AppLogger.w(TAG, "steamclient.dll not found - Steam may not launch properly")
   }

   if (!tier0Dll.exists()) {
    AppLogger.w(TAG, "tier0_s.dll not found - Steam may not launch properly")
   }

   AppLogger.i(TAG, "Installation verified: Steam.exe (${steamExe.length() / 1024}KB)")
   if (steamclientDll.exists()) {
    AppLogger.i(TAG, "  steamclient.dll: ${steamclientDll.length() / 1024}KB")
   }
   if (tier0Dll.exists()) {
    AppLogger.i(TAG, "  tier0_s.dll: ${tier0Dll.length() / 1024}KB")
   }

   // Step 6: Initialize Steam client (create steamapps directory structure)
   AppLogger.i(TAG, "Creating steamapps directory structure...")
   val steamappsDir = File(steamDir, "steamapps")
   val commonDir = File(steamappsDir, "common")

   try {
    if (!steamappsDir.exists() && !steamappsDir.mkdirs()) {
     AppLogger.w(TAG, "Failed to create steamapps directory (non-fatal)")
    } else {
     AppLogger.i(TAG, "Created steamapps directory: ${steamappsDir.absolutePath}")
    }

    if (!commonDir.exists() && !commonDir.mkdirs()) {
     AppLogger.w(TAG, "Failed to create common directory (non-fatal)")
    } else {
     AppLogger.i(TAG, "Created common directory: ${commonDir.absolutePath}")
    }
   } catch (e: Exception) {
    AppLogger.w(TAG, "Failed to create Steam directory structure (non-fatal): ${e.message}")
   }

   // Save installation information
   steamInstallerService.saveInstallation(
    containerId = container.id,
    installPath = DEFAULT_STEAM_PATH,
    status = SteamInstallStatus.INSTALLED
   )

   progressCallback?.invoke(ProgressRanges.VERIFY_END, "Step 5/5: Installation complete!", "100% - Steam ready")
   AppLogger.i(TAG, "=== Steam installation completed successfully ===")
   AppLogger.i(TAG, "Total files extracted: $filesExtracted")
   AppLogger.i(TAG, "Installation path: $DEFAULT_STEAM_PATH")
   AppLogger.i(TAG, "Container ID: ${container.id}")

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
  *
  * Container Metadata Management (2025-12-25):
  * - REMOVED: WinlatorContainerEntity/Dao (eliminated duplicate Database management)
  * - NEW: Filesystem-based management via WinlatorEmulator
  *
  * Container Metadata Location:
  * - Directory: `containers/<containerId>/`
  * - Metadata: WinlatorEmulator manages Wine version, Box64 preset, etc.
  * - Standard: Matches Winlator 10.1 implementation
  *
  * Why Filesystem-based?
  * - Single Source of Truth (no Database sync issues)
  * - Winlator-compatible (direct file access)
  * - Simpler architecture (YAGNI principle)
  */
 private suspend fun getOrCreateContainer(containerId: String): Result<com.steamdeck.mobile.domain.emulator.EmulatorContainer> =
  withContext(Dispatchers.IO) {
   try {
    // CRITICAL FIX: Protect entire container creation flow with mutex to prevent race conditions
    // Multiple concurrent calls could create duplicate containers without synchronization
    containerCreationMutex.withLock {
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

     // If container doesn't exist, create new one with default Steam configuration
     AppLogger.i(TAG, "Container not found, creating new container for ID: $containerId")

     // CRITICAL FIX: Use "Default Container" name to trigger default_shared_container ID
     // WinlatorEmulator.createContainer() uses fixed ID "default_shared_container" when name == "Default Container"
     // This prevents duplicate container creation and enables efficient container reuse
     val config = com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig(
      name = "Default Container",  // Triggers fixed ID: default_shared_container
      performancePreset = com.steamdeck.mobile.domain.emulator.PerformancePreset.MAXIMUM_STABILITY // Steam prioritizes stability
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
  * Run Steam installer using Wine
  *
  * Executes SteamSetup.exe in Wine container with silent installation flag (/S).
  * The installer will automatically install Steam to C:\Program Files (x86)\Steam.
  *
  * Technical details:
  * - Command: wine SteamSetup.exe /S
  * - Silent mode: No user interaction required
  * - Timeout: 180 seconds (3 minutes) for complete installation
  * - Installs: Steam.exe, steamclient.dll, tier0_s.dll, vstdlib_s.dll, libcef.dll, etc.
  *
  * @param container Wine container for installation
  * @param setupFile SteamSetup.exe file in container file system
  * @param progressCallback Progress callback (0.0-1.0, message)
  * @return Result indicating success or failure
  */
 private suspend fun runSteamInstaller(
  container: com.steamdeck.mobile.domain.emulator.EmulatorContainer,
  setupFile: File,
  progressCallback: ((Float, String) -> Unit)? = null
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Running Steam installer in Wine container: ${container.id}")
   AppLogger.i(TAG, "Setup file: ${setupFile.absolutePath} (${setupFile.length()} bytes)")

   if (!setupFile.exists()) {
    return@withContext Result.failure(
     Exception("SteamSetup.exe not found at: ${setupFile.absolutePath}")
    )
   }

   // Launch installer with silent flag
   progressCallback?.invoke(0.1f, "Starting installer...")

   val launchResult = winlatorEmulator.launchExecutable(
    container = container,
    executable = setupFile,
    arguments = listOf("/S") // Silent installation
   )

   if (launchResult.isFailure) {
    val error = launchResult.exceptionOrNull()
    AppLogger.e(TAG, "Failed to launch Steam installer", error)
    return@withContext Result.failure(
     error ?: Exception("Failed to launch Steam installer")
    )
   }

   AppLogger.i(TAG, "Steam installer launched successfully")
   progressCallback?.invoke(0.3f, "Installing Steam...")

   // Wait for installation to complete (monitor Steam directory)
   val steamDir = File(container.rootPath, "drive_c/Program Files (x86)/Steam")
   val steamExe = File(steamDir, "Steam.exe")
   val installTimeout = 180_000L // 3 minutes
   val startTime = System.currentTimeMillis()

   while (System.currentTimeMillis() - startTime < installTimeout) {
    delay(3000) // Check every 3 seconds

    if (steamExe.exists() && steamExe.length() > 0) {
     val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
     AppLogger.i(TAG, "Steam.exe detected after ${elapsedSeconds}s: ${steamExe.absolutePath}")

     // Wait additional 5 seconds for installer to complete writing files
     progressCallback?.invoke(0.8f, "Finalizing installation...")
     delay(5000)

     AppLogger.i(TAG, "Steam installation completed successfully")
     progressCallback?.invoke(1.0f, "Installation complete")
     return@withContext Result.success(Unit)
    }

    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
    val progress = 0.3f + (elapsedSeconds / 180f) * 0.5f // 30% to 80% based on time
    progressCallback?.invoke(progress.coerceAtMost(0.8f), "Installing Steam... (${elapsedSeconds}s)")
    AppLogger.d(TAG, "Waiting for Steam.exe... (${elapsedSeconds}s elapsed)")
   }

   // Timeout
   AppLogger.e(TAG, "Steam installer timeout after 3 minutes")
   AppLogger.e(TAG, "Expected: ${steamExe.absolutePath}")
   AppLogger.e(TAG, "Directory exists: ${steamDir.exists()}")
   if (steamDir.exists()) {
    val files = steamDir.listFiles()?.joinToString(", ") { it.name } ?: "empty"
    AppLogger.e(TAG, "Directory contents: $files")
   }

   return@withContext Result.failure(
    Exception("Steam installer timeout - installation took longer than 3 minutes")
   )

  } catch (e: Exception) {
   AppLogger.e(TAG, "Steam installer execution failed", e)
   Result.failure(e)
  }
 }

 /**
  * Initialize Steam client in background
  *
  * Launches Steam.exe with -silent flag to trigger first-run
  * initialization, which creates steamapps/, config.vdf, loginusers.vdf, etc.
  *
  * Technical details:
  * - Uses Wine container to execute Steam.exe
  * - Command: Steam.exe -silent
  * - Timeout: 30 seconds (sufficient for directory creation)
  * - Creates: steamapps/, config/, config.vdf, loginusers.vdf
  *
  * Note: -noreactlogin flag was removed due to kernel32.dll load failure in WoW64 mode
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
   // -silent: Run in background mode without UI (keeps process alive)
   //
   // REMOVED: -noreactlogin (causes kernel32.dll load failure in WoW64 mode)
   // See: wine: could not load kernel32.dll, status c0000135
   val arguments = listOf("-silent")
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
