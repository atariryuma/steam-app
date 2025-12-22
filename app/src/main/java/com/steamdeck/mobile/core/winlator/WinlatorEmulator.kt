package com.steamdeck.mobile.core.winlator

import android.content.Context
import android.system.Os
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.util.ElfPatcher
import com.steamdeck.mobile.core.wine.WineMonoInstaller
import com.steamdeck.mobile.domain.emulator.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
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
 private val processMonitor: ProcessMonitor,
 private val wineMonoInstaller: WineMonoInstaller
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

 // Active processes map (processId -> ProcessInfo) - Thread-safe for concurrent access
 private val activeProcesses = ConcurrentHashMap<String, ProcessInfo>()

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
  // CRITICAL FIX: Use Termux proot (proven compatible with Android)
  // Previous v5.3.0 was incompatible and crashed with SIGSEGV
  private const val PROOT_ASSET = "winlator/proot-termux-aarch64.txz"

  // Process management timeouts
  private const val OUTPUT_DRAIN_TIMEOUT_MS = 2000L  // Timeout for output reading completion
  private const val RETRY_BACKOFF_MS = 2000L         // Base backoff time for retries
  private const val SYMLINK_CREATION_TIMEOUT_MS = 5000L  // Timeout for ln command execution

  // Retry configuration
  private const val MAX_RETRY_ATTEMPTS = 3           // Maximum retry attempts for wineboot initialization
  private const val WINEBOOT_TIMEOUT_FIRST_MS = 60_000L   // First attempt timeout (balanced)
  private const val WINEBOOT_TIMEOUT_RETRY_MS = 90_000L   // Retry timeout (more conservative)

  // Wineserver socket polling
  private const val WINESERVER_SOCKET_POLL_ATTEMPTS = 20  // Max attempts to find wineserver socket
  private const val WINESERVER_SOCKET_POLL_DELAY_MS = 100L // Delay between socket poll attempts
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

   AppLogger.d(TAG, "Winlator availability: binaries=$binariesExist, rootfs=$rootfsExtracted")
   Result.success(isAvailable)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Error checking availability", e)
   Result.failure(EmulatorException("Failed to check Winlator availability", e))
  }
 }

 override suspend fun initialize(
  progressCallback: ((Float, String) -> Unit)?
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Initializing Winlator emulator...")
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

    // Extract proot.txz from assets (Termux proot - Android-compatible)
    val prootTxzFile = File(prootDir, "proot-termux-aarch64.txz")
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
      AppLogger.e(TAG, "Failed to extract PRoot", extractResult.exceptionOrNull())
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
     AppLogger.i(TAG, "PRoot extracted and set executable: ${prootBinary.absolutePath}")

     // EXPERIMENTAL: Disable PRoot PIE/TLS patches (testing if patches cause SIGSEGV)
     // // Patch PRoot to PIE format (ET_DYN) for Android 5.0+ compatibility
     // val patchResult = ElfPatcher.patchToPie(prootBinary)
     // if (patchResult.isFailure) {
     //  AppLogger.w(TAG, "Failed to patch PRoot to PIE: ${patchResult.exceptionOrNull()?.message}")
     //  // Continue anyway - binary might already be PIE
     // }

     // // Patch TLS alignment for Android 12+ (requires 64-byte alignment)
     // val tlsPatchResult = ElfPatcher.patchTlsAlignment(prootBinary, 64)
     // if (tlsPatchResult.isFailure) {
     //  AppLogger.w(TAG, "Failed to patch PRoot TLS alignment: ${tlsPatchResult.exceptionOrNull()?.message}")
     //  // Continue anyway - might already be aligned
     // }
     AppLogger.i(TAG, "PRoot extracted, skipping PIE/TLS patches to test original binary")

     progressCallback?.invoke(0.15f, "PRoot ready")
    } else {
     AppLogger.e(TAG, "PRoot .txz file not found: ${prootTxzFile.absolutePath}")
     return@withContext Result.failure(
      EmulatorException("PRoot asset file not found")
     )
    }
   } else {
    progressCallback?.invoke(0.15f, "PRoot already extracted")
   }

   // EXPERIMENTAL: Disable PRoot patching to test if patches cause SIGSEGV
   // PRoot v5.3.0 is already statically linked and may not need PIE/TLS patches
   // TODO: Re-enable if proot works without patches, or try different proot version
   if (prootBinary.exists()) {
    AppLogger.i(TAG, "PRoot binary exists, skipping PIE/TLS patches (testing if patches cause SIGSEGV)")
    // // Verify and patch PIE if needed
    // val patchPieResult = ElfPatcher.patchToPie(prootBinary)
    // if (patchPieResult.isFailure) {
    //  AppLogger.w(TAG, "PRoot PIE patch check failed: ${patchPieResult.exceptionOrNull()?.message}")
    // } else {
    //  AppLogger.d(TAG, "PRoot PIE patch verified/applied")
    // }

    // // Verify and patch TLS if needed
    // val patchTlsResult = ElfPatcher.patchTlsAlignment(prootBinary, 64)
    // if (patchTlsResult.isFailure) {
    //  AppLogger.w(TAG, "PRoot TLS patch check failed: ${patchTlsResult.exceptionOrNull()?.message}")
    // } else {
    //  AppLogger.d(TAG, "PRoot TLS alignment verified/applied")
    // }
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
      AppLogger.e(TAG, "Failed to extract Box64", extractResult.exceptionOrNull())
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
      AppLogger.i(TAG, "Box64 binary moved from ${extractedBox64.absolutePath} to ${box64Binary.absolutePath}")

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
     AppLogger.i(TAG, "Box64 extracted and set executable: ${box64Binary.absolutePath}")

     // Patch Box64 to PIE format (ET_DYN) for Android 5.0+ compatibility
     val patchResult = ElfPatcher.patchToPie(box64Binary)
     if (patchResult.isFailure) {
      AppLogger.w(TAG, "Failed to patch Box64 to PIE: ${patchResult.exceptionOrNull()?.message}")
      // Continue anyway - binary might already be PIE
     }

     // Patch TLS alignment for Android 12+ (requires 64-byte alignment)
     val tlsPatchResult = ElfPatcher.patchTlsAlignment(box64Binary, 64)
     if (tlsPatchResult.isFailure) {
      AppLogger.w(TAG, "Failed to patch Box64 TLS alignment: ${tlsPatchResult.exceptionOrNull()?.message}")
      // Continue anyway - might not have PT_TLS segment
     }

     progressCallback?.invoke(0.3f, "Box64 ready")
    } else {
     AppLogger.e(TAG, "Box64 .txz file not found: ${box64TxzFile.absolutePath}")
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
     AppLogger.w(TAG, "Box64 PIE patch check failed: ${patchPieResult.exceptionOrNull()?.message}")
    }

    // Verify and patch TLS if needed
    val patchTlsResult = ElfPatcher.patchTlsAlignment(box64Binary, 64)
    if (patchTlsResult.isFailure) {
     AppLogger.w(TAG, "Box64 TLS patch check failed: ${patchTlsResult.exceptionOrNull()?.message}")
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
      AppLogger.i(TAG, "Rootfs extraction successful")

      // Verify Wine binary exists
      if (wineBinary.exists()) {
       wineBinary.setExecutable(true, false)
       AppLogger.i(TAG, "Wine binary ready: ${wineBinary.absolutePath}")

       // Set wineserver executable
       val wineserver = File(wineDir, "bin/wineserver")
       if (wineserver.exists()) {
        wineserver.setExecutable(true, false)
       }
      } else {
       AppLogger.w(TAG, "Wine binary not found after extraction at: ${wineBinary.absolutePath}")
      }

      // Delete temporary .txz file to save space
      if (rootfsTxzFile.exists()) {
       rootfsTxzFile.delete()
       AppLogger.d(TAG, "Cleaned up temporary rootfs.txz file")
      }

      // Setup hardcoded linker path now that rootfs is extracted
      setupHardcodedLinkerPath()
      AppLogger.d(TAG, "setupHardcodedLinkerPath() completed")

      // NOTE: Wine hardcoded path issue is now handled by proot virtualization
      // Wine binaries have compile-time hardcoded paths (/data/data/com.winlator/files/rootfs/tmp)
      // Instead of creating symlinks (which fails due to Android security), we wrap all
      // Wine/Box64 commands with proot to bind our actual rootfs to the hardcoded path
      // See wineboot/wineserver/launchExecutable command construction

      // CRITICAL FIX: Short Symlink Strategy for PT_INTERP size limit
      // Problem: actualLinker path is 89 chars, PT_INTERP limit is 63 bytes
      // Solution: Create short symlink (~43 chars) pointing to actual glibc linker
      AppLogger.d(TAG, "Starting Box64 interpreter path patch...")
      val actualLinker = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")
      val shortLinkerSymlink = File(context.filesDir, "l")

      AppLogger.d(TAG, "actualLinker: ${actualLinker.absolutePath} (${actualLinker.absolutePath.length} chars)")
      AppLogger.d(TAG, "shortSymlink: ${shortLinkerSymlink.absolutePath} (${shortLinkerSymlink.absolutePath.length} chars)")

      if (actualLinker.exists() && box64Binary.exists()) {
       try {
        // Remove old symlink if exists
        if (shortLinkerSymlink.exists()) {
         shortLinkerSymlink.delete()
         AppLogger.d(TAG, "Removed existing symlink")
        }

        // Create symlink using native Android API (no external commands)
        Os.symlink(actualLinker.absolutePath, shortLinkerSymlink.absolutePath)
        AppLogger.i(TAG, "Created symlink: ${shortLinkerSymlink.absolutePath} -> ${actualLinker.absolutePath}")

        // Verify symlink
        if (!shortLinkerSymlink.exists()) {
         AppLogger.e(TAG, "Symlink creation failed - file doesn't exist")
         return@withContext Result.failure(
          EmulatorException("Failed to create linker symlink")
         )
        }

        // Patch Box64 to use short symlink path
        val patchResult = ElfPatcher.patchInterpreterPath(
         box64Binary,
         shortLinkerSymlink.absolutePath
        )

        if (patchResult.isSuccess) {
         AppLogger.i(TAG, "Successfully patched Box64 interpreter to: ${shortLinkerSymlink.absolutePath}")
        } else {
         AppLogger.e(TAG, "Failed to patch Box64 interpreter", patchResult.exceptionOrNull())
         return@withContext Result.failure(
          patchResult.exceptionOrNull() ?: EmulatorException("Box64 interpreter patch failed")
         )
        }
       } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to setup linker symlink", e)
        return@withContext Result.failure(
         EmulatorException("Linker symlink setup failed: ${e.message}", e)
        )
       }
      } else {
       AppLogger.w(TAG, "Skipping Box64 patch: actualLinker=${actualLinker.exists()}, box64=${box64Binary.exists()}")
      }

      // EXPERIMENTAL: Skip proot PT_INTERP patch
      // PRoot is statically linked and has NO PT_INTERP header (logs show "No PT_INTERP header found")
      // Patching it is unnecessary and may cause issues
      AppLogger.d(TAG, "Skipping PRoot interpreter path patch (statically linked, no PT_INTERP)")

      // CRITICAL FIX: Create libfreetype.so.6 symlink for Wine/Box64
      // Wine+Box64 needs libfreetype.so.6 for font rendering (Steam installer GUI)
      // Rootfs has libfreetype.so.6.20.2 but no .so.6 symlink → "cannot find FreeType library"
      setupLibrarySymlinks()
     }.onFailure { error ->
      AppLogger.e(TAG, "Rootfs extraction failed: ${error.message}", error)
      return@withContext Result.failure(
       EmulatorException("Failed to extract Wine rootfs: ${error.message}", error)
      )
     }
    }
   } else {
    AppLogger.i(TAG, "Wine already extracted, skipping")
    // Ensure linker is setup even if rootfs was already extracted
    setupHardcodedLinkerPath()

    // NOTE: Wine hardcoded paths now handled by proot (see command construction)

    // CRITICAL FIX: Short Symlink Strategy (also needed when Wine already extracted)
    val actualLinker = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")
    val shortLinkerSymlink = File(context.filesDir, "l")

    if (actualLinker.exists() && box64Binary.exists()) {
     try {
      // Remove old symlink if exists
      if (shortLinkerSymlink.exists()) {
       shortLinkerSymlink.delete()
      }

      // Create symlink using native Android API
      Os.symlink(actualLinker.absolutePath, shortLinkerSymlink.absolutePath)
      AppLogger.i(TAG, "Created symlink: ${shortLinkerSymlink.absolutePath} -> ${actualLinker.absolutePath}")

      // Patch Box64 to use short symlink path
      val patchResult = ElfPatcher.patchInterpreterPath(
       box64Binary,
       shortLinkerSymlink.absolutePath
      )

      if (patchResult.isSuccess) {
       AppLogger.i(TAG, "Successfully patched Box64 with short symlink")
      } else {
       AppLogger.e(TAG, "Box64 patch failed", patchResult.exceptionOrNull())
      }
     } catch (e: Exception) {
      AppLogger.e(TAG, "Symlink setup failed", e)
     }
    } else {
     AppLogger.w(TAG, "Skipping Box64 patch: linker=${actualLinker.exists()}, box64=${box64Binary.exists()}")
    }

    // EXPERIMENTAL: Skip proot PT_INTERP patch (Wine already extracted case)
    // PRoot is statically linked and has NO PT_INTERP header
    AppLogger.d(TAG, "Skipping PRoot interpreter path patch (Wine already extracted, proot is statically linked)")

    // CRITICAL FIX: Ensure library symlinks even if Wine already extracted
    setupLibrarySymlinks()

    progressCallback?.invoke(1.0f, "Wine already ready")
   }

   progressCallback?.invoke(1.0f, "Initialization complete")

   AppLogger.i(TAG, "Winlator initialization complete")
   Result.success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Initialization failed", e)
   Result.failure(EmulatorException("Failed to initialize Winlator: ${e.message}", e))
  }
 }

 override suspend fun createContainer(
  config: EmulatorContainerConfig
 ): Result<EmulatorContainer> = withContext(Dispatchers.IO) {
  try {
   // Best Practice: Use fixed ID for default shared container
   // This enables efficient container reuse across all games
   val containerId = if (config.name == "Default Container") {
    "default_shared_container"
   } else {
    "${System.currentTimeMillis()}"
   }
   val containerDir = File(containersDir, containerId)

   // Check if container already exists (prevent duplicate creation)
   if (containerDir.exists()) {
    AppLogger.w(TAG, "Container already exists: $containerId, skipping creation")
    // Return existing container info
    return@withContext Result.success(
     EmulatorContainer(
      id = containerId,
      name = config.name,
      config = config,
      rootPath = containerDir,
      createdAt = containerDir.lastModified(),
      lastUsedAt = containerDir.lastModified(),
      sizeBytes = calculateDirectorySize(containerDir)
     )
    )
   }

   // Create container directory structure
   val driveC = File(containerDir, "drive_c")
   File(driveC, "windows").mkdirs()
   File(driveC, "Program Files").mkdirs()
   File(driveC, "Program Files (x86)").mkdirs()
   File(driveC, "users/Public").mkdirs()

   // Initialize Wine prefix with wineboot
   val initResult = initializeWinePrefix(containerDir)
   if (initResult.isFailure) {
    AppLogger.e(TAG, "Wine prefix initialization failed: ${initResult.exceptionOrNull()?.message}")
    containerDir.deleteRecursively()
    return@withContext Result.failure(
     initResult.exceptionOrNull()
      ?: EmulatorException("Wine prefix initialization failed")
    )
   }

   // Run wineboot with -u to update the Wine prefix (loads services)
   // This is the "Normal (Load all services)" mode recommended for Steam
   AppLogger.i(TAG, "Running wineboot -u to load services...")
   val updateResult = runWinebootUpdate(containerDir)
   if (updateResult.isFailure) {
    AppLogger.w(TAG, "wineboot -u failed (non-fatal): ${updateResult.exceptionOrNull()?.message}")
   }

   // CRITICAL FIX: Create C:\windows\system32\ directory manually
   // Wine's minimal prefix doesn't create this directory, causing
   // "could not open working directory" errors for many Windows apps
   try {
    val windowsDir = File(containerDir, "drive_c/windows")
    val system32Dir = File(windowsDir, "system32")
    if (!system32Dir.exists()) {
     system32Dir.mkdirs()
     // Set permissions to match Wine-created directories (rwx------)
     system32Dir.setReadable(true, false)
     system32Dir.setWritable(true, false)
     system32Dir.setExecutable(true, false)
     AppLogger.i(TAG, "Created C:\\windows\\system32\\ directory with correct permissions")
    }
   } catch (e: Exception) {
    AppLogger.w(TAG, "Failed to create system32 directory (non-fatal)", e)
   }

   // CRITICAL: Copy WoW64 DLLs to system32 for 32-bit app support
   // Wine 9.2+ WoW64 mode requires these DLLs to run 32-bit Windows executables
   try {
    val system32Dir = File(containerDir, "drive_c/windows/system32")
    val wineLibDir = File(wineDir, "lib/wine/x86_64-windows")
    // WoW64 core DLLs + dependencies required for 32-bit app execution
    val wow64Dlls = listOf(
     "wow64.dll",       // WoW64 core
     "wow64cpu.dll",    // WoW64 CPU emulation
     "wow64win.dll",    // WoW64 Windows API bridge
     "win32u.dll",      // Win32 user mode library (required by wow64win.dll)
     "ntdll.dll",       // NT kernel interface (required by all WoW64 DLLs)
     "kernelbase.dll",  // Kernel base library
     "kernel32.dll"     // Win32 kernel API
    )

    wow64Dlls.forEach { dllName ->
     val sourceDll = File(wineLibDir, dllName)
     val targetDll = File(system32Dir, dllName)

     if (sourceDll.exists()) {
      sourceDll.copyTo(targetDll, overwrite = true)
      AppLogger.i(TAG, "Copied WoW64 DLL: $dllName (${sourceDll.length()} bytes)")
     } else {
      AppLogger.w(TAG, "WoW64 DLL not found: ${sourceDll.absolutePath}")
     }
    }
    AppLogger.i(TAG, "WoW64 DLLs installed successfully for 32-bit app support")
   } catch (e: Exception) {
    AppLogger.w(TAG, "Failed to copy WoW64 DLLs (non-fatal): ${e.message}", e)
   }

   // Set Windows version to Windows 10 (required for Steam)
   // Use direct registry edit (Winlator method) instead of wine regedit to avoid proot crashes
   AppLogger.i(TAG, "Configuring Wine to report as Windows 10...")
   val directEditSuccess = setWindowsVersionDirect(containerDir)
   if (!directEditSuccess) {
    AppLogger.w(TAG, "Direct registry edit failed, trying fallback method...")
    // Fallback: try the old wine regedit method
    val versionResult = setWindowsVersion(containerDir)
    if (versionResult.isFailure) {
     AppLogger.e(TAG, "CRITICAL: Failed to set Windows version: ${versionResult.exceptionOrNull()?.message}")
     AppLogger.e(TAG, "Steam installation will likely fail without Windows 10 registry configuration")
    } else {
     AppLogger.i(TAG, "Windows 10 registry configuration completed via fallback method")
    }
   } else {
    AppLogger.i(TAG, "Windows 10 registry configuration completed successfully (direct edit)")
   }

   // NEW: Install Wine Mono for .NET Framework compatibility
   // Required for 32-bit applications (e.g., SteamSetup.exe NSIS installer)
   try {
    AppLogger.i(TAG, "Installing Wine Mono for WoW64 support...")
    val monoInstallResult = installWineMonoIfNeeded(containerDir)
    if (monoInstallResult.isFailure) {
     AppLogger.w(TAG, "Wine Mono installation failed (non-fatal): ${monoInstallResult.exceptionOrNull()?.message}")
    } else {
     AppLogger.i(TAG, "Wine Mono installation completed successfully")
    }
   } catch (e: Exception) {
    AppLogger.w(TAG, "Wine Mono installation error (non-fatal)", e)
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

   AppLogger.i(TAG, "Created container: ${container.name} (${container.id})")
   Result.success(container)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to create container", e)
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
     AppLogger.w(TAG, "Failed to load container ${dir.name}", e)
     null
    }
   } ?: emptyList()

   Result.success(containers)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to list containers", e)
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
   AppLogger.i(TAG, "Deleted container: $containerId")
   Result.success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to delete container", e)
   Result.failure(EmulatorException("Failed to delete container", e))
  }
 }

 override suspend fun launchExecutable(
  container: EmulatorContainer,
  executable: File,
  arguments: List<String>
 ): Result<EmulatorProcess> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Launching executable: ${executable.absolutePath}")
   AppLogger.d(TAG, "Container: ${container.name}, Arguments: $arguments")

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
   val environmentVars = buildEnvironmentVariables(container).toMutableMap()
   val rootfsLibraryPath = buildRootfsLibraryPath()

   // Add executable's directory to WINEDLLPATH for DLL loading
   // Priority: Game DLLs first, then Wine system DLLs
   // This allows Wine to find both game-specific DLLs (ffmpeg.dll) and Windows system DLLs (winhttp.dll)
   // IMPORTANT: Use proot-virtualized path for Wine system DLLs (rootfs is mounted to /data/data/com.winlator/files/rootfs)
   executable.parentFile?.let { exeDir ->
    val wineSystemDllPath = "/data/data/com.winlator/files/rootfs/opt/wine/lib/wine/x86_64-windows"
    val combinedPath = "${exeDir.absolutePath}:${wineSystemDllPath}"
    environmentVars["WINEDLLPATH"] = combinedPath
    AppLogger.d(TAG, "Set WINEDLLPATH: $combinedPath")
   }

   // 4. Build command - wrap with proot for hardcoded path virtualization
   // CRITICAL FIX: Do NOT pass linker as proot argument
   // Box64 binary already has PT_INTERP set to linker path - ELF loader handles it automatically
   // Passing linker to proot causes proot to try executing ld-linux directly → SIGSEGV
   val command = buildList {
    add(prootBinary.absolutePath)

    // Bind rootfs directory for Wine system files
    add("-b")
    add("${rootfsDir.absolutePath}:/data/data/com.winlator/files/rootfs")

    // CRITICAL: Bind /tmp to rootfs/tmp for XServer socket access
    // XServer creates socket at: rootfsDir/tmp/.X11-unix/X0 (Android filesystem)
    // Wine expects socket at: /tmp/.X11-unix/X0 (inside PRoot chroot)
    // This bind mount makes them the same file, allowing Wine to connect to XServer
    add("-b")
    add("${rootfsDir.absolutePath}/tmp:/tmp")

    add(box64ToUse.absolutePath)  // ✅ Execute Box64 directly (PT_INTERP handles linker)
    add(wineBinary.absolutePath)
    add(executable.absolutePath)
    addAll(arguments)
   }

   AppLogger.d(TAG, "Command: ${command.joinToString(" ")}")
   AppLogger.d(TAG, "Environment: $environmentVars")

   // 5. Start process
   val processBuilder = ProcessBuilder(command)
   // Clear environment and set only what we need
   val env = processBuilder.environment()
   env.clear()
   env.putAll(environmentVars)
   // CRITICAL: Set LD_LIBRARY_PATH via environment (not as proot argument)
   env["LD_LIBRARY_PATH"] = rootfsLibraryPath
   processBuilder.redirectErrorStream(true)

   // WORKING DIRECTORY FIX: Set to executable's parent directory
   // This allows Wine to find DLLs in the same folder as the .exe
   // Wine will use this as the current working directory when launching the application
   val workingDir = executable.parentFile ?: File("/sdcard")

   if (workingDir.exists() && workingDir.canRead()) {
    processBuilder.directory(workingDir)
    AppLogger.d(TAG, "Working directory: ${workingDir.absolutePath}")
   } else {
    // Fallback to root if parent directory is inaccessible
    processBuilder.directory(File("/"))
    AppLogger.w(TAG, "Working directory not accessible, using root: ${workingDir.absolutePath}")
   }

   val process = processBuilder.start()
   val pid = getPid(process)
   val processId = "${System.currentTimeMillis()}_$pid"

   AppLogger.i(TAG, "Process launched: PID=$pid, ProcessId=$processId")

   // 6. CRITICAL FIX: Drain process output to prevent buffer overflow deadlock
   // Launch background coroutine to continuously read stdout
   // This prevents the process from blocking when output buffer fills up
   // OPTIMIZATION: Rate-limit logging to reduce I/O overhead (10-20% CPU reduction)
   // BEST PRACTICE: Use cancellable reading loop instead of forEach
   CoroutineScope(Dispatchers.IO).launch {
    try {
     process.inputStream.bufferedReader().use { reader ->
      var lineCount = 0
      // Use while loop to continuously read until EOF (process termination)
      while (true) {
       val line = reader.readLine() ?: break  // EOF reached, process terminated
       // Only log errors and first 50 lines to reduce logging overhead
       if (line.contains("err", ignoreCase = true) ||
           line.contains("warn", ignoreCase = true) ||
           line.contains("fail", ignoreCase = true) ||
           lineCount < 50) {
        AppLogger.d(TAG, "[Wine:$pid] $line")
       }
       lineCount++
      }
      if (lineCount >= 50) {
       AppLogger.d(TAG, "[Wine:$pid] ... ($lineCount total lines, showing errors only)")
      }
     }
    } catch (e: Exception) {
     // Stream closed or read interrupted - this is expected on process termination
     AppLogger.d(TAG, "[Wine:$pid] Output stream monitoring stopped: ${e.message}")
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
   AppLogger.e(TAG, "Failed to launch executable", e)
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
     AppLogger.w(TAG, "Failed to read process metrics", e)
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
   AppLogger.e(TAG, "Failed to get process status", e)
   Result.failure(EmulatorException("Failed to get process status: ${e.message}", e))
  }
 }

 override fun monitorProcess(processId: String, intervalMs: Long): Flow<EmulatorProcessStatus> = flow {
  AppLogger.i(TAG, "Starting process monitoring: $processId (interval: ${intervalMs}ms)")

  try {
   while (true) {
    val statusResult = getProcessStatus(processId)

    when {
     statusResult.isSuccess -> {
      val status = statusResult.getOrThrow()
      emit(status)

      // If process has terminated, complete the flow
      if (!status.isRunning) {
       AppLogger.i(TAG, "Process terminated: $processId (exitCode: ${status.exitCode})")
       break
      }
     }
     else -> {
      // Process not found or error - assume terminated
      AppLogger.w(TAG, "Process monitoring error: ${statusResult.exceptionOrNull()?.message}")
      break
     }
    }

    delay(intervalMs)
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Process monitoring failed: $processId", e)
   // Flow will complete naturally
  } finally {
   // CRITICAL FIX: Remove process from activeProcesses to prevent memory leak
   activeProcesses.remove(processId)
   AppLogger.d(TAG, "Process monitoring stopped and cleaned up: $processId (remaining active processes: ${activeProcesses.size})")
  }
 }.flowOn(Dispatchers.IO)

 override suspend fun killProcess(processId: String, force: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   val processInfo = activeProcesses[processId]
    ?: return@withContext Result.failure(
     ProcessNotFoundException(processId)
    )

   AppLogger.i(TAG, "Killing process: $processId (force=$force)")

   if (force) {
    // SIGKILL
    processInfo.process.destroyForcibly()
    AppLogger.d(TAG, "Process forcibly destroyed: $processId")
   } else {
    // SIGTERM - graceful shutdown
    processInfo.process.destroy()

    // Wait up to 10 seconds for graceful shutdown
    val exited = withTimeoutOrNull(10_000) {
     while (processInfo.process.isAlive) {
      delay(100)
     }
     true
    }

    if (exited == null) {
     AppLogger.w(TAG, "Process did not exit gracefully, force killing")
     processInfo.process.destroyForcibly()
    }
   }

   // Wait for exit
   processInfo.process.waitFor()

   // Remove from active processes
   activeProcesses.remove(processId)

   AppLogger.i(TAG, "Process killed successfully: $processId")
   Result.success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to kill process", e)
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

   AppLogger.i(TAG, "Cleanup freed ${bytesFreed / 1024 / 1024}MB")
   Result.success(bytesFreed)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Cleanup failed", e)
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
   val rootfsLibraryPath = buildRootfsLibraryPath()

   put("WINEPREFIX", containerDir.absolutePath)
   put("HOME", containerDir.absolutePath)
   put("TMPDIR", context.cacheDir.absolutePath)
   // CRITICAL: PRoot requires PROOT_TMP_DIR for temporary files
   // Without this, proot fails with "can't create temporary directory"
   put("PROOT_TMP_DIR", context.cacheDir.absolutePath)

   put("LANG", "C")
   put("LC_ALL", "C")

   // WINEDEBUG: Optimized for performance and memory
   // Minimize logging for faster container creation and reduced memory usage
   when (attemptNumber) {
    0 -> put("WINEDEBUG", "-all") // MINIMAL: Complete silence (fastest, lowest memory)
    1 -> put("WINEDEBUG", "+err,+seh,+loaddll,+process") // Core diagnostics
    else -> put("WINEDEBUG", "+all") // MAXIMUM: All Wine debug channels
   }

   put("WINEARCH", "win64") // 64-bit prefix with WoW64 for 32-bit app support (matches Winlator)
   put("WINELOADERNOEXEC", "1")
   // DO NOT set WINESERVER - let Wine find it via PATH
   // Setting WINESERVER causes Box64 nested posix_spawn to fail with ENOENT
   // Wine will search: $WINESERVER -> PATH -> hardcoded paths
   // Box64 auto-wraps posix_spawn, so Wine will find wineserver via PATH
   // put("WINESERVER", wineserverBinary.absolutePath)
   put("WINEFSYNC", "0") // Disable FSync during initialization for stability

   // MEMORY OPTIMIZATION: Disable unnecessary Wine processes
   // explorer.exe: Windows desktop shell (not needed on Android, saves 30-50MB)
   // winemenubuilder.exe: Start menu integration (not needed, saves 10-20MB)
   // Reference: https://wiki.archlinux.org/title/Wine
   put("WINEDLLOVERRIDES", "explorer.exe=d;winemenubuilder.exe=d")

   // Paths
   put("PATH", "${wineDir.absolutePath}/bin:${box64Dir.absolutePath}")
   put(
    "LD_LIBRARY_PATH",
    "${rootfsLibraryPath}:" +
     "${wineDir.absolutePath}/lib:" +
     "${wineDir.absolutePath}/lib/wine/x86_64-unix"
   )
   put("WINEDLLPATH", "${wineDir.absolutePath}/lib/wine/x86_64-windows")

   // Box64 library paths (critical for Android dlopen restrictions)
   // CRITICAL FIX: Include both x86_64 (emulated) AND aarch64 (native) library paths
   // Box64 needs native ARM64 libraries for GLIBC functions (dlerror, perror, etc.)
   put(
    "BOX64_LD_LIBRARY_PATH",
    // x86_64 emulated libraries
    "${rootfsDir.absolutePath}/usr/lib/x86_64-linux-gnu:" +
    "${wineDir.absolutePath}/lib/wine/x86_64-unix:" +
    // ARM64 native libraries (CRITICAL for Box64 native function calls)
    "${rootfsDir.absolutePath}/usr/lib:" +
    "${rootfsDir.absolutePath}/lib:" +
    "${rootfsDir.absolutePath}/usr/lib/aarch64-linux-gnu:" +
    "${rootfsDir.absolutePath}/lib/aarch64-linux-gnu"
   )
   put("BOX64_PATH", "${wineDir.absolutePath}/bin")
   put("BOX64_EMULATED_LIBS", "libc.so.6:libpthread.so.0:libdl.so.2:librt.so.1:libm.so.6")
   put("BOX64_ALLOWMISSINGLIBS", "1")

   // Box64 dynarec settings per attempt - each attempt gets more conservative
   // OPTIMIZED: Start with balanced settings for faster container creation
   when (attemptNumber) {
    0 -> {
     // Attempt 1: BALANCED PERFORMANCE + MEMORY OPTIMIZATION
     // Most containers succeed with these settings in ~30-40s
     put("BOX64_DYNAREC_SAFEFLAGS", "1")  // Moderate flag safety
     put("BOX64_DYNAREC_FASTNAN", "1")   // Fast NaN handling
     put("BOX64_DYNAREC_FASTROUND", "1")  // Fast rounding
     put("BOX64_DYNAREC_X87DOUBLE", "1")  // Accurate x87 FPU emulation
     put("BOX64_DYNAREC_BIGBLOCK", "1")  // REDUCED: Smaller blocks (saves 20-40MB cache)
     put("BOX64_DYNAREC_STRONGMEM", "1")  // Balanced memory ordering
     put("BOX64_DYNAREC_FORWARD", "256")  // Moderate block forward
     put("BOX64_DYNAREC_CALLRET", "1")   // Enable call/ret optimization
     put("BOX64_DYNAREC_WAIT", "0")    // No wait

     // Minimal logging for performance and memory
     put("BOX64_LOG", "1")       // Minimal logging
     put("BOX64_SHOWSEGV", "1")    // Show segfault details
     put("BOX64_NOBANNER", "1")    // Hide banner for speed
     put("BOX64_DYNACACHE", "0")   // Disable disk cache (avoid I/O overhead)
    }
    1 -> {
     // Attempt 2: MAXIMUM STABILITY + DIAGNOSTIC MODE
     // Fallback for problematic systems
     put("BOX64_DYNAREC_SAFEFLAGS", "2")  // Full flag safety
     put("BOX64_DYNAREC_FASTNAN", "0")   // Accurate NaN handling
     put("BOX64_DYNAREC_FASTROUND", "0")  // Accurate rounding
     put("BOX64_DYNAREC_X87DOUBLE", "1")  // Accurate x87 FPU emulation
     put("BOX64_DYNAREC_BIGBLOCK", "0")  // Disable large block compilation
     put("BOX64_DYNAREC_STRONGMEM", "2")  // Maximum memory ordering strength
     put("BOX64_DYNAREC_FORWARD", "128")  // Conservative block forward
     put("BOX64_DYNAREC_CALLRET", "0")   // Disable call/ret optimization
     put("BOX64_DYNAREC_WAIT", "0")    // Winlator STABILITY setting

     // Enhanced diagnostic mode
     put("BOX64_TRACE", "1")      // Instruction-level tracing
     put("BOX64_PREFER_EMULATED", "1")  // Prefer emulated libraries
     put("BOX64_CRASHHANDLER", "0")   // Emulated crash handler
     put("BOX64_SHOWBT", "1")      // Show backtrace

     put("BOX64_LOG", "3")       // Maximum logging
     put("BOX64_SHOWSEGV", "1")     // Show segfault details
     put("BOX64_NOBANNER", "0")
    }
    else -> {
     // Attempt 3: DISABLE DYNAREC + FULL DIAGNOSTICS
     // Falls back to pure interpretation - ~10x slower but eliminates JIT bugs
     put("BOX64_DYNAREC", "0")     // COMPLETE DISABLE

     // Maximum diagnostic mode
     put("BOX64_TRACE", "1")      // Instruction tracing
     put("BOX64_PREFER_EMULATED", "1")  // Force emulated libraries
     put("BOX64_CRASHHANDLER", "0")   // Emulated crash handler
     put("BOX64_SHOWBT", "1")      // Backtrace on signals

     put("BOX64_LOG", "3")       // Maximum logging
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
      AppLogger.d(TAG, "Cleaned up partial registry: $regFile")
     } else {
      AppLogger.w(TAG, "Failed to delete partial registry: $regFile")
     }
    }
   }

   // Clean update timestamp
   File(containerDir, ".update-timestamp").apply {
    if (exists()) {
     val deleted = delete()
     if (deleted) {
      AppLogger.d(TAG, "Cleaned up .update-timestamp")
     }
    }
   }
  } catch (e: Exception) {
   AppLogger.w(TAG, "Failed to cleanup partial prefix", e)
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
   AppLogger.w(TAG, "wineboot is not an ELF binary, falling back to wineboot.exe")
  }

  val linker = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")
  if (!linker.exists()) {
   return@withContext Result.failure(
    EmulatorException("Rootfs linker not found: ${linker.absolutePath}")
   )
  }

  // CRITICAL: Verify required library directories exist
  // Missing libraries cause Box64 to fail with "quit=1" errors
  val requiredLibDirs = listOf(
   File(rootfsDir, "usr/lib"),
   File(rootfsDir, "lib"),
   File(rootfsDir, "usr/lib/aarch64-linux-gnu"),
   File(rootfsDir, "usr/lib/x86_64-linux-gnu")
  )
  requiredLibDirs.forEach { dir ->
   if (!dir.exists()) {
    AppLogger.w(TAG, "Missing library directory: ${dir.absolutePath}")
    AppLogger.w(TAG, "Box64 may fail to load native libraries (glibc, etc.)")
   } else {
    AppLogger.d(TAG, "Verified library directory exists: ${dir.absolutePath}")
   }
  }

  AppLogger.i(TAG, "Initializing Wine prefix: ${containerDir.absolutePath}")
  AppLogger.i(TAG, "Using linker: ${linker.absolutePath}")
  AppLogger.i(TAG, "Using box64: ${box64ToUse.absolutePath}")
  AppLogger.i(TAG, "Using wine: ${wineBinary.absolutePath}")
  AppLogger.i(
   TAG,
   "Using wineboot: ${if (useWinebootBinary) winebootBinary.absolutePath else winebootExe.absolutePath}"
  )
  val rootfsLibraryPath = buildRootfsLibraryPath()

  // Build command (same for all attempts)
  // CRITICAL FIX: Wrap with proot to virtualize hardcoded Wine paths
  // Wine/Wineserver binaries have hardcoded paths: /data/data/com.winlator/files/rootfs/tmp
  // proot binds our actual rootfs to the hardcoded path location
  // This allows Wine to access /data/data/com.winlator/... without permission errors
  //
  // CRITICAL: Do NOT pass linker as proot argument
  // Box64 binary already has PT_INTERP set to linker path - ELF loader handles it automatically
  // Passing linker to proot causes proot to try executing ld-linux directly → SIGSEGV
  val command = if (useWinebootBinary) {
   listOf(
    prootBinary.absolutePath,
    "-b", "${rootfsDir.absolutePath}:/data/data/com.winlator/files/rootfs",
    box64ToUse.absolutePath,  // ✅ Execute Box64 directly (PT_INTERP handles linker)
    winebootBinary.absolutePath,
    "-i"
   )
  } else {
   // CRITICAL FIX: wineboot.exe must be invoked as a Windows executable
   // Use Windows-style path: C:\windows\system32\wineboot.exe
   // Wine will map this to the correct location in its virtual filesystem
   listOf(
    prootBinary.absolutePath,
    "-b", "${rootfsDir.absolutePath}:/data/data/com.winlator/files/rootfs",
    box64ToUse.absolutePath,  // ✅ Execute Box64 directly (PT_INTERP handles linker)
    wineBinary.absolutePath,
    "C:\\\\windows\\\\system32\\\\wineboot.exe",
    "--init"
   )
  }

  // CRITICAL: Pre-start wineserver via Box64 before running wineboot
  // Wine tries to spawn wineserver directly via posix_spawn, which fails because
  // wineserver is an x86_64 binary and needs Box64 to run
  //
  // Research findings (Box64 Issue #208, #154):
  // - Box64 automatically wraps posix_spawn() calls with itself as interpreter
  // - Spawned wineserver subprocess needs all env vars (BOX64_*, LD_LIBRARY_PATH)
  // - Must wait for wineserver socket to be created before launching wineboot
  try {
   AppLogger.i(TAG, "Pre-starting wineserver via Box64...")
   // CRITICAL: Wrap wineserver with proot for hardcoded path virtualization
   // CRITICAL: Do NOT pass linker as proot argument
   // Box64 binary already has PT_INTERP set to linker path - ELF loader handles it automatically
   // Passing linker to proot causes proot to try executing ld-linux directly → SIGSEGV
   val wineserverCmd = listOf(
    prootBinary.absolutePath,
    "-b", "${rootfsDir.absolutePath}:/data/data/com.winlator/files/rootfs",
    box64ToUse.absolutePath,  // ✅ Execute Box64 directly (PT_INTERP handles linker)
    wineserverBinary.absolutePath,
    "-p0"  // Persistent mode
   )
   val wineserverEnv = buildWinebootEnvironmentVariables(containerDir, 0).toMutableMap()

   // Add extra logging for subprocess debugging
   wineserverEnv["BOX64_TRACE_INIT"] = "1"
   wineserverEnv["BOX64_LOG"] = "3"
   // CRITICAL: Set LD_LIBRARY_PATH via environment (not as proot argument)
   wineserverEnv["LD_LIBRARY_PATH"] = rootfsLibraryPath

   val wineserverProcess = ProcessBuilder(wineserverCmd).apply {
    environment().clear()
    environment().putAll(wineserverEnv)
    redirectErrorStream(true)
   }.start()

   // CRITICAL FIX: Drain wineserver output to prevent buffer overflow deadlock
   // Launch background coroutine to continuously read output
   // BEST PRACTICE: Use simple reading loop instead of forEach
   CoroutineScope(Dispatchers.IO).launch {
    try {
     wineserverProcess.inputStream.bufferedReader().use { reader ->
      // Use while loop to continuously read until EOF (process termination)
      while (true) {
       val line = reader.readLine() ?: break  // EOF reached, process terminated
       AppLogger.d(TAG, "[wineserver] $line")
      }
     }
    } catch (e: Exception) {
     // Stream closed or read interrupted - this is expected on process termination
     AppLogger.d(TAG, "[wineserver] Output reading stopped: ${e.message}")
    }
   }

   // Wait for wineserver socket to be created (up to 2 seconds - optimized)
   val wineServerDir = File(containerDir, ".wine")
   var wineserverReady = false
   for (attempt in 0 until WINESERVER_SOCKET_POLL_ATTEMPTS) {
    delay(WINESERVER_SOCKET_POLL_DELAY_MS)
    // Check if .wine directory exists and has server-* subdirectory with socket
    if (wineServerDir.exists()) {
     wineServerDir.listFiles()?.forEach { file ->
      if (file.isDirectory && file.name.startsWith("server-")) {
       val socket = File(file, "socket")
       if (socket.exists()) {
        wineserverReady = true
        AppLogger.i(TAG, "Wineserver socket found: ${socket.absolutePath}")
        return@forEach
       }
      }
     }
    }
    if (wineserverReady) break
   }

   if (wineserverReady) {
    AppLogger.i(TAG, "Wineserver pre-started successfully")
   } else {
    AppLogger.w(TAG, "Wineserver socket not found after 5s, continuing anyway...")
   }
  } catch (e: Exception) {
   AppLogger.w(TAG, "Failed to pre-start wineserver: ${e.message}")
   // Continue anyway - wineboot might still work
  }

  // Multi-tier retry loop with progressive timeout increase
  for (attemptNumber in 0 until MAX_RETRY_ATTEMPTS) {
   try {
    AppLogger.i(TAG, "=== Wine prefix initialization attempt ${attemptNumber + 1}/$MAX_RETRY_ATTEMPTS ===")

    // Build environment variables specific to this attempt
    val environmentVars = buildWinebootEnvironmentVariables(containerDir, attemptNumber)

    // Log key settings for this attempt
    AppLogger.d(TAG, "Attempt $attemptNumber settings:")
    AppLogger.d(TAG, " WINEDEBUG=${environmentVars["WINEDEBUG"]}")
    AppLogger.d(TAG, " BOX64_DYNAREC=${environmentVars["BOX64_DYNAREC"] ?: "enabled"}")
    AppLogger.d(TAG, " BOX64_DYNAREC_SAFEFLAGS=${environmentVars["BOX64_DYNAREC_SAFEFLAGS"] ?: "default"}")
    AppLogger.d(TAG, " BOX64_DYNAREC_STRONGMEM=${environmentVars["BOX64_DYNAREC_STRONGMEM"] ?: "default"}")

    val processBuilder = ProcessBuilder(command)
    val env = processBuilder.environment()
    env.clear()
    env.putAll(environmentVars)
    // CRITICAL: Set LD_LIBRARY_PATH via environment (not as proot argument)
    env["LD_LIBRARY_PATH"] = rootfsLibraryPath
    processBuilder.redirectErrorStream(true)

    AppLogger.d(TAG, "Running: ${command.joinToString(" ")}")

    val process = processBuilder.start()

    // Timeout: Optimized for faster container creation
    // First attempt uses balanced timeout, retries use more conservative timeout
    // Most wineboot --init completes within 30-45s on modern devices
    val timeoutMs = if (attemptNumber == 0) WINEBOOT_TIMEOUT_FIRST_MS else WINEBOOT_TIMEOUT_RETRY_MS

    // Execute with proper output draining (prevents deadlock)
    val result = executeProcessWithOutputDrain(
     process = process,
     timeoutMs = timeoutMs,
     logPrefix = "[wineboot]"
    )
    val exitCode = result.exitCode
    val output = result.output

    if (exitCode == null) {
     // Timeout occurred (process already destroyed in finally block)
     AppLogger.w(TAG, "Attempt $attemptNumber: wineboot timed out after ${timeoutMs / 1000}s")

     // If last attempt, fail
     if (attemptNumber == MAX_RETRY_ATTEMPTS - 1) {
      return@withContext Result.failure(
       WinePrefixException("Wine prefix initialization timed out on all $MAX_RETRY_ATTEMPTS attempts")
      )
     }

     // Cleanup and retry with exponential backoff
     cleanupPartialPrefix(containerDir)
     val backoffMs = (attemptNumber + 1) * RETRY_BACKOFF_MS
     AppLogger.i(TAG, "Waiting ${backoffMs / 1000}s before retry...")
     delay(backoffMs)
     continue
    }

    if (exitCode == 0) {
     // Exit code 0, but verify Wine prefix is actually complete
     // Check for critical registry files (Wine best practice)
     val systemReg = File(containerDir, "system.reg")
     val userReg = File(containerDir, "user.reg")
     val userdefReg = File(containerDir, "userdef.reg")

     // Wait up to 5 seconds for registry files to be created
     var registryCheckRetries = 0
     val maxRegistryRetries = 10 // 10 x 500ms = 5 seconds
     while (registryCheckRetries < maxRegistryRetries) {
      if (systemReg.exists() && userReg.exists() && userdefReg.exists()) {
       AppLogger.i(TAG, "Wine prefix initialized successfully on attempt ${attemptNumber + 1}")
       AppLogger.d(TAG, "Verified: system.reg, user.reg, userdef.reg all present")
       return@withContext Result.success(Unit)
      }

      if (registryCheckRetries == 0) {
       AppLogger.i(TAG, "Registry files not yet created, waiting...")
      }
      delay(500)
      registryCheckRetries++
     }

     // Registry files not created despite exit code 0
     AppLogger.w(TAG, "wineboot returned 0 but registry files missing after ${maxRegistryRetries * 500 / 1000}s")
     AppLogger.w(TAG, "  system.reg exists: ${systemReg.exists()}")
     AppLogger.w(TAG, "  user.reg exists: ${userReg.exists()}")
     AppLogger.w(TAG, "  userdef.reg exists: ${userdefReg.exists()}")

     // Check output for c0000135 error (DLL not found)
     val hasC0000135 = output.toString().contains("c0000135", ignoreCase = true)
     if (hasC0000135) {
      AppLogger.e(TAG, "Detected c0000135 error (DLL not found) - Wine prefix initialization failed")
     }

     // Treat as failure and retry
     if (attemptNumber < MAX_RETRY_ATTEMPTS - 1) {
      cleanupPartialPrefix(containerDir)
      val backoffMs = (attemptNumber + 1) * RETRY_BACKOFF_MS
      AppLogger.i(TAG, "Retrying after ${backoffMs / 1000}s...")
      delay(backoffMs)
      continue
     } else {
      return@withContext Result.failure(
       WinePrefixException("Wine prefix incomplete: registry files not created. Possible c0000135 error.")
      )
     }
    }

    // Failed with non-zero exit code
    val outputTail = output.takeLast(500).toString()
    AppLogger.w(TAG, "Attempt $attemptNumber: wineboot exited with code $exitCode")
    AppLogger.d(TAG, "Output (last 500 chars): $outputTail")

    // Special handling for SIGSEGV (exit code 139)
    if (exitCode == 139) {
     AppLogger.e(TAG, "Attempt $attemptNumber: SIGSEGV detected (exit 139)")
    }

    // If last attempt, fail
    if (attemptNumber == MAX_RETRY_ATTEMPTS - 1) {
     return@withContext Result.failure(
      WinePrefixException("wineboot failed on all $MAX_RETRY_ATTEMPTS attempts. Last exit code: $exitCode. Output: $outputTail")
     )
    }

    // Cleanup and retry
    cleanupPartialPrefix(containerDir)
    val backoffMs = (attemptNumber + 1) * RETRY_BACKOFF_MS
    AppLogger.i(TAG, "Waiting ${backoffMs / 1000}s before retry...")
    delay(backoffMs)

   } catch (e: Exception) {
    AppLogger.e(TAG, "Attempt $attemptNumber: Exception during Wine prefix initialization", e)

    // If last attempt, fail
    if (attemptNumber == MAX_RETRY_ATTEMPTS - 1) {
     return@withContext Result.failure(
      WinePrefixException("Failed to initialize Wine prefix after $MAX_RETRY_ATTEMPTS attempts: ${e.message}", e)
     )
    }

    // Cleanup and retry
    cleanupPartialPrefix(containerDir)
    val backoffMs = (attemptNumber + 1) * RETRY_BACKOFF_MS
    AppLogger.i(TAG, "Waiting ${backoffMs / 1000}s before retry...")
    delay(backoffMs)
   }
  }

  // Should never reach here, but safety fallback
  return@withContext Result.failure(
   WinePrefixException("Wine prefix initialization failed - all retry attempts exhausted")
  )
 }

 /**
  * Run wineboot with -u flag to update Wine prefix and load services
  *
  * This is equivalent to Winlator's "Normal (Load all services)" startup mode.
  * Recommended for Steam to ensure all Windows services are available.
  *
  * @param containerDir Wine prefix directory
  * @return Result indicating success or failure (non-fatal)
  */
 private suspend fun runWinebootUpdate(containerDir: File): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Running wineboot -u to load Windows services...")

   val command = buildList {
    add(prootBinary.absolutePath)
    add("-b")
    add("${rootfsDir.absolutePath}:/data/data/com.winlator/files/rootfs")
    add(box64Binary.absolutePath)
    add(File(wineDir, "bin/wine").absolutePath)
    add("C:\\windows\\system32\\wineboot.exe")
    add("-u")  // Update mode - loads services
   }

   val env = buildWinebootEnvironmentVariables(containerDir)
   val process = ProcessBuilder(command)
    .directory(File("/"))
    .apply {
     environment().putAll(env)
    }
    .start()

   val output = StringBuilder()
   process.inputStream.bufferedReader().forEachLine { line ->
    output.appendLine(line)
    AppLogger.d(TAG, "[wineboot -u] $line")
   }

   val exitCode = process.waitFor()
   AppLogger.i(TAG, "wineboot -u completed with exit code: $exitCode")

   if (exitCode == 0) {
    Result.success(Unit)
   } else {
    Result.failure(EmulatorException("wineboot -u failed with exit code $exitCode"))
   }
  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to run wineboot -u", e)
   Result.failure(e)
  }
 }

 /**
  * Set Windows version to Windows 10 via Wine registry
  *
  * CRITICAL: Steam requires Windows 10/11 to run properly.
  * This configures Wine to report as Windows 10 (same as Winlator 10.1).
  *
  * @param containerDir Wine prefix directory
  * @return Result indicating success or failure
  */
 private suspend fun setWindowsVersion(containerDir: File): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Setting Windows version to Windows 10")

   // Create .reg file with Windows 10 configuration
   val regFile = File(context.cacheDir, "set_windows_version.reg")
   regFile.writeText("""
REGEDIT4

[HKEY_LOCAL_MACHINE\Software\Microsoft\Windows NT\CurrentVersion]
"CurrentVersion"="10.0"
"CurrentBuild"="19045"
"CurrentBuildNumber"="19045"
"ProductName"="Windows 10 Pro"

[HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\ProductOptions]
"ProductType"="WinNT"

[HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Windows]
"CSDVersion"=dword:00000000

   """.trimIndent())

   // Build Wine environment
   val wineEnv = buildMap {
    put("WINEPREFIX", containerDir.absolutePath)
    put("WINEARCH", "win64") // 64-bit prefix with WoW64 for 32-bit app support (matches Winlator)
    put("WINEDEBUG", "-all")
   }

   // Get Box64 binary
   val box64Binary = getBox64Binary()
   if (!box64Binary.exists()) {
    regFile.delete()
    return@withContext Result.failure(
     Box64BinaryNotFoundException()
    )
   }

   if (!wineBinary.exists()) {
    regFile.delete()
    return@withContext Result.failure(
     WineBinaryNotFoundException()
    )
   }

   // Verify proot binary exists (CRITICAL: required for proper library loading)
   if (!prootBinary.exists()) {
    regFile.delete()
    return@withContext Result.failure(
     EmulatorException("Proot binary not found at ${prootBinary.absolutePath}")
    )
   }

   // Execute: proot wine regedit /S set_windows_version.reg
   // IMPORTANT: Use proot to properly mount rootfs and load system libraries (libc.so.6, etc.)
   val command = buildList {
    add(prootBinary.absolutePath)
    add("-b")
    add("${rootfsDir.absolutePath}:/data/data/com.winlator/files/rootfs")
    add(box64Binary.absolutePath)
    add(wineBinary.absolutePath)
    add("regedit")
    add("/S") // Silent mode
    add(regFile.absolutePath)
   }

   AppLogger.d(TAG, "Executing regedit command: ${command.joinToString(" ")}")

   val processBuilder = ProcessBuilder(command)

   // Add Wine environment with library paths
   // CRITICAL: Include all required paths for Box64 to find native libraries
   val fullEnv = wineEnv.toMutableMap().apply {
    put("PATH", "${wineDir.absolutePath}/bin:${box64Dir.absolutePath}:/usr/bin:/bin")
    put("LD_LIBRARY_PATH",
     "${rootfsDir.absolutePath}/usr/lib:" +
     "${rootfsDir.absolutePath}/lib:" +
     "${wineDir.absolutePath}/lib:" +
     "${wineDir.absolutePath}/lib/wine/x86_64-unix"
    )
    put("BOX64_LD_LIBRARY_PATH",
     "${rootfsDir.absolutePath}/usr/lib:" +
     "${rootfsDir.absolutePath}/lib:" +
     "${rootfsDir.absolutePath}/usr/lib/x86_64-linux-gnu:" +
     "${wineDir.absolutePath}/lib/wine/x86_64-unix"
    )
    put("BOX64_PATH", "${wineDir.absolutePath}/bin")
    put("BOX64_EMULATED_LIBS", "libc.so.6:libpthread.so.0:libdl.so.2:librt.so.1:libm.so.6")
    put("BOX64_ALLOWMISSINGLIBS", "1")
    put("BOX64_LOG", "0")
    put("BOX64_NOBANNER", "1")
    put("TMPDIR", context.cacheDir.absolutePath)
    put("PROOT_TMP_DIR", context.cacheDir.absolutePath)

    // CRITICAL FIX: Android 10+ Seccomp workaround
    // Android 10+ blocks ptrace/execve/chmod/chdir syscalls via Seccomp filter
    // Setting PROOT_NO_SECCOMP=1 tells proot to ignore Seccomp events
    put("PROOT_NO_SECCOMP", "1")
   }

   processBuilder.environment().putAll(fullEnv)
   processBuilder.redirectErrorStream(true)

   val process = processBuilder.start()

   // Wait for regedit to complete (max 30 seconds)
   // IMPORTANT: proot adds overhead, so longer timeout needed
   val completed = withTimeoutOrNull(30_000L) {
    process.waitFor()
    true
   } ?: false

   // Read output after process completes (or times out)
   val output = try {
    process.inputStream.bufferedReader().readText()
   } catch (e: Exception) {
    "Failed to read output: ${e.message}"
   }

   if (!completed) {
    process.destroy()
    regFile.delete()
    AppLogger.e(TAG, "regedit timeout after 30s. Output: $output")
    return@withContext Result.failure(
     EmulatorException("regedit timeout after 30 seconds")
    )
   }

   val exitCode = process.exitValue()
   AppLogger.d(TAG, "regedit exit code: $exitCode")
   AppLogger.d(TAG, "regedit output: $output")

   regFile.delete()

   if (exitCode != 0) {
    return@withContext Result.failure(
     EmulatorException("regedit failed with exit code $exitCode: $output")
    )
   }

   AppLogger.i(TAG, "Successfully configured Wine to report as Windows 10")
   Result.success(Unit)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to set Windows version", e)
   Result.failure(EmulatorException("Failed to set Windows version: ${e.message}", e))
  }
 }

 /**
  * Set Windows version to Windows 10 by directly editing system.reg
  *
  * This method uses the Winlator approach: directly editing the registry file
  * instead of using `wine regedit`, which can crash with proot (SIGSEGV).
  *
  * @param containerDir Wine prefix directory
  * @return true if successful, false otherwise
  */
 private suspend fun setWindowsVersionDirect(containerDir: File): Boolean = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Setting Windows version to Windows 10 (direct registry edit)")

   val systemRegFile = File(containerDir, "system.reg")

   // Wait for system.reg to be created by wineserver (up to 10 seconds)
   var retries = 0
   val maxRetries = 20  // 20 x 500ms = 10 seconds
   while (!systemRegFile.exists() && retries < maxRetries) {
    if (retries == 0) {
     AppLogger.i(TAG, "Waiting for system.reg to be created by wineserver...")
    }
    delay(500)
    retries++
   }

   if (!systemRegFile.exists()) {
    AppLogger.e(TAG, "system.reg not found after ${maxRetries * 500 / 1000}s: ${systemRegFile.absolutePath}")
    return@withContext false
   }

   AppLogger.i(TAG, "Found system.reg after ${retries * 500}ms")

   // Read system.reg
   val lines = systemRegFile.readLines().toMutableList()
   AppLogger.d(TAG, "Read ${lines.size} lines from system.reg")

   // Find the CurrentVersion section (or a suitable insertion point)
   // Wine registry file uses single backslashes (escaped as \\ in Kotlin string)
   var currentVersionIndex = -1
   var insertionPoint = -1

   for (i in lines.indices) {
    val line = lines[i]

    // Debug: Log lines that look like CurrentVersion sections
    if (line.contains("CurrentVersion", ignoreCase = true) && line.startsWith("[")) {
     AppLogger.d(TAG, "Line $i contains CurrentVersion: $line")
    }

    // Check if the exact section already exists
    // Registry file format: [Software\\Microsoft\\...] with DOUBLE backslashes
    if (line.startsWith("[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion]")) {
     currentVersionIndex = i
     AppLogger.d(TAG, "Found CurrentVersion section at line $i")
     break
    }
    // Find a subsection like FontLink\SystemLink as insertion point
    if (insertionPoint == -1 && line.startsWith("[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\")) {
     insertionPoint = i
     AppLogger.i(TAG, "Found CurrentVersion subsection at line $i: $line")
    }
   }

   AppLogger.d(TAG, "Search complete: currentVersionIndex=$currentVersionIndex, insertionPoint=$insertionPoint")

   // If the section doesn't exist, create it before the first subsection
   if (currentVersionIndex == -1) {
    if (insertionPoint == -1) {
     AppLogger.e(TAG, "Cannot find suitable insertion point for CurrentVersion section")
     AppLogger.e(TAG, "First 50 lines of system.reg:")
     lines.take(50).forEachIndexed { idx, line ->
      AppLogger.e(TAG, "  $idx: $line")
     }
     return@withContext false
    }

    AppLogger.i(TAG, "CurrentVersion section not found, creating new section at line $insertionPoint")
    // Insert new section with timestamp
    // Use double backslashes to match Wine registry format
    val timestamp = System.currentTimeMillis()
    lines.add(insertionPoint, "")
    lines.add(insertionPoint + 1, "[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion] $timestamp")
    lines.add(insertionPoint + 2, "#time=1dc71f780aa601e")
    currentVersionIndex = insertionPoint + 1
   }

   // Remove existing Windows version settings to avoid duplicates
   var insertIndex = currentVersionIndex + 1
   val keysToRemove = setOf("CurrentVersion", "CurrentBuild", "CurrentBuildNumber", "ProductName")

   while (insertIndex < lines.size && !lines[insertIndex].startsWith("[")) {
    val line = lines[insertIndex]
    if (line.isNotBlank() && keysToRemove.any { key -> line.contains("\"$key\"") }) {
     AppLogger.d(TAG, "Removing existing registry entry: ${line.trim()}")
     lines.removeAt(insertIndex)
    } else {
     insertIndex++
    }
   }

   // Insert Windows 10 settings
   // Wine registry format: "KeyName"="Value" for strings, "KeyName"=dword:VALUE for DWORD
   val newSettings = listOf(
    "\"CurrentVersion\"=\"10.0\"",
    "\"CurrentBuild\"=\"19045\"",
    "\"CurrentBuildNumber\"=\"19045\"",
    "\"ProductName\"=\"Windows 10 Pro\""
   )

   insertIndex = currentVersionIndex + 1
   newSettings.forEach { setting ->
    lines.add(insertIndex++, setting)
    AppLogger.d(TAG, "Added registry entry: $setting")
   }

   // Write back to file
   systemRegFile.writeText(lines.joinToString("\n"))

   AppLogger.i(TAG, "Successfully configured Wine to report as Windows 10 (direct edit)")
   return@withContext true

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to set Windows version via direct edit", e)
   return@withContext false
  }
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
   AppLogger.w(TAG, "Failed to read process metrics for PID $pid", e)
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
  val rootfsLibraryPath = buildRootfsLibraryPath()

  return buildMap {
   // Wine basic configuration
   put("WINEPREFIX", winePrefix.absolutePath)
   put("HOME", winePrefix.absolutePath)
   put("TMPDIR", context.cacheDir.absolutePath)
   // CRITICAL: PRoot requires PROOT_TMP_DIR for temporary files
   // Without this, proot fails with "can't create temporary directory"
   put("PROOT_TMP_DIR", context.cacheDir.absolutePath)

   put("LANG", "C")
   put("LC_ALL", "C")
   // CRITICAL: Use minimal logging to prevent SIGSEGV crashes
   // Excessive logging (+all,+relay,+file) destabilizes Wine execution
   put("WINEDEBUG", "+err,+process,+loaddll") // DEBUG: Enable Wine error/process/dll logs
   put("WINEARCH", "win64") // 64-bit prefix with WoW64 for 32-bit app support (matches Winlator)
   put("WINELOADERNOEXEC", "1")
   // DO NOT set WINESERVER - let Wine find it via PATH (same reason as wineboot)
   // put("WINESERVER", wineserverBinary.absolutePath)

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
   put("BOX64_LOG", "2") // DEBUG: Enable Box64 logging (0=none, 1=info, 2=debug, 3=verbose)
   put("BOX64_NOBANNER", "0") // DEBUG: Show Box64 banner

   // Display configuration
   put("DISPLAY", ":0")
   // CRITICAL: XAUTHORITY is required for Wine X11 authentication (even for local connections)
   // Without this, Wine's libX11 will fail to connect to XServer silently
   // Path must be in PRoot chroot namespace: /tmp/.Xauthority (maps to rootfs/tmp/.Xauthority)
   put("XAUTHORITY", "/tmp/.Xauthority")
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

   // CRITICAL: WINEARCH must be set AFTER customEnvVars to ensure it's never overridden
   // Wine 9.2+ WoW64 mode requires win64 architecture (32-bit apps run via WoW64 layer)
   // DO NOT allow customEnvVars to override this - it would break WoW64 mode
   put("WINEARCH", "win64")

   // Add system paths
   val existingPath = System.getenv("PATH") ?: ""
   put("PATH", "${wineDir.absolutePath}/bin:${box64Dir.absolutePath}:$existingPath")

   // Set LD_LIBRARY_PATH for Box64/Wine loader (glibc from rootfs)
   put(
    "LD_LIBRARY_PATH",
    "${rootfsLibraryPath}:" +
     "${wineDir.absolutePath}/lib:" +
     "${wineDir.absolutePath}/lib/wine/x86_64-unix"
   )

   // Set Wine DLL path for Windows libraries
   put("WINEDLLPATH", "${wineDir.absolutePath}/lib/wine/x86_64-windows")

   // Add Box64 library paths for better compatibility
   put(
    "BOX64_LD_LIBRARY_PATH",
    "${rootfsLibraryPath}:${rootfsDir.absolutePath}/usr/lib/x86_64-linux-gnu:${wineDir.absolutePath}/lib/wine/x86_64-unix"
   )
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
   AppLogger.w(TAG, "Failed to get PID via reflection, using fallback", e)
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
  * Builds a stable library path for the rootfs linker.
  *
  * The rootfs in this project stores glibc in usr/lib, not /lib.
  * Using an explicit path avoids reliance on LD_LIBRARY_PATH behavior
  * across Android versions.
  */
 private fun buildRootfsLibraryPath(): String {
  val candidates = listOf(
   File(rootfsDir, "usr/lib"),
   File(rootfsDir, "lib")
  )
  val existing = candidates.filter { it.exists() }.map { it.absolutePath }
  return if (existing.isNotEmpty()) {
   existing.joinToString(":")
  } else {
   "${rootfsDir.absolutePath}/usr/lib:${rootfsDir.absolutePath}/lib"
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
  * Result of process execution with output draining
  */
 private data class ProcessExecutionResult(
  val exitCode: Int?,
  val output: StringBuilder
 )

 /**
  * Execute a process with proper output draining to prevent deadlocks.
  *
  * Best Practice: Drains stdout/stderr concurrently to prevent buffer overflow.
  * When the output buffer fills up, the process blocks waiting for a reader.
  *
  * @param process The process to execute
  * @param timeoutMs Timeout in milliseconds
  * @param logPrefix Prefix for log messages (e.g., "[wineboot]")
  * @return ProcessExecutionResult with exit code and captured output
  */
 private suspend fun executeProcessWithOutputDrain(
  process: Process,
  timeoutMs: Long,
  logPrefix: String = ""
 ): ProcessExecutionResult = withContext(Dispatchers.IO) {
  val output = StringBuilder()

  val exitCode = try {
   withTimeoutOrNull(timeoutMs) {
    // Launch concurrent output reader to drain stdout/stderr
    val outputJob = async(Dispatchers.IO) {
     process.inputStream.bufferedReader().use { reader ->
      try {
       // Read until EOF or job is cancelled
       while (true) {
        val line = reader.readLine() ?: break  // EOF reached
        if (logPrefix.isNotEmpty()) {
         AppLogger.d(TAG, "$logPrefix $line")
        }
        output.appendLine(line)
       }
      } catch (e: Exception) {
       // Stream closed or read interrupted - expected on cancellation
       AppLogger.d(TAG, "$logPrefix Output reading stopped: ${e.message}")
      }
     }
    }

    // Wait for process completion
    val code = process.waitFor()

    // Close input stream to unblock the reader
    try {
     process.inputStream.close()
    } catch (e: Exception) {
     AppLogger.d(TAG, "Failed to close input stream: ${e.message}")
    }

    // Wait for output reading to complete (with timeout)
    withTimeoutOrNull(OUTPUT_DRAIN_TIMEOUT_MS) {
     outputJob.await()
    } ?: run {
     AppLogger.w(TAG, "Output reading didn't complete within ${OUTPUT_DRAIN_TIMEOUT_MS / 1000}s, cancelling...")
     outputJob.cancel()
    }

    code
   }
  } finally {
   // Ensure process is cleaned up
   if (process.isAlive) {
    AppLogger.w(TAG, "Process still alive after timeout, destroying forcibly")
    process.destroyForcibly()
   }
  }

  ProcessExecutionResult(exitCode, output)
 }

 /**
  * Setup hardcoded linker path for Box64.
  * Box64 has hardcoded interpreter: /data/data/com.winlator/files/rootfs/lib/ld-linux-aarch64.so.1
  *
  * Since the path in our app is too long to patch, we create a symlink at rootfs/lib/ld-linux-aarch64.so.1
  * that points to the actual linker at rootfs/usr/lib/ld-linux-aarch64.so.1.
  */
 private suspend fun setupHardcodedLinkerPath() = withContext(Dispatchers.IO) {
  // The actual linker is at usr/lib/ld-linux-aarch64.so.1
  val actualLinker = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")

  if (!actualLinker.exists()) {
   AppLogger.w(TAG, "Rootfs linker not found: ${actualLinker.absolutePath}")
   return@withContext
  }

  // Create the lib directory if it doesn't exist
  val libDir = File(rootfsDir, "lib")
  if (!libDir.exists()) {
   libDir.mkdirs()
   AppLogger.d(TAG, "Created lib directory: ${libDir.absolutePath}")
  }

  // Create symlink at lib/ld-linux-aarch64.so.1 -> ../usr/lib/ld-linux-aarch64.so.1
  val linkerSymlink = File(libDir, "ld-linux-aarch64.so.1")
  try {
   if (!linkerSymlink.exists()) {
    // Use relative path for symlink
    val symlinkTarget = "../usr/lib/ld-linux-aarch64.so.1"
    val process = ProcessBuilder("ln", "-s", symlinkTarget, linkerSymlink.absolutePath)
     .redirectErrorStream(true)
     .start()

    // Execute with proper output draining (prevents deadlock)
    val result = executeProcessWithOutputDrain(
     process = process,
     timeoutMs = SYMLINK_CREATION_TIMEOUT_MS,
     logPrefix = "[ln]"
    )

    when (val exitCode = result.exitCode) {
     null -> AppLogger.w(TAG, "Linker symlink creation timed out")
     0 -> AppLogger.i(TAG, "Created linker symlink: ${linkerSymlink.absolutePath} -> $symlinkTarget")
     else -> AppLogger.w(TAG, "Failed to create linker symlink (exit code $exitCode)")
    }
   } else {
    AppLogger.d(TAG, "Linker symlink already exists: ${linkerSymlink.absolutePath}")
   }
  } catch (e: Exception) {
   AppLogger.w(TAG, "Could not create linker symlink: ${e.message}")
  }
 }

 /**
  * Setup library symlinks for Wine/Box64 compatibility
  *
  * Problem: Wine+Box64 looks for libfreetype.so.6 for font rendering
  * Rootfs has libfreetype.so.6.20.2 but no .so.6 symlink
  * Result: "Wine cannot find the FreeType font library" → Steam installer GUI fails
  *
  * Solution: Create symlinks for versioned libraries (.so.6 → .so.6.x.x)
  */
 private fun setupLibrarySymlinks() {
  val libDir = File(rootfsDir, "usr/lib")
  if (!libDir.exists()) {
   AppLogger.w(TAG, "Library directory not found: ${libDir.absolutePath}")
   return
  }

  try {
   // Create symlinks for versioned libraries
   // Format: library.so -> library.so.X.Y.Z, library.so.X -> library.so.X.Y.Z
   val libraries = listOf(
    Triple("libfreetype.so.6.20.2", "libfreetype.so.6", "libfreetype.so"),
    Triple("libz.so.1.3.1", "libz.so.1", "libz.so"),
    Triple("libzstd.so.1.5.6", "libzstd.so.1", "libzstd.so"),
    Triple("libpng16.so.16.44.0", "libpng16.so.16", "libpng16.so"),
    Triple("libpng16.so.16.44.0", "libpng.so", null), // libpng.so -> libpng16.so.16.44.0
    Triple("libbz2.so.1.0.8", "libbz2.so.1.0", "libbz2.so.1"),
    Triple("libbz2.so.1.0.8", "libbz2.so", null), // libbz2.so -> libbz2.so.1.0.8
    Triple("libbrotlicommon.so.1.1.0", "libbrotlicommon.so.1", "libbrotlicommon.so"),
    Triple("libbrotlidec.so.1.1.0", "libbrotlidec.so.1", "libbrotlidec.so"),
    Triple("libbrotlienc.so.1.1.0", "libbrotlienc.so.1", "libbrotlienc.so")
   )

   for ((actual, symlink1, symlink2) in libraries) {
    val actualFile = File(libDir, actual)
    if (!actualFile.exists()) {
     AppLogger.d(TAG, "$actual not found, skipping symlinks")
     continue
    }

    // Create first symlink (e.g., libfreetype.so.6 -> libfreetype.so.6.20.2)
    val symlink1File = File(libDir, symlink1)
    if (!symlink1File.exists()) {
     Os.symlink(actualFile.absolutePath, symlink1File.absolutePath)
     AppLogger.i(TAG, "Created $symlink1 -> $actual")
    } else {
     AppLogger.d(TAG, "$symlink1 already exists")
    }

    // Create second symlink if specified (e.g., libfreetype.so -> libfreetype.so.6.20.2)
    if (symlink2 != null) {
     val symlink2File = File(libDir, symlink2)
     if (!symlink2File.exists()) {
      Os.symlink(actualFile.absolutePath, symlink2File.absolutePath)
      AppLogger.i(TAG, "Created $symlink2 -> $actual")
     } else {
      AppLogger.d(TAG, "$symlink2 already exists")
     }
    }
   }
  } catch (e: Exception) {
   AppLogger.w(TAG, "Failed to create library symlinks: ${e.message}", e)
  }
 }

 /**
  * Setup Wine's hardcoded paths as symlinks
  *
  * Wine binaries compiled for Winlator have hardcoded paths like:
  * /data/data/com.winlator/files/rootfs/tmp
  *
  * We create these as symlinks pointing to our actual package paths.
  */
 private fun setupWineHardcodedPaths() {
  try {
   // Wine's hardcoded rootfs tmp directory
   val hardcodedTmpDir = File("/data/data/com.winlator/files/rootfs/tmp")
   val actualTmpDir = File(rootfsDir, "tmp")

   // Ensure actual tmp directory exists
   if (!actualTmpDir.exists()) {
    val created = actualTmpDir.mkdirs()
    AppLogger.d(TAG, "Created actual tmp directory: ${actualTmpDir.absolutePath}, success=$created")
   }

   // Verify actualTmpDir exists before creating symlink
   if (!actualTmpDir.exists()) {
    AppLogger.e(TAG, "Failed to create actual tmp directory: ${actualTmpDir.absolutePath}")
    return
   }

   // Create parent directories for hardcoded path
   val hardcodedParent = File("/data/data/com.winlator/files/rootfs")
   if (!hardcodedParent.exists()) {
    val created = hardcodedParent.mkdirs()
    AppLogger.d(TAG, "Created hardcoded parent directory: ${hardcodedParent.absolutePath}, success=$created")
   }

   // Verify hardcoded parent exists
   if (!hardcodedParent.exists()) {
    AppLogger.e(TAG, "Failed to create hardcoded parent directory: ${hardcodedParent.absolutePath}")
    return
   }

   // Create symlink: /data/data/com.winlator/files/rootfs/tmp -> actual tmp
   // CRITICAL: Os.symlink(target, linkpath) - NOT (linkpath, target)
   if (!hardcodedTmpDir.exists()) {
    Os.symlink(actualTmpDir.absolutePath, hardcodedTmpDir.absolutePath)
    AppLogger.i(TAG, "Created Wine hardcoded tmp symlink: ${hardcodedTmpDir.absolutePath} -> ${actualTmpDir.absolutePath}")
   } else if (!java.nio.file.Files.isSymbolicLink(hardcodedTmpDir.toPath())) {
    // If it exists but is not a symlink, delete and recreate
    hardcodedTmpDir.deleteRecursively()
    Os.symlink(actualTmpDir.absolutePath, hardcodedTmpDir.absolutePath)
    AppLogger.i(TAG, "Recreated Wine hardcoded tmp symlink: ${hardcodedTmpDir.absolutePath} -> ${actualTmpDir.absolutePath}")
   } else {
    AppLogger.d(TAG, "Wine hardcoded tmp symlink already exists")
   }
  } catch (e: Exception) {
   AppLogger.w(TAG, "Could not create Wine hardcoded paths: ${e.message}")
   // This is not fatal - Wine may still work if it can create the directory itself
  }

  // Copy Box64 to rootfs and patch its interpreter to use Android system linker
  val rootfsBinDir = File(rootfsDir, "usr/local/bin")
  rootfsBinDir.mkdirs()
  val rootfsBox64 = File(rootfsBinDir, "box64")

  try {
   if (!rootfsBox64.exists() && box64Binary.exists()) {
    box64Binary.copyTo(rootfsBox64, overwrite = true)
    rootfsBox64.setExecutable(true, false)
    AppLogger.i(TAG, "Copied Box64 to rootfs: ${rootfsBox64.absolutePath}")

    // Note: Box64 interpreter path is hardcoded in the binary and too long to patch
    // We handle this by invoking ld-linux directly when running Box64
    AppLogger.d(TAG, "Box64 will be invoked via explicit ld-linux interpreter")
   } else if (rootfsBox64.exists()) {
    AppLogger.d(TAG, "Box64 already exists in rootfs: ${rootfsBox64.absolutePath}")
   }
  } catch (e: Exception) {
   AppLogger.w(TAG, "Could not copy Box64 to rootfs: ${e.message}")
  }
 }

 private fun extractAsset(assetPath: String, destination: File) {
  destination.parentFile?.mkdirs()
  context.assets.open(assetPath).use { input ->
   FileOutputStream(destination).use { output ->
    input.copyTo(output)
   }
  }
  AppLogger.d(TAG, "Extracted asset: $assetPath -> ${destination.absolutePath}")
 }

 private fun calculateDirectorySize(directory: File): Long {
  if (!directory.exists()) return 0
  return directory.walkTopDown()
   .filter { it.isFile }
   .map { it.length() }
   .sum()
 }

 /**
  * Install Wine Mono to Wine container if needed
  *
  * Wine Mono provides .NET Framework compatibility, which is required for
  * some 32-bit Windows applications (e.g., SteamSetup.exe NSIS installer).
  *
  * @param containerDir Wine container directory
  * @return Installation result (non-fatal if fails)
  */
 private suspend fun installWineMonoIfNeeded(
  containerDir: File
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   // Check if Wine Mono is already installed
   val monoDir = File(containerDir, "drive_c/windows/mono")
   if (monoDir.exists() && monoDir.listFiles()?.isNotEmpty() == true) {
    AppLogger.i(TAG, "Wine Mono already installed, skipping")
    return@withContext Result.success(Unit)
   }

   // Download Wine Mono MSI
   val downloadResult = wineMonoInstaller.downloadWineMono()
   if (downloadResult.isFailure) {
    return@withContext downloadResult.map { }
   }

   val msiFile = downloadResult.getOrThrow()

   // Install Wine Mono using msiexec
   val installResult = wineMonoInstaller.installWineMono(
    msiFile = msiFile,
    containerDir = containerDir,
    executeCommand = { executable, arguments ->
     executeWineCommand(containerDir, executable, arguments)
    }
   )

   installResult

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to install Wine Mono", e)
   Result.failure(e)
  }
 }

 /**
  * Execute Wine command in Wine container
  *
  * Runs a Windows executable via Wine using proot + box64 + wine.
  *
  * @param containerDir Wine container directory
  * @param executable Executable name (e.g., "msiexec")
  * @param arguments Command line arguments
  * @return Execution result
  */
 private suspend fun executeWineCommand(
  containerDir: File,
  executable: String,
  arguments: List<String>
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   val command = buildList {
    add(prootBinary.absolutePath)
    add("-b")
    add("${rootfsDir.absolutePath}:/data/data/com.winlator/files/rootfs")
    add(box64Binary.absolutePath)
    add(File(wineDir, "bin/wine").absolutePath)
    add("C:\\windows\\system32\\$executable.exe")
    addAll(arguments)
   }

   // Use the same environment variables as wineboot
   val env = buildWinebootEnvironmentVariables(containerDir, 0)
   val process = ProcessBuilder(command)
    .directory(File("/"))
    .apply { environment().putAll(env) }
    .start()

   val output = StringBuilder()
   process.inputStream.bufferedReader().forEachLine { line ->
    output.appendLine(line)
    AppLogger.d(TAG, "[$executable] $line")
   }

   val exitCode = process.waitFor()
   AppLogger.i(TAG, "$executable completed with exit code: $exitCode")

   if (exitCode == 0) Result.success(Unit)
   else Result.failure(Exception("$executable failed with exit code $exitCode"))

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to execute $executable", e)
   Result.failure(e)
  }
 }

 /**
  * Open Winlator app
  *
  * Launches the Winlator app so users can manually run Steam or other programs
  */
 fun openWinlatorApp(): Result<Unit> {
  return try {
   AppLogger.i(TAG, "Opening Winlator app")

   val intent = context.packageManager.getLaunchIntentForPackage("com.winlator")
    ?: return Result.failure(
     Exception("Winlator app not found. Please install Winlator from Google Play Store or GitHub.")
    )

   intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
   context.startActivity(intent)

   AppLogger.i(TAG, "Winlator app launched successfully")
   Result.success(Unit)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to open Winlator app", e)
   Result.failure(e)
  }
 }
}
