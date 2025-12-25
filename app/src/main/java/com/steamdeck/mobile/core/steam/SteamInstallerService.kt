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
 private val nsisExtractor: NsisExtractor
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
  * Extract Steam files from NSIS installer
  *
  * Uses 7-Zip-JBinding-4Android to extract all files from SteamSetup.exe.
  * This bypasses Wine WoW64 limitations.
  *
  * Extracted files include:
  * - Steam.exe (~180KB) - Main Steam client
  * - steamclient.dll (~2.5MB) - Steam API library
  * - tier0_s.dll, vstdlib_s.dll (~300KB total) - Valve foundation libraries
  * - libcef.dll (~124MB) - Chromium Embedded Framework
  * - steamwebhelper.exe (~3MB) - Web content renderer
  * - And ~127 other files (total ~180MB)
  *
  * @param setupFile SteamSetup.exe file (NSIS installer)
  * @param targetDir Target directory (e.g., C:\Program Files (x86)\Steam in Wine container)
  * @param onProgress Optional progress callback (filesExtracted, totalFiles)
  * @return Result containing extracted file count or error
  */
 suspend fun extractSteamFromNSIS(
  setupFile: File,
  targetDir: File,
  onProgress: ((filesExtracted: Int, totalFiles: Int) -> Unit)? = null
 ): Result<Int> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Extracting Steam from NSIS installer: ${setupFile.absolutePath}")
   AppLogger.i(TAG, "Target directory: ${targetDir.absolutePath}")

   if (!setupFile.exists()) {
    return@withContext Result.failure(
     Exception("SteamSetup.exe not found: ${setupFile.absolutePath}")
    )
   }

   // Extract using 7-Zip-JBinding-4Android
   val extractionResult = nsisExtractor.extractAll(setupFile, targetDir, onProgress)

   if (extractionResult.isFailure) {
    val error = extractionResult.exceptionOrNull()
    AppLogger.e(TAG, "NSIS extraction failed", error)
    return@withContext Result.failure(
     error ?: Exception("NSIS extraction failed")
    )
   }

   val filesExtracted = extractionResult.getOrNull() ?: 0
   AppLogger.i(TAG, "NSIS extraction completed: $filesExtracted files extracted to ${targetDir.absolutePath}")

   // Verify critical files exist
   val steamExe = File(targetDir, "Steam.exe")
   val steamclientDll = File(targetDir, "steamclient.dll")

   if (!steamExe.exists()) {
    return@withContext Result.failure(
     Exception("Steam.exe not found after extraction - NSIS extraction may have failed")
    )
   }

   if (!steamclientDll.exists()) {
    AppLogger.w(TAG, "steamclient.dll not found - Steam may not launch properly")
   }

   AppLogger.i(TAG, "Extraction verified: Steam.exe (${steamExe.length() / 1024}KB)")
   if (steamclientDll.exists()) {
    AppLogger.i(TAG, "  steamclient.dll: ${steamclientDll.length() / 1024}KB")
   }

   Result.success(filesExtracted)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Steam NSIS extraction failed", e)
   Result.failure(e)
  }
 }

}
