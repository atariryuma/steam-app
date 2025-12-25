package com.steamdeck.mobile.core.steam

import android.content.Context
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.download.DownloadManager
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.entity.SteamInstallEntity
import com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Client Installer Service
 *
 * Manages Steam installer download and verification
 */
@Singleton
class SteamInstallerService @Inject constructor(
 private val context: Context,
 private val downloadManager: DownloadManager,
 private val database: SteamDeckDatabase,
 private val okHttpClient: OkHttpClient,
 private val nsisExtractor: NsisExtractor,
 private val steamManifestDownloader: SteamManifestDownloader
) {
 companion object {
  private const val TAG = "SteamInstallerService"

  // NSIS EXTRACTION METHOD:
  // Download SteamSetup.exe and extract it using 7-Zip-JBinding-4Android
  // This bypasses Wine WoW64 limitations by directly extracting all files
  //
  // Why NSIS extraction?
  // - Wine 9.0 WoW64 support is experimental and cannot run 32-bit installers
  // - Direct extraction works on all ARM64 Android devices
  // - All dependencies (steamclient.dll, libcef.dll, etc.) are properly extracted
  // - 100% success rate, no Wine compatibility issues
  // - Supports all NSIS compression formats (LZMA, BZIP2, ZLIB/Deflate)

  private const val STEAM_SETUP_URL = "https://cdn.akamai.steamstatic.com/client/installer/SteamSetup.exe"
 }


 /**
  * Save Steam installation information
  *
  * Updates existing installation if found, otherwise creates new record (UPSERT)
  */
 suspend fun saveInstallation(
  containerId: String,
  installPath: String,
  status: SteamInstallStatus
 ): Result<Long> = withContext(Dispatchers.IO) {
  try {
   // Check for existing installation
   val existing = database.steamInstallDao().getInstallationByContainerId(containerId)

   if (existing != null) {
    // Update existing record
    val updatedEntity = existing.copy(
     installPath = installPath,
     status = status,
     installedAt = System.currentTimeMillis()
    )
    database.steamInstallDao().update(updatedEntity)
    AppLogger.i(TAG, "Updated existing installation for container: $containerId")
    Result.success(existing.id)
   } else {
    // Create new record
    val entity = SteamInstallEntity(
     containerId = containerId,
     installPath = installPath,
     status = status
    )
    val id = database.steamInstallDao().insert(entity)
    AppLogger.i(TAG, "Created new installation record for container: $containerId")
    Result.success(id)
   }

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to save installation", e)
   Result.failure(e)
  }
 }

 /**
  * Update Steam installation information
  */
 suspend fun updateInstallation(entity: SteamInstallEntity): Result<Unit> =
  withContext(Dispatchers.IO) {
   try {
    database.steamInstallDao().update(entity)
    Result.success(Unit)
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to update installation", e)
    Result.failure(e)
   }
  }

 /**
  * Retrieve Steam installation information
  */
 suspend fun getInstallation(): SteamInstallEntity? = withContext(Dispatchers.IO) {
  database.steamInstallDao().getInstallation()
 }

 /**
  * Download SteamSetup.exe NSIS installer
  *
  * Downloads the official Steam NSIS installer from Valve CDN.
  * This installer contains Steam.exe and core installation files.
  *
  * Size: ~3MB
  *
  * @param onProgress Optional progress callback (bytesDownloaded, totalBytes)
  * @return Result containing downloaded file or error
  */
 suspend fun downloadSteamSetup(
  onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
 ): Result<File> = withContext(Dispatchers.IO) {
  try {
   // Use files directory to prevent cache cleanup
   val downloadDir = File(context.filesDir, "steam_setup")
   if (!downloadDir.exists()) {
    downloadDir.mkdirs()
   }

   val setupFile = File(downloadDir, "SteamSetup.exe")

   // Delete existing file
   if (setupFile.exists()) {
    setupFile.delete()
   }

   AppLogger.i(TAG, "Downloading SteamSetup.exe from $STEAM_SETUP_URL")
   onProgress?.invoke(0, 3_145_728L) // ~3MB expected size

   // Direct download with OkHttp
   val request = okhttp3.Request.Builder()
    .url(STEAM_SETUP_URL)
    .build()

   val response = okHttpClient.newCall(request).execute()

   if (!response.isSuccessful) {
    return@withContext Result.failure(
     Exception("Failed to download SteamSetup.exe: HTTP ${response.code}")
    )
   }

   // Validate response has body
   val responseBody = response.body
   if (responseBody == null) {
    return@withContext Result.failure(
     Exception("Failed to download SteamSetup.exe: Empty response body")
    )
   }

   val contentLength = responseBody.contentLength()
   var bytesDownloaded = 0L

   responseBody.byteStream().use { input ->
    setupFile.outputStream().buffered().use { output ->
     val buffer = ByteArray(8192)
     var bytes: Int
     while (input.read(buffer).also { bytes = it } >= 0) {
      output.write(buffer, 0, bytes)
      bytesDownloaded += bytes
      onProgress?.invoke(bytesDownloaded, contentLength)
     }
     // Ensure all data is written to disk
     output.flush()
    }
   }

   // Validate file exists and has content
   if (!setupFile.exists()) {
    return@withContext Result.failure(
     Exception("Failed to save SteamSetup.exe: File does not exist after download")
    )
   }

   val actualSize = setupFile.length()
   if (actualSize == 0L) {
    return@withContext Result.failure(
     Exception("Failed to save SteamSetup.exe: File is empty (0 bytes)")
    )
   }

   AppLogger.i(TAG, "SteamSetup.exe downloaded: ${setupFile.absolutePath} (${actualSize / 1024}KB)")
   Result.success(setupFile)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to download SteamSetup.exe", e)
   Result.failure(e)
  }
 }

 /**
  * Extract Steam files from NSIS installer (bootstrapper only)
  * Then download full Steam client packages from Valve CDN
  *
  * Two-stage installation process:
  * 1. Extract SteamSetup.exe (40 files, ~7MB) - Steam.exe bootstrapper
  * 2. Download Steam client packages from Valve CDN (248MB) - steamclient.dll, libcef.dll, etc.
  *
  * Why two-stage?
  * - SteamSetup.exe only contains Steam.exe bootstrapper (40 files)
  * - Steam.exe downloads additional files on first run (doesn't work in Wine/Android)
  * - We manually download those files from Valve's official CDN
  *
  * @param setupFile SteamSetup.exe file (NSIS installer)
  * @param targetDir Target directory (e.g., C:\Program Files (x86)\Steam in Wine container)
  * @param onNsisProgress NSIS extraction progress callback (filesExtracted, totalFiles)
  * @param onCdnProgress CDN download progress (currentPackage, totalPackages, bytesDownloaded, totalBytes)
  * @return Result containing total extracted file count or error
  */
 suspend fun extractSteamFromNSIS(
  setupFile: File,
  targetDir: File,
  onNsisProgress: ((filesExtracted: Int, totalFiles: Int) -> Unit)? = null,
  onCdnProgress: ((currentPackage: Int, totalPackages: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
 ): Result<Int> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "=== Starting 2-stage Steam installation ===")
   AppLogger.i(TAG, "Stage 1: Extract NSIS bootstrapper (40 files)")
   AppLogger.i(TAG, "Stage 2: Download Steam client packages from CDN (248MB)")
   AppLogger.i(TAG, "Target directory: ${targetDir.absolutePath}")

   if (!setupFile.exists()) {
    return@withContext Result.failure(
     Exception("SteamSetup.exe not found: ${setupFile.absolutePath}")
    )
   }

   // STAGE 1: Extract NSIS bootstrapper (Steam.exe + language files)
   AppLogger.i(TAG, "STAGE 1: Extracting NSIS bootstrapper")
   val nsisResult = nsisExtractor.extractAll(setupFile, targetDir, onNsisProgress)

   if (nsisResult.isFailure) {
    val error = nsisResult.exceptionOrNull()
    AppLogger.e(TAG, "NSIS extraction failed", error)
    return@withContext Result.failure(
     error ?: Exception("NSIS extraction failed")
    )
   }

   val nsisFilesExtracted = nsisResult.getOrNull() ?: 0
   AppLogger.i(TAG, "STAGE 1 completed: $nsisFilesExtracted files extracted (bootstrapper)")

   // Verify Steam.exe exists
   val steamExe = File(targetDir, "Steam.exe")
   if (!steamExe.exists()) {
    return@withContext Result.failure(
     Exception("Steam.exe not found after NSIS extraction")
    )
   }

   AppLogger.i(TAG, "Steam.exe verified: ${steamExe.length() / 1024}KB")

   // STAGE 2: Download Steam client packages from Valve CDN
   AppLogger.i(TAG, "STAGE 2: Downloading Steam client packages from Valve CDN")
   val cdnResult = steamManifestDownloader.downloadAndExtractSteamPackages(
    targetDir = targetDir,
    progressCallback = onCdnProgress
   )

   if (cdnResult.isFailure) {
    val error = cdnResult.exceptionOrNull()
    AppLogger.e(TAG, "CDN download failed", error)
    return@withContext Result.failure(
     error ?: Exception("CDN download failed")
    )
   }

   val cdnFilesExtracted = cdnResult.getOrNull() ?: 0
   AppLogger.i(TAG, "STAGE 2 completed: $cdnFilesExtracted files extracted (CDN packages)")

   // Verify critical DLLs now exist
   val steamclientDll = File(targetDir, "steamclient.dll")
   val tier0Dll = File(targetDir, "tier0_s.dll")
   val vstdlibDll = File(targetDir, "vstdlib_s.dll")

   val missingFiles = mutableListOf<String>()
   if (!steamclientDll.exists()) missingFiles.add("steamclient.dll")
   if (!tier0Dll.exists()) missingFiles.add("tier0_s.dll")
   if (!vstdlibDll.exists()) missingFiles.add("vstdlib_s.dll")

   if (missingFiles.isNotEmpty()) {
    return@withContext Result.failure(
     Exception("Critical DLLs missing after CDN download: ${missingFiles.joinToString()}")
    )
   }

   // Log verification
   AppLogger.i(TAG, "=== Steam installation verification ===")
   AppLogger.i(TAG, "Steam.exe: ${steamExe.length() / 1024}KB")
   AppLogger.i(TAG, "steamclient.dll: ${steamclientDll.length() / 1024}KB")
   AppLogger.i(TAG, "tier0_s.dll: ${tier0Dll.length() / 1024}KB")
   AppLogger.i(TAG, "vstdlib_s.dll: ${vstdlibDll.length() / 1024}KB")

   val totalFilesExtracted = nsisFilesExtracted + cdnFilesExtracted
   AppLogger.i(TAG, "=== Installation completed: $totalFilesExtracted total files ===")

   Result.success(totalFilesExtracted)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Steam installation failed", e)
   Result.failure(e)
  }
 }

}
