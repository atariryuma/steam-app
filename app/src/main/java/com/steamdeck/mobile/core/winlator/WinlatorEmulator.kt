package com.steamdeck.mobile.core.winlator

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.core.util.ElfPatcher
import com.steamdeck.mobile.domain.emulator.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Winlator implementation of WindowsEmulator interface.
 *
 * Winlator architecture:
 * - Wine 9.0+ for Windows API translation
 * - Box64 0.3.6 for x86_64 → ARM64 binary translation
 * - DXVK 2.4.1 for DirectX → Vulkan
 * - Linux rootfs (chroot environment)
 *
 * @see WindowsEmulator
 */
@Singleton
class WinlatorEmulator @Inject constructor(
 @ApplicationContext private val context: Context,
 private val zstdDecompressor: ZstdDecompressor,
 private val processMonitor: ProcessMonitor
) : WindowsEmulator {

 override val name: String = "Winlator"
 override val version: String = "10.1.0"

 private val dataDir = File(context.filesDir, "winlator")
 private val box64Dir = File(dataDir, "box64")
 private val prootDir = File(dataDir, "proot")
 private val rootfsDir = File(dataDir, "rootfs")
 private val wineDir = File(rootfsDir, "opt/wine")
 private val containersDir = File(dataDir, "containers")

 // Binary paths (extracted from assets at runtime)
 private val prootBinary = File(prootDir, "proot")
 private val box64Binary = File(box64Dir, "box64")
 private val wineBinary = File(wineDir, "bin/wine")
 private val winebootBinary = File(wineDir, "bin/wineboot")
 private val wineserverBinary = File(wineDir, "bin/wineserver")

 // Active processes map (processId -> ProcessInfo)
 private val activeProcesses = mutableMapOf<String, ProcessInfo>()

 private data class ProcessInfo(
  val process: Process,
  val startTime: Long
 )

 companion object {
  private const val TAG = "WinlatorEmulator"

  // Box64 assets
  private const val BOX64_ASSET = "winlator/box64-0.3.4.txz" // Downgraded from v0.3.6 for stability
  private const val BOX64_RC_ASSET = "winlator/default.box64rc"
  private const val ENV_VARS_ASSET = "winlator/env_vars.json"

  // Rootfs assets (contains Wine 9.0+)
  private const val ROOTFS_ASSET = "winlator/rootfs.txz"

  // PRoot asset for SELinux compatibility
  private const val PROOT_ASSET = "winlator/proot-v5.3.0-aarch64.txz"
 }

 override suspend fun isAvailable(): Result<Boolean> = withContext(Dispatchers.IO) {
  try {
   // Check if binaries exist (extracted from assets during initialization)
   val binariesExist = prootBinary.exists() &&
        box64Binary.exists() &&
        wineBinary.exists() &&
        winebootBinary.exists() &&
        wineserverBinary.exists()

   // Check if rootfs is extracted (Wine support files)
   val rootfsExtracted = File(wineDir, "bin").exists()

   val isAvailable = binariesExist && rootfsExtracted

   Log.d(TAG, "Winlator availability: binaries=$binariesExist, rootfs=$rootfsExtracted")
   Result.success(isAvailable)
  } catch (e: Exception) {
   Log.e(TAG, "Error checking availability", e)
   Result.failure(EmulatorException("Failed to check Winlator availability", e))
  }
 }

 override suspend fun initialize(
  progressCallback: ((Float, String) -> Unit)?
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   Log.i(TAG, "Initializing Winlator emulator...")
   progressCallback?.invoke(0.0f, "Initializing Winlator...")

   // Create directories
   dataDir.mkdirs()
   box64Dir.mkdirs()
   prootDir.mkdirs()
   rootfsDir.mkdirs()
   containersDir.mkdirs()

   // Step 1: Extract PRoot binary (0.0 - 0.15)
   if (!prootBinary.exists()) {
    progressCallback?.invoke(0.0f, "Extracting PRoot binary...")

    // Extract proot.txz from assets
    val prootTxzFile = File(prootDir, "proot-v5.3.0-aarch64.txz")
    if (!prootTxzFile.exists()) {
     extractAsset(PROOT_ASSET, prootTxzFile)
     progressCallback?.invoke(0.025f, "PRoot asset copied")
    }

    if (prootTxzFile.exists()) {
     // Extract .txz to prootDir
     val extractResult = zstdDecompressor.extractTxz(
      txzFile = prootTxzFile,
      targetDir = prootDir
     ) { extractProgress, status ->
      progressCallback?.invoke(0.05f + extractProgress * 0.1f, status)
     }

     if (extractResult.isFailure) {
      Log.e(TAG, "Failed to extract PRoot", extractResult.exceptionOrNull())
      return@withContext Result.failure(
       EmulatorException("Failed to extract PRoot: ${extractResult.exceptionOrNull()?.message}")
      )
     }

     // PRoot binary is extracted, need to find and move it
     val extractedProot = File(prootDir, "proot")
     if (!extractedProot.exists()) {
      // Try alternate path (might be in subdir)
      val altProot = File(prootDir, "usr/local/bin/proot")
      if (altProot.exists()) {
       altProot.copyTo(extractedProot, overwrite = true)
       File(prootDir, "usr").deleteRecursively()
      }
     }

     // Verify binary exists
     if (!prootBinary.exists()) {
      return@withContext Result.failure(
       EmulatorException("PRoot binary not found after extraction: ${prootBinary.absolutePath}")
      )
     }

     // Make sure it's executable
     prootBinary.setExecutable(true, false)
     Log.i(TAG, "PRoot extracted and set executable: ${prootBinary.absolutePath}")

     // Patch PRoot to PIE format (ET_DYN) for Android 5.0+ compatibility
     val patchResult = ElfPatcher.patchToPie(prootBinary)
     if (patchResult.isFailure) {
      Log.w(TAG, "Failed to patch PRoot to PIE: ${patchResult.exceptionOrNull()?.message}")
      // Continue anyway - binary might already be PIE
     }

     // Patch TLS alignment for Android 12+ (requires 64-byte alignment)
     val tlsPatchResult = ElfPatcher.patchTlsAlignment(prootBinary, 64)
     if (tlsPatchResult.isFailure) {
      Log.w(TAG, "Failed to patch PRoot TLS alignment: ${tlsPatchResult.exceptionOrNull()?.message}")
      // Continue anyway - might already be aligned
     }

     progressCallback?.invoke(0.15f, "PRoot ready")
    } else {
     Log.e(TAG, "PRoot .txz file not found: ${prootTxzFile.absolutePath}")
     return@withContext Result.failure(
      EmulatorException("PRoot asset file not found")
     )
    }
   } else {
    progressCallback?.invoke(0.15f, "PRoot already extracted")
   }

   // Step 2: Extract Box64 binary (0.15 - 0.3)
   if (!box64Binary.exists()) {
    progressCallback?.invoke(0.15f, "Extracting Box64 binary...")

    // Extract box64.txz from assets
    val box64TxzFile = File(box64Dir, "box64-0.3.4.txz")
    if (!box64TxzFile.exists()) {
     extractAsset(BOX64_ASSET, box64TxzFile)
     progressCallback?.invoke(0.17f, "Box64 asset copied")
    }

    if (box64TxzFile.exists()) {
     // Extract .txz to box64Dir
     val extractResult = zstdDecompressor.extractTxz(
      txzFile = box64TxzFile,
      targetDir = box64Dir
     ) { extractProgress, status ->
      progressCallback?.invoke(0.18f + extractProgress * 0.12f, status)
     }

     if (extractResult.isFailure) {
      Log.e(TAG, "Failed to extract Box64", extractResult.exceptionOrNull())
      return@withContext Result.failure(
       Box64BinaryNotFoundException(
        "Failed to extract Box64: ${extractResult.exceptionOrNull()?.message}"
       )
      )
     }

     // Box64 binary is extracted to usr/local/bin/box64, need to move it
     val extractedBox64 = File(box64Dir, "usr/local/bin/box64")
     if (extractedBox64.exists() && !box64Binary.exists()) {
      // Move binary to expected location
      extractedBox64.copyTo(box64Binary, overwrite = true)
      box64Binary.setExecutable(true, false)
      Log.i(TAG, "Box64 binary moved from ${extractedBox64.absolutePath} to ${box64Binary.absolutePath}")

      // Clean up extracted directory structure
      File(box64Dir, "usr").deleteRecursively()
     }

     // Verify binary exists
     if (!box64Binary.exists()) {
      return@withContext Result.failure(
       Box64BinaryNotFoundException(
        "Box64 binary not found after extraction: ${box64Binary.absolutePath}"
       )
      )
     }

     // Make sure it's executable
     box64Binary.setExecutable(true, false)
     Log.i(TAG, "Box64 extracted and set executable: ${box64Binary.absolutePath}")

     // Patch Box64 to PIE format (ET_DYN) for Android 5.0+ compatibility
     val patchResult = ElfPatcher.patchToPie(box64Binary)
     if (patchResult.isFailure) {
      Log.w(TAG, "Failed to patch Box64 to PIE: ${patchResult.exceptionOrNull()?.message}")
      // Continue anyway - binary might already be PIE
     }

     // Patch TLS alignment for Android 12+ (requires 64-byte alignment)
     val tlsPatchResult = ElfPatcher.patchTlsAlignment(box64Binary, 64)
     if (tlsPatchResult.isFailure) {
      Log.w(TAG, "Failed to patch Box64 TLS alignment: ${tlsPatchResult.exceptionOrNull()?.message}")
      // Continue anyway - might not have PT_TLS segment
     }

     progressCallback?.invoke(0.3f, "Box64 ready")
    } else {
     Log.e(TAG, "Box64 .txz file not found: ${box64TxzFile.absolutePath}")
     return@withContext Result.failure(
      Box64BinaryNotFoundException("Box64 asset file not found")
     )
    }
   } else {
    progressCallback?.invoke(0.3f, "Box64 already extracted")
   }

   // Ensure Box64 patches are always applied (even if already extracted)
   if (box64Binary.exists()) {
    // Verify and patch PIE if needed
    val patchPieResult = ElfPatcher.patchToPie(box64Binary)
    if (patchPieResult.isFailure) {
     Log.w(TAG, "Box64 PIE patch check failed: ${patchPieResult.exceptionOrNull()?.message}")
    }

    // Verify and patch TLS if needed
    val patchTlsResult = ElfPatcher.patchTlsAlignment(box64Binary, 64)
    if (patchTlsResult.isFailure) {
     Log.w(TAG, "Box64 TLS patch check failed: ${patchTlsResult.exceptionOrNull()?.message}")
    }
   }

   // Step 2: Extract configuration files (0.3 - 0.4)
   progressCallback?.invoke(0.3f, "Extracting configuration files...")

   val box64RcFile = File(box64Dir, "default.box64rc")
   if (!box64RcFile.exists()) {
    extractAsset(BOX64_RC_ASSET, box64RcFile)
   }

   val envVarsFile = File(box64Dir, "env_vars.json")
   if (!envVarsFile.exists()) {
    extractAsset(ENV_VARS_ASSET, envVarsFile)
   }

   progressCallback?.invoke(0.4f, "Configuration files ready")

   // Step 3: Extract Rootfs/Wine support files (0.4 - 1.0)
   if (!File(wineDir, "bin").exists()) {
    progressCallback?.invoke(0.4f, "Extracting Wine rootfs (53MB)...")

    // Extract rootfs.txz from assets
    val rootfsTxzFile = File(dataDir, "rootfs.txz")
    if (!rootfsTxzFile.exists()) {
     extractAsset(ROOTFS_ASSET, rootfsTxzFile)
    }

    progressCallback?.invoke(0.4f, "Decompressing Wine rootfs (this may take 2-3 minutes)...")

    // Extract .txz archive
    if (rootfsTxzFile.exists()) {
     zstdDecompressor.extractTxz(
      txzFile = rootfsTxzFile,
      targetDir = rootfsDir
     ) { extractProgress, status ->
      // 0.4 to 1.0 = 60% of total progress
      progressCallback?.invoke(0.4f + extractProgress * 0.6f, status)
     }.onSuccess {
      Log.i(TAG, "Rootfs extraction successful")

      // Verify Wine binary exists
      if (wineBinary.exists()) {
       wineBinary.setExecutable(true, false)
       Log.i(TAG, "Wine binary ready: ${wineBinary.absolutePath}")

       // Set wineserver executable
       val wineserver = File(wineDir, "bin/wineserver")
       if (wineserver.exists()) {
        wineserver.setExecutable(true, false)
       }
      } else {
       Log.w(TAG, "Wine binary not found after extraction at: ${wineBinary.absolutePath}")
      }

      // Delete temporary .txz file to save space
      if (rootfsTxzFile.exists()) {
       rootfsTxzFile.delete()
       Log.d(TAG, "Cleaned up temporary rootfs.txz file")
      }

      // Setup hardcoded linker path now that rootfs is extracted
      setupHardcodedLinkerPath()
     }.onFailure { error ->
      Log.e(TAG, "Rootfs extraction failed: ${error.message}", error)
      return@withContext Result.failure(
       EmulatorException("Failed to extract Wine rootfs: ${error.message}", error)
      )
     }
    }
   } else {
    Log.i(TAG, "Wine already extracted, skipping")
    // Ensure linker is setup even if rootfs was already extracted
    setupHardcodedLinkerPath()
    progressCallback?.invoke(1.0f, "Wine already ready")
   }

   progressCallback?.invoke(1.0f, "Initialization complete")

   Log.i(TAG, "Winlator initialization complete")
   Result.success(Unit)
  } catch (e: Exception) {
   Log.e(TAG, "Initialization failed", e)
   Result.failure(EmulatorException("Failed to initialize Winlator: ${e.message}", e))
  }
 }

 override suspend fun createContainer(
  config: EmulatorContainerConfig
 ): Result<EmulatorContainer> = withContext(Dispatchers.IO) {
  try {
   val containerId = "${System.currentTimeMillis()}"
   val containerDir = File(containersDir, containerId)

   // Create container directory structure
   val driveC = File(containerDir, "drive_c")
   File(driveC, "windows").mkdirs()
   File(driveC, "Program Files").mkdirs()
   File(driveC, "Program Files (x86)").mkdirs()
   File(driveC, "users/Public").mkdirs()

   // Initialize Wine prefix with wineboot
   val initResult = initializeWinePrefix(containerDir)
   if (initResult.isFailure) {
    Log.e(TAG, "Wine prefix initialization failed: ${initResult.exceptionOrNull()?.message}")
    containerDir.deleteRecursively()
    return@withContext Result.failure(
     initResult.exceptionOrNull()
      ?: EmulatorException("Wine prefix initialization failed")
    )
   }

   val container = EmulatorContainer(
    id = containerId,
    name = config.name,
    config = config,
    rootPath = containerDir,
    createdAt = System.currentTimeMillis(),
    lastUsedAt = System.currentTimeMillis(),
    sizeBytes = calculateDirectorySize(containerDir)
   )

   Log.i(TAG, "Created container: ${container.name} (${container.id})")
   Result.success(container)
  } catch (e: Exception) {
   Log.e(TAG, "Failed to create container", e)
   Result.failure(EmulatorException("Failed to create container: ${e.message}", e))
  }
 }

 override suspend fun listContainers(): Result<List<EmulatorContainer>> = withContext(Dispatchers.IO) {
  try {
   if (!containersDir.exists()) {
    return@withContext Result.success(emptyList())
   }

   val containers = containersDir.listFiles()?.mapNotNull { dir ->
    if (!dir.isDirectory) return@mapNotNull null

    try {
     EmulatorContainer(
      id = dir.name,
      name = dir.name, // TODO: Load from metadata file
      config = EmulatorContainerConfig(name = dir.name), // TODO: Load actual config
      rootPath = dir,
      createdAt = dir.lastModified(),
      lastUsedAt = dir.lastModified(),
      sizeBytes = calculateDirectorySize(dir)
     )
    } catch (e: Exception) {
     Log.w(TAG, "Failed to load container ${dir.name}", e)
     null
    }
   } ?: emptyList()

   Result.success(containers)
  } catch (e: Exception) {
   Log.e(TAG, "Failed to list containers", e)
   Result.failure(EmulatorException("Failed to list containers", e))
  }
 }

 override suspend fun deleteContainer(containerId: String): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   val containerDir = File(containersDir, containerId)
   if (!containerDir.exists()) {
    return@withContext Result.failure(
     ContainerNotFoundException(containerId)
    )
   }

   containerDir.deleteRecursively()
   Log.i(TAG, "Deleted container: $containerId")
   Result.success(Unit)
  } catch (e: Exception) {
   Log.e(TAG, "Failed to delete container", e)
   Result.failure(EmulatorException("Failed to delete container", e))
  }
 }

 override suspend fun launchExecutable(
  container: EmulatorContainer,
  executable: File,
  arguments: List<String>
 ): Result<EmulatorProcess> = withContext(Dispatchers.IO) {
  try {
   Log.i(TAG, "Launching executable: ${executable.absolutePath}")
   Log.d(TAG, "Container: ${container.name}, Arguments: $arguments")

   // 1. Verify executable exists
   if (!executable.exists()) {
    return@withContext Result.failure(
     EmulatorException("Executable not found: ${executable.absolutePath}")
    )
   }

   // 2. Get Box64 binary
   val box64ToUse = getBox64Binary()

   if (!box64ToUse.exists()) {
    return@withContext Result.failure(
     Box64BinaryNotFoundException()
    )
   }

   if (!wineBinary.exists()) {
    return@withContext Result.failure(
     WineBinaryNotFoundException()
    )
   }

   // Get the rootfs linker to use as interpreter
   val linker = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")
   if (!linker.exists()) {
    return@withContext Result.failure(
     EmulatorException("Rootfs linker not found: ${linker.absolutePath}")
    )
   }

   // 3. Build environment variables
   val environmentVars = buildEnvironmentVariables(container)

   // 4. Build command - invoke linker directly with Box64 as argument
   val command = buildList {
    add(linker.absolutePath)
    add(box64ToUse.absolutePath)
    add(wineBinary.absolutePath)
    add(executable.absolutePath)
    addAll(arguments)
   }

   Log.d(TAG, "Command: ${command.joinToString(" ")}")
   Log.d(TAG, "Environment: $environmentVars")

   // 5. Start process
   val processBuilder = ProcessBuilder(command)
   // Clear environment and set only what we need
   val env = processBuilder.environment()
   env.clear()
   env.putAll(environmentVars)
   processBuilder.redirectErrorStream(true)

   // Set working directory to executable's parent
   executable.parentFile?.let { workingDir ->
    if (workingDir.exists()) {
     processBuilder.directory(workingDir)
     Log.d(TAG, "Working directory: ${workingDir.absolutePath}")
    }
   }

   val process = processBuilder.start()
   val pid = getPid(process)
   val processId = "${System.currentTimeMillis()}_$pid"

   Log.i(TAG, "Process launched: PID=$pid, ProcessId=$processId")

   // 6. Monitor output in background (optional, for debugging)
   CoroutineScope(Dispatchers.IO).launch {
    try {
     process.inputStream.bufferedReader().use { reader ->
      reader.lineSequence().forEach { line ->
       Log.d(TAG, "[Wine:$pid] $line")
      }
     }
    } catch (e: Exception) {
     Log.w(TAG, "Output stream monitoring failed", e)
    }
   }

   val emulatorProcess = EmulatorProcess(
    id = processId,
    containerId = container.id,
    executable = executable.absolutePath,
    startedAt = System.currentTimeMillis(),
    pid = pid
   )

   // Store process reference for status checking
   activeProcesses[processId] = ProcessInfo(
    process = process,
    startTime = System.currentTimeMillis()
   )

   Result.success(emulatorProcess)
  } catch (e: Exception) {
   Log.e(TAG, "Failed to launch executable", e)
   Result.failure(ProcessLaunchException("Failed to launch executable: ${e.message}", e))
  }
 }

 override suspend fun installApplication(
  container: EmulatorContainer,
  installer: File,
  silent: Boolean
 ): Result<Unit> = withContext(Dispatchers.IO) {
  // TODO: Implement installer execution
  Result.failure(EmulatorException("installApplication not implemented"))
 }

 override suspend fun getProcessStatus(processId: String): Result<EmulatorProcessStatus> = withContext(Dispatchers.IO) {
  try {
   val processInfo = activeProcesses[processId]
    ?: return@withContext Result.failure(
     ProcessNotFoundException(processId)
    )

   val isRunning = processInfo.process.isAlive
   val exitCode = if (!isRunning) processInfo.process.exitValue() else null

   // Get real-time process metrics
   val pid = getPid(processInfo.process)
   val metrics = if (isRunning && pid > 0) {
    try {
     readProcessMetricsOnce(pid, processInfo.startTime)
    } catch (e: Exception) {
     Log.w(TAG, "Failed to read process metrics", e)
     null
    }
   } else {
    null
   }

   val status = EmulatorProcessStatus(
    processId = processId,
    isRunning = isRunning,
    exitCode = exitCode,
    cpuUsage = metrics?.cpuPercent ?: 0f,
    memoryUsageMB = metrics?.memoryMB?.toLong() ?: 0L,
    uptime = if (isRunning) System.currentTimeMillis() - processInfo.startTime else 0L
   )

   Result.success(status)
  } catch (e: Exception) {
   Log.e(TAG, "Failed to get process status", e)
   Result.failure(EmulatorException("Failed to get process status: ${e.message}", e))
  }
 }

 override fun monitorProcess(processId: String, intervalMs: Long): Flow<EmulatorProcessStatus> = flow {
  Log.i(TAG, "Starting process monitoring: $processId (interval: ${intervalMs}ms)")

  try {
   while (true) {
    val statusResult = getProcessStatus(processId)

    when {
     statusResult.isSuccess -> {
      val status = statusResult.getOrThrow()
      emit(status)

      // If process has terminated, complete the flow
      if (!status.isRunning) {
       Log.i(TAG, "Process terminated: $processId (exitCode: ${status.exitCode})")
       break
      }
     }
     else -> {
      // Process not found or error - assume terminated
      Log.w(TAG, "Process monitoring error: ${statusResult.exceptionOrNull()?.message}")
      break
     }
    }

    delay(intervalMs)
   }
  } catch (e: Exception) {
   Log.e(TAG, "Process monitoring failed: $processId", e)
   // Flow will complete naturally
  } finally {
   Log.d(TAG, "Process monitoring stopped: $processId")
  }
 }.flowOn(Dispatchers.IO)

 override suspend fun killProcess(processId: String, force: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   val processInfo = activeProcesses[processId]
    ?: return@withContext Result.failure(
     ProcessNotFoundException(processId)
    )

   Log.i(TAG, "Killing process: $processId (force=$force)")

   if (force) {
    // SIGKILL
    processInfo.process.destroyForcibly()
    Log.d(TAG, "Process forcibly destroyed: $processId")
   } else {
    // SIGTERM - graceful shutdown
    processInfo.process.destroy()

    // Wait up to 10 seconds for graceful shutdown
    val exited = kotlinx.coroutines.withTimeoutOrNull(10_000) {
     while (processInfo.process.isAlive) {
      kotlinx.coroutines.delay(100)
     }
     true
    }

    if (exited == null) {
     Log.w(TAG, "Process did not exit gracefully, force killing")
     processInfo.process.destroyForcibly()
    }
   }

   // Wait for exit
   processInfo.process.waitFor()

   // Remove from active processes
   activeProcesses.remove(processId)

   Log.i(TAG, "Process killed successfully: $processId")
   Result.success(Unit)
  } catch (e: Exception) {
   Log.e(TAG, "Failed to kill process", e)
   Result.failure(EmulatorException("Failed to kill process: ${e.message}", e))
  }
 }

 override fun getEmulatorInfo(): EmulatorInfo {
  return EmulatorInfo(
   name = name,
   version = version,
   backend = EmulatorBackend.WINLATOR,
   wineVersion = "9.0+",
   translationLayer = "Box64 0.3.6",
   graphicsBackend = "DXVK 2.4.1 / VKD3D 2.13",
   installPath = dataDir,
   capabilities = setOf(
    EmulatorCapability.DIRECT3D_9,
    EmulatorCapability.DIRECT3D_10,
    EmulatorCapability.DIRECT3D_11,
    EmulatorCapability.DIRECT3D_12,
    EmulatorCapability.VULKAN,
    EmulatorCapability.X86_64_TRANSLATION,
    EmulatorCapability.AUDIO_PLAYBACK,
    EmulatorCapability.GAMEPAD_SUPPORT,
    EmulatorCapability.KEYBOARD_MOUSE
   )
  )
 }

 override suspend fun cleanup(): Result<Long> = withContext(Dispatchers.IO) {
  try {
   var bytesFreed = 0L

   // Clean temporary files
   val tempDir = File(dataDir, "temp")
   if (tempDir.exists()) {
    bytesFreed += calculateDirectorySize(tempDir)
    tempDir.deleteRecursively()
   }

   // Clean cache
   val cacheDir = File(dataDir, "cache")
   if (cacheDir.exists()) {
    bytesFreed += calculateDirectorySize(cacheDir)
    cacheDir.deleteRecursively()
   }

   Log.i(TAG, "Cleanup freed ${bytesFreed / 1024 / 1024}MB")
   Result.success(bytesFreed)
  } catch (e: Exception) {
   Log.e(TAG, "Cleanup failed", e)
   Result.failure(EmulatorException("Cleanup failed", e))
  }
 }

 // Helper functions

 /**
  * Builds environment variables for wineboot initialization with retry-specific settings.
  *
  * Each retry attempt uses progressively more conservative Box64 and Wine settings
  * to maximize stability at the cost of performance.
  *
  * @param containerDir Container root directory for WINEPREFIX
  * @param attemptNumber Retry attempt number (0-2)
  * @return Map of environment variables optimized for the attempt
  */
 private fun buildWinebootEnvironmentVariables(
  containerDir: File,
  attemptNumber: Int = 0
 ): Map<String, String> {
  return buildMap {
   put("WINEPREFIX", containerDir.absolutePath)
   put("HOME", containerDir.absolutePath)
   put("TMPDIR", context.cacheDir.absolutePath)
   put("LANG", "C")
   put("LC_ALL", "C")

   // CRITICAL: Reduce WINEDEBUG verbosity based on attempt
   // Excessive logging (+all,+relay,+file) destabilizes Wine and causes SIGSEGV
   when (attemptNumber) {
    0 -> put("WINEDEBUG", "-all")    // Silent - fastest, most stable
    1 -> put("WINEDEBUG", "warn")    // Warnings only
    else -> put("WINEDEBUG", "+err,+fixme") // Errors + fixme - full diagnostics
   }

   put("WINEARCH", "win64")
   put("WINELOADERNOEXEC", "1")
   put("WINESERVER", File(wineDir, "bin/wineserver_wrapper").absolutePath)
   put("WINEFSYNC", "0") // Disable FSync during initialization for stability

   // Paths - DO NOT set LD_LIBRARY_PATH here, as it breaks Android shell
   // when running wineserver_wrapper. Box64 uses BOX64_LD_LIBRARY_PATH instead.
   put("PATH", "${wineDir.absolutePath}/bin:${box64Dir.absolutePath}")
   put("WINEDLLPATH", "${wineDir.absolutePath}/lib/wine/x86_64-windows")

   // Box64 library paths (critical for Android dlopen restrictions)
   put("BOX64_LD_LIBRARY_PATH", "${rootfsDir.absolutePath}/lib:${rootfsDir.absolutePath}/usr/lib:${rootfsDir.absolutePath}/usr/lib/x86_64-linux-gnu:${wineDir.absolutePath}/lib/wine/x86_64-unix")
   put("BOX64_PATH", "${wineDir.absolutePath}/bin")
   put("BOX64_EMULATED_LIBS", "libc.so.6:libpthread.so.0:libdl.so.2:librt.so.1:libm.so.6")
   put("BOX64_ALLOWMISSINGLIBS", "1")

   // Box64 dynarec settings per attempt - each attempt gets more conservative
   when (attemptNumber) {
    0 -> {
     // Attempt 1: MAXIMUM STABILITY preset (Winlator-based)
     // These settings prioritize correctness over performance
     put("BOX64_DYNAREC_BIGBLOCK", "0")  // Disable large block compilation
     put("BOX64_DYNAREC_STRONGMEM", "2")  // FIXED: Max valid value is 2, not 3!
     put("BOX64_DYNAREC_FASTNAN", "0")  // CRITICAL: Accurate NaN handling
     put("BOX64_DYNAREC_FASTROUND", "0")  // CRITICAL: Accurate rounding
     put("BOX64_DYNAREC_SAFEFLAGS", "2")  // CRITICAL: Full flag safety
     put("BOX64_DYNAREC_FORWARD", "128")  // ADDED: Conservative block forward (Winlator STABILITY)
     put("BOX64_DYNAREC_WAIT", "0")   // ADDED: Winlator STABILITY setting
     put("BOX64_DYNAREC_WEAKBARRIER", "0") // Strong memory barriers
     put("BOX64_DYNAREC_X87DOUBLE", "1")  // Accurate x87 FPU emulation
     put("BOX64_LOG", "1")     // Basic logging
     put("BOX64_NOBANNER", "0")    // Show banner for diagnostics
    }
    1 -> {
     // Attempt 2: ULTRA-CONSERVATIVE (disable optimizations)
     put("BOX64_DYNAREC_BIGBLOCK", "0")
     put("BOX64_DYNAREC_STRONGMEM", "2")  // FIXED: Max valid value is 2
     put("BOX64_DYNAREC_FASTNAN", "0")
     put("BOX64_DYNAREC_FASTROUND", "0")
     put("BOX64_DYNAREC_SAFEFLAGS", "2")
     put("BOX64_DYNAREC_WEAKBARRIER", "0")
     put("BOX64_DYNAREC_X87DOUBLE", "1")  // ADDED: Maintain FPU accuracy
     put("BOX64_DYNAREC_CALLRET", "0")  // Disable call/ret optimization
     put("BOX64_DYNAREC_FORWARD", "0")  // Disable forward optimization
     put("BOX64_DYNAREC_WAIT", "0")   // ADDED: Winlator setting
     put("BOX64_LOG", "2")     // More verbose logging
     put("BOX64_NOBANNER", "0")
    }
    else -> {
     // Attempt 3: DISABLE DYNAREC (slowest but most stable)
     // Falls back to pure interpretation - ~10x slower but eliminates JIT bugs
     put("BOX64_DYNAREC", "0")    // COMPLETE DISABLE
     put("BOX64_LOG", "3")     // Maximum logging
     put("BOX64_NOBANNER", "0")
    }
   }

   // Android compatibility
   put("MESA_EXTENSION_MAX_YEAR", "2003")
   put("MESA_GL_VERSION_OVERRIDE", "4.6")
   put("MESA_GLSL_VERSION_OVERRIDE", "460")
   put("DISPLAY", ":0")
  }
 }

 /**
  * Cleans up partial Wine prefix after failed initialization attempt.
  *
  * Removes potentially corrupted registry files and timestamps that could
  * interfere with subsequent retry attempts.
  *
  * @param containerDir Container root directory to clean
  */
 private fun cleanupPartialPrefix(containerDir: File) {
  try {
   // Remove potentially corrupted registry files
   val regFiles = listOf("system.reg", "user.reg", "userdef.reg")
   regFiles.forEach { regFile ->
    val file = File(containerDir, regFile)
    if (file.exists()) {
     val deleted = file.delete()
     if (deleted) {
      Log.d(TAG, "Cleaned up partial registry: $regFile")
     } else {
      Log.w(TAG, "Failed to delete partial registry: $regFile")
     }
    }
   }

   // Clean update timestamp
   File(containerDir, ".update-timestamp").apply {
    if (exists()) {
     val deleted = delete()
     if (deleted) {
      Log.d(TAG, "Cleaned up .update-timestamp")
     }
    }
   }
  } catch (e: Exception) {
   Log.w(TAG, "Failed to cleanup partial prefix", e)
   // Don't propagate - cleanup is best-effort
  }
 }

 /**
  * Initializes Wine prefix by running `wineboot --init` with 3-tier retry mechanism.
  *
  * Uses progressively more conservative Box64/Wine settings across 3 attempts:
  * - Attempt 1: Maximum stability (90s timeout)
  * - Attempt 2: Ultra-conservative with disabled optimizations (120s timeout)
  * - Attempt 3: Dynarec completely disabled - slowest but most stable (120s timeout)
  *
  * Between attempts, cleans up partial prefix corruption and uses exponential backoff.
  *
  * @param containerDir Container root directory
  * @return Result indicating success or failure after all attempts
  */
 private suspend fun initializeWinePrefix(containerDir: File): Result<Unit> = withContext(Dispatchers.IO) {
  // Pre-flight checks
  val box64ToUse = getBox64Binary()
  if (!box64ToUse.exists()) {
   return@withContext Result.failure(Box64BinaryNotFoundException())
  }

  if (!wineBinary.exists()) {
   return@withContext Result.failure(WineBinaryNotFoundException())
  }

  val winebootExe = File(wineDir, "lib/wine/x86_64-windows/wineboot.exe")
  val useWinebootBinary = winebootBinary.exists() && isElfBinary(winebootBinary)
  if (!useWinebootBinary && !winebootExe.exists()) {
   return@withContext Result.failure(
    EmulatorException("wineboot not found: ${winebootBinary.absolutePath}")
   )
  }
  if (winebootBinary.exists() && !useWinebootBinary) {
   Log.w(TAG, "wineboot is not an ELF binary, falling back to wineboot.exe")
  }

  val linker = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")
  if (!linker.exists()) {
   return@withContext Result.failure(
    EmulatorException("Rootfs linker not found: ${linker.absolutePath}")
   )
  }

  // Create wrappers for internal processes (wineserver)
  createWrappers()

  Log.i(TAG, "Initializing Wine prefix: ${containerDir.absolutePath}")
  Log.i(TAG, "Using linker: ${linker.absolutePath}")
  Log.i(TAG, "Using box64: ${box64ToUse.absolutePath}")
  Log.i(TAG, "Using wine: ${wineBinary.absolutePath}")
  Log.i(
   TAG,
   "Using wineboot: ${if (useWinebootBinary) winebootBinary.absolutePath else winebootExe.absolutePath}"
  )

  // Build command (same for all attempts)
  // We use linker's --library-path instead of LD_LIBRARY_PATH because:
  // 1. LD_LIBRARY_PATH breaks Android shell when running wineserver_wrapper
  // 2. BOX64_LD_LIBRARY_PATH only works after Box64 starts
  // 3. The linker itself needs library paths to load Box64's dependencies (libc.so.6, etc.)
  val libraryPath = "${rootfsDir.absolutePath}/lib:" +
   "${rootfsDir.absolutePath}/usr/lib:" +
   "${rootfsDir.absolutePath}/usr/lib/aarch64-linux-gnu"

  val command = if (useWinebootBinary) {
   listOf(
    linker.absolutePath,
    "--library-path", libraryPath,
    box64ToUse.absolutePath,
    winebootBinary.absolutePath,
    "-i"
   )
  } else {
   listOf(
    linker.absolutePath,
    "--library-path", libraryPath,
    box64ToUse.absolutePath,
    wineBinary.absolutePath,
    winebootExe.absolutePath,
    "--init"
   )
  }

  // 3-tier retry loop
  for (attemptNumber in 0 until 3) {
   try {
    Log.i(TAG, "=== Wine prefix initialization attempt ${attemptNumber + 1}/3 ===")

    // Build environment variables specific to this attempt
    val environmentVars = buildWinebootEnvironmentVariables(containerDir, attemptNumber)

    // Log key settings for this attempt
    Log.d(TAG, "Attempt $attemptNumber settings:")
    Log.d(TAG, " WINEDEBUG=${environmentVars["WINEDEBUG"]}")
    Log.d(TAG, " BOX64_DYNAREC=${environmentVars["BOX64_DYNAREC"] ?: "enabled"}")
    Log.d(TAG, " BOX64_DYNAREC_SAFEFLAGS=${environmentVars["BOX64_DYNAREC_SAFEFLAGS"] ?: "default"}")
    Log.d(TAG, " BOX64_DYNAREC_STRONGMEM=${environmentVars["BOX64_DYNAREC_STRONGMEM"] ?: "default"}")

    val processBuilder = ProcessBuilder(command)
    val env = processBuilder.environment()
    env.clear()
    env.putAll(environmentVars)
    processBuilder.redirectErrorStream(true)

    Log.d(TAG, "Running: ${command.joinToString(" ")}")

    val process = processBuilder.start()

    // Monitor output
    val output = StringBuilder()
    process.inputStream.bufferedReader().use { reader ->
     reader.lineSequence().forEach { line ->
      Log.d(TAG, "[wineboot] $line")
      output.appendLine(line)
     }
    }

    // Timeout: 90s for first attempt, 120s for retries
    val timeoutMs = if (attemptNumber == 0) 90_000L else 120_000L
    val completed = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
     process.waitFor()
     true
    }

    if (completed == null) {
     Log.w(TAG, "Attempt $attemptNumber: wineboot timed out after ${timeoutMs / 1000}s")
     process.destroyForcibly()

     // If last attempt, fail
     if (attemptNumber == 2) {
      return@withContext Result.failure(
       WinePrefixException("Wine prefix initialization timed out on all 3 attempts")
      )
     }

     // Cleanup and retry
     cleanupPartialPrefix(containerDir)
     val backoffMs = (attemptNumber + 1) * 2000L
     Log.i(TAG, "Waiting ${backoffMs / 1000}s before retry...")
     kotlinx.coroutines.delay(backoffMs)
     continue
    }

    val exitCode = process.exitValue()

    if (exitCode == 0) {
     // SUCCESS!
     Log.i(TAG, "Wine prefix initialized successfully on attempt ${attemptNumber + 1}")
     return@withContext Result.success(Unit)
    }

    // Failed with non-zero exit code
    val outputTail = output.takeLast(500).toString()
    Log.w(TAG, "Attempt $attemptNumber: wineboot exited with code $exitCode")
    Log.d(TAG, "Output (last 500 chars): $outputTail")

    // Special handling for SIGSEGV (exit code 139)
    if (exitCode == 139) {
     Log.e(TAG, "Attempt $attemptNumber: SIGSEGV detected (exit 139)")
    }

    // If last attempt, fail
    if (attemptNumber == 2) {
     return@withContext Result.failure(
      WinePrefixException("wineboot failed on all 3 attempts. Last exit code: $exitCode. Output: $outputTail")
     )
    }

    // Cleanup and retry
    cleanupPartialPrefix(containerDir)
    val backoffMs = (attemptNumber + 1) * 2000L
    Log.i(TAG, "Waiting ${backoffMs / 1000}s before retry...")
    kotlinx.coroutines.delay(backoffMs)

   } catch (e: Exception) {
    Log.e(TAG, "Attempt $attemptNumber: Exception during Wine prefix initialization", e)

    // If last attempt, fail
    if (attemptNumber == 2) {
     return@withContext Result.failure(
      WinePrefixException("Failed to initialize Wine prefix after 3 attempts: ${e.message}", e)
     )
    }

    // Cleanup and retry
    cleanupPartialPrefix(containerDir)
    val backoffMs = (attemptNumber + 1) * 2000L
    Log.i(TAG, "Waiting ${backoffMs / 1000}s before retry...")
    kotlinx.coroutines.delay(backoffMs)
   }
  }

  // Should never reach here, but safety fallback
  return@withContext Result.failure(
   WinePrefixException("Wine prefix initialization failed - all retry attempts exhausted")
  )
 }

 /**
  * Reads process metrics once (synchronously) for status checking.
  *
  * @param pid Process ID
  * @param startTime Process start time
  * @return ProcessMetrics or null if unavailable
  */
 private fun readProcessMetricsOnce(pid: Int, startTime: Long): ProcessMetrics? {
  val statFile = File("/proc/$pid/stat")
  val statusFile = File("/proc/$pid/status")

  if (!statFile.exists() || !statusFile.exists()) {
   return null
  }

  try {
   // Read memory from /proc/[pid]/status
   val statusContent = statusFile.readText()
   val vmRssLine = statusContent.lines().find { it.startsWith("VmRSS:") }
   val memoryMB = if (vmRssLine != null) {
    val parts = vmRssLine.split(Regex("\\s+"))
    val memoryKB = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    (memoryKB / 1024).toInt()
   } else {
    0
   }

   val uptimeMs = System.currentTimeMillis() - startTime

   // CPU calculation would require previous sample, so we return 0 for single read
   return ProcessMetrics(
    pid = pid,
    cpuPercent = 0f, // Cannot calculate from single sample
    memoryMB = memoryMB,
    uptimeMs = uptimeMs
   )
  } catch (e: Exception) {
   Log.w(TAG, "Failed to read process metrics for PID $pid", e)
   return null
  }
 }

 /**
  * Builds environment variables for Wine process execution.
  */
 private fun buildEnvironmentVariables(container: EmulatorContainer): Map<String, String> {
  val config = container.config
  // WINEPREFIX should be the container root, not rootPath/drive_c
  // Wine creates drive_c automatically
  val winePrefix = container.rootPath

  return buildMap {
   // Wine basic configuration
   put("WINEPREFIX", winePrefix.absolutePath)
   put("HOME", winePrefix.absolutePath)
   put("TMPDIR", context.cacheDir.absolutePath)
   put("LANG", "C")
   put("LC_ALL", "C")
   // CRITICAL: Use minimal logging to prevent SIGSEGV crashes
   // Excessive logging (+all,+relay,+file) destabilizes Wine execution
   put("WINEDEBUG", "-all") // Silent - most stable for game execution
   put("WINEARCH", "win64")
   put("WINELOADERNOEXEC", "1")
   put("WINESERVER", File(wineDir, "bin/wineserver_wrapper").absolutePath)

   // Graphics configuration
   when (config.directXWrapper) {
    DirectXWrapperType.DXVK -> {
     put("DXVK_HUD", if (config.enableFPS) "fps" else "0")
     put("DXVK_LOG_LEVEL", "warn")
     put("DXVK_STATE_CACHE_PATH", File(dataDir, "cache/dxvk").absolutePath)
    }
    DirectXWrapperType.VKD3D -> {
     put("VKD3D_CONFIG", "dxr")
     put("VKD3D_DEBUG", "warn")
    }
    DirectXWrapperType.WINED3D -> {
     put("WINED3D", "1")
    }
    DirectXWrapperType.NONE -> {
     // No DirectX wrapper
    }
   }

   // Box64 performance configuration
   when (config.performancePreset) {
    PerformancePreset.MAXIMUM_PERFORMANCE -> {
     put("BOX64_DYNAREC_BIGBLOCK", "3")
     put("BOX64_DYNAREC_STRONGMEM", "0")
     put("BOX64_DYNAREC_FASTNAN", "1")
     put("BOX64_DYNAREC_FASTROUND", "1")
    }
    PerformancePreset.BALANCED -> {
     put("BOX64_DYNAREC_BIGBLOCK", "2")
     put("BOX64_DYNAREC_STRONGMEM", "1")
     put("BOX64_DYNAREC_FASTNAN", "1")
    }
    PerformancePreset.MAXIMUM_STABILITY -> {
     put("BOX64_DYNAREC_BIGBLOCK", "1")
     put("BOX64_DYNAREC_STRONGMEM", "2")
     put("BOX64_DYNAREC_FASTNAN", "0")
     put("BOX64_DYNAREC_FASTROUND", "0")
    }
   }

   // Box64 log configuration
   put("BOX64_LOG", "0") // Disable verbose logging
   put("BOX64_NOBANNER", "1") // Disable startup banner

   // Display configuration
   put("DISPLAY", ":0")
   put("MESA_GL_VERSION_OVERRIDE", "4.6")
   put("MESA_GLSL_VERSION_OVERRIDE", "460")

   // Audio configuration
   when (config.audioDriver) {
    AudioDriverType.ALSA -> {
     put("AUDIODRIVER", "alsa")
    }
    AudioDriverType.PULSEAUDIO -> {
     put("AUDIODRIVER", "pulseaudio")
    }
    AudioDriverType.NONE -> {
     put("AUDIODRIVER", "null")
    }
   }

   // Custom environment variables from config
   putAll(config.customEnvVars)

   // Add system paths
   val existingPath = System.getenv("PATH") ?: ""
   put("PATH", "${wineDir.absolutePath}/bin:${box64Dir.absolutePath}:$existingPath")

   // Set LD_LIBRARY_PATH for Box64/Wine loader (glibc from rootfs)
   put(
    "LD_LIBRARY_PATH",
    "${rootfsDir.absolutePath}/lib:" +
     "${rootfsDir.absolutePath}/usr/lib:" +
     "${rootfsDir.absolutePath}/usr/lib/aarch64-linux-gnu:" +
     "${wineDir.absolutePath}/lib:" +
     "${wineDir.absolutePath}/lib/wine/x86_64-unix"
   )

   // Set Wine DLL path for Windows libraries
   put("WINEDLLPATH", "${wineDir.absolutePath}/lib/wine/x86_64-windows")

   // Add Box64 library paths for better compatibility
   put("BOX64_LD_LIBRARY_PATH", "${rootfsDir.absolutePath}/lib:${rootfsDir.absolutePath}/usr/lib:${rootfsDir.absolutePath}/usr/lib/x86_64-linux-gnu:${wineDir.absolutePath}/lib/wine/x86_64-unix")
   put("BOX64_PATH", "${wineDir.absolutePath}/bin")

   // Force Box64 to use its own library loader instead of system dlopen
   // This bypasses Android SELinux restrictions on dlopen from app_data_file
   put("BOX64_EMULATED_LIBS", "libc.so.6:libpthread.so.0:libdl.so.2:librt.so.1:libm.so.6")
   put("BOX64_ALLOWMISSINGLIBS", "1")
  }
 }

 /**
  * Gets the PID of a Process using reflection.
  * Note: Process.pid() is available in Android API 24+, but not as a public method.
  */
 private fun getPid(process: Process): Int {
  return try {
   val pidField = process.javaClass.getDeclaredField("pid")
   pidField.isAccessible = true
   pidField.getInt(process)
  } catch (e: Exception) {
   Log.w(TAG, "Failed to get PID via reflection, using fallback", e)
   -1 // Fallback PID
  }
 }

 private fun isElfBinary(file: File): Boolean {
  if (!file.exists() || !file.isFile) return false
  return try {
   file.inputStream().use { input ->
    val header = ByteArray(4)
    if (input.read(header) != 4) return false
    header[0] == 0x7F.toByte() &&
     header[1] == 'E'.code.toByte() &&
     header[2] == 'L'.code.toByte() &&
     header[3] == 'F'.code.toByte()
   }
  } catch (e: Exception) {
   false
  }
 }

 /**
  * Get the Box64 binary to use for execution.
  * Uses the extracted Box64 which has the hardcoded interpreter path.
  * The setupHardcodedLinkerPath() function creates a symlink at the expected location.
  */
 private fun getBox64Binary(): File {
  return box64Binary
 }

 /**
  * Setup hardcoded linker path for Box64.
  * Box64 has hardcoded interpreter: /data/data/com.winlator/files/rootfs/lib/ld-linux-aarch64.so.1
  *
  * Since the path in our app is too long to patch, we create a symlink at rootfs/lib/ld-linux-aarch64.so.1
  * that points to the actual linker at rootfs/usr/lib/ld-linux-aarch64.so.1.
  */
 private fun setupHardcodedLinkerPath() {
  // The actual linker is at usr/lib/ld-linux-aarch64.so.1
  val actualLinker = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")

  if (!actualLinker.exists()) {
   Log.w(TAG, "Rootfs linker not found: ${actualLinker.absolutePath}")
   return
  }

  // Create the lib directory if it doesn't exist
  val libDir = File(rootfsDir, "lib")
  if (!libDir.exists()) {
   libDir.mkdirs()
   Log.d(TAG, "Created lib directory: ${libDir.absolutePath}")
  }

  // Create symlink at lib/ld-linux-aarch64.so.1 -> ../usr/lib/ld-linux-aarch64.so.1
  val linkerSymlink = File(libDir, "ld-linux-aarch64.so.1")
  try {
   if (!linkerSymlink.exists()) {
    // Use relative path for symlink
    val symlinkTarget = "../usr/lib/ld-linux-aarch64.so.1"
    ProcessBuilder("ln", "-s", symlinkTarget, linkerSymlink.absolutePath)
     .redirectErrorStream(true)
     .start()
     .apply {
      waitFor()
      val output = inputStream.bufferedReader().readText()
      if (exitValue() == 0) {
       Log.i(TAG, "Created linker symlink: ${linkerSymlink.absolutePath} -> $symlinkTarget")
      } else {
       Log.w(TAG, "Failed to create linker symlink: $output")
      }
     }
   } else {
    Log.d(TAG, "Linker symlink already exists: ${linkerSymlink.absolutePath}")
   }
  } catch (e: Exception) {
   Log.w(TAG, "Could not create linker symlink: ${e.message}")
  }

  // Copy Box64 to rootfs and patch its interpreter to use Android system linker
  val rootfsBinDir = File(rootfsDir, "usr/local/bin")
  rootfsBinDir.mkdirs()
  val rootfsBox64 = File(rootfsBinDir, "box64")

  try {
   if (!rootfsBox64.exists() && box64Binary.exists()) {
    box64Binary.copyTo(rootfsBox64, overwrite = true)
    rootfsBox64.setExecutable(true, false)
    Log.i(TAG, "Copied Box64 to rootfs: ${rootfsBox64.absolutePath}")

    // Note: Box64 interpreter path is hardcoded in the binary and too long to patch
    // We handle this by invoking ld-linux directly when running Box64
    Log.d(TAG, "Box64 will be invoked via explicit ld-linux interpreter")
   } else if (rootfsBox64.exists()) {
    Log.d(TAG, "Box64 already exists in rootfs: ${rootfsBox64.absolutePath}")
   }
  } catch (e: Exception) {
   Log.w(TAG, "Could not copy Box64 to rootfs: ${e.message}")
  }
 }

 private fun createWrappers() {
  val linker = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")
  if (!linker.exists()) {
   Log.w(TAG, "Cannot create wrappers: linker not found at ${linker.absolutePath}")
   return
  }

  // wineserver wrapper - explicitly invokes linker with Box64 to bypass hardcoded interpreter path
  val wrapperDir = File(wineDir, "bin")
  wrapperDir.mkdirs()
  val wrapper = File(wrapperDir, "wineserver_wrapper")

  val content = """
   #!/system/bin/sh
   unset LD_LIBRARY_PATH
   exec ${linker.absolutePath} ${box64Binary.absolutePath} ${wineserverBinary.absolutePath} "${'$'}@"
  """.trimIndent()

  try {
   wrapper.writeText(content)
   wrapper.setExecutable(true, false)
   Log.d(TAG, "Created/Updated wineserver wrapper at ${wrapper.absolutePath}")
  } catch (e: Exception) {
   Log.e(TAG, "Failed to create wineserver wrapper", e)
  }
 }

 private fun extractAsset(assetPath: String, destination: File) {
  destination.parentFile?.mkdirs()
  context.assets.open(assetPath).use { input ->
   FileOutputStream(destination).use { output ->
    input.copyTo(output)
   }
  }
  Log.d(TAG, "Extracted asset: $assetPath -> ${destination.absolutePath}")
 }

 private fun calculateDirectorySize(directory: File): Long {
  if (!directory.exists()) return 0
  return directory.walkTopDown()
   .filter { it.isFile }
   .map { it.length() }
   .sum()
 }
}
