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
 private val okHttpClient: OkHttpClient
) {
 companion object {
  private const val TAG = "SteamInstallerService"

  // SteamSetup.exe (32-bit NSIS installer) - CURRENT METHOD
  // NSIS extraction using 7-Zip-JBinding-4Android (bypasses Wine execution)
  // Supports LZMA, BZIP2, ZLIB/Deflate compression formats
  private const val STEAM_INSTALLER_URL = "https://steamcdn-a.akamaihd.net/client/installer/SteamSetup.exe"
  private const val INSTALLER_FILENAME = "SteamSetup.exe"

  // Pre-built Steam Client (ZIP archive) - FALLBACK METHOD
  // Direct extraction to Wine container, bypassing installer entirely
  // NOTE: steam.zip URL returns 404 (may be incorrect/deprecated)
  private const val STEAM_CLIENT_URL = "https://steamcdn-a.akamaihd.net/client/installer/steam.zip"
  private const val STEAM_CLIENT_FILENAME = "steam_client.zip"
 }

 /**
  * Download pre-built Steam Client (ZIP archive) - FALLBACK METHOD
  *
  * This downloads a complete Steam client installation as a ZIP file,
  * bypassing the NSIS installer entirely.
  *
  * NOTE: Currently not used. steam.zip URL returns 404.
  * Primary method is SteamSetup.exe with Windows 10 registry configuration.
  *
  * @return Steam client ZIP file
  */
 suspend fun downloadSteamClient(): Result<File> = withContext(Dispatchers.IO) {
  try {
   val downloadDir = File(context.cacheDir, "steam")
   if (!downloadDir.exists()) {
    downloadDir.mkdirs()
   }

   val zipFile = File(downloadDir, STEAM_CLIENT_FILENAME)

   // Delete existing file
   if (zipFile.exists()) {
    zipFile.delete()
   }

   AppLogger.i(TAG, "Downloading Steam Client from $STEAM_CLIENT_URL")

   // Direct download with OkHttp
   val request = okhttp3.Request.Builder()
    .url(STEAM_CLIENT_URL)
    .build()

   val response = okHttpClient.newCall(request).execute()

   if (!response.isSuccessful) {
    return@withContext Result.failure(
     Exception("Failed to download Steam Client: HTTP ${response.code}")
    )
   }

   response.body?.byteStream()?.use { input ->
    zipFile.outputStream().use { output ->
     input.copyTo(output)
    }
   }

   AppLogger.i(TAG, "Steam Client downloaded: ${zipFile.absolutePath} (${zipFile.length() / 1024 / 1024}MB)")
   Result.success(zipFile)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to download Steam Client", e)
   Result.failure(e)
  }
 }

 /**
  * Extract Steam Client ZIP archive to Wine container - FALLBACK METHOD
  *
  * Extracts the pre-built Steam client directly to the container's
  * "C:\Program Files (x86)\Steam" directory.
  *
  * NOTE: Currently not used. steam.zip URL returns 404.
  * Primary method is SteamSetup.exe with Windows 10 registry configuration.
  *
  * @param zipFile Downloaded steam_client.zip
  * @param targetDir Target directory (e.g., container/drive_c/Program Files (x86)/Steam)
  * @return Result indicating success or failure
  */
 suspend fun extractSteamClient(zipFile: File, targetDir: File): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   if (!targetDir.parentFile?.exists()!!) {
    targetDir.parentFile?.mkdirs()
   }

   AppLogger.i(TAG, "Extracting Steam Client to ${targetDir.absolutePath}")

   // Use Java's built-in ZipInputStream for extraction
   java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zipInput ->
    var entry = zipInput.nextEntry
    while (entry != null) {
     val outputFile = File(targetDir, entry.name)

     if (entry.isDirectory) {
      outputFile.mkdirs()
     } else {
      outputFile.parentFile?.mkdirs()
      outputFile.outputStream().use { output ->
       zipInput.copyTo(output)
      }
     }

     zipInput.closeEntry()
     entry = zipInput.nextEntry
    }
   }

   // Verify Steam.exe exists (case-sensitive on Android)
   val steamExe = File(targetDir, "Steam.exe")
   if (!steamExe.exists()) {
    return@withContext Result.failure(
     Exception("Steam.exe not found after extraction")
    )
   }

   AppLogger.i(TAG, "Steam Client extracted successfully: ${targetDir.absolutePath}")
   Result.success(Unit)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to extract Steam Client", e)
   Result.failure(e)
  }
 }

 /**
  * Steam installer download (SteamSetup.exe)
  *
  * Downloads the official Steam installer from Valve CDN.
  * Used as source for NSIS extraction (primary method) or Wine-based installation (fallback).
  *
  * @return Result containing downloaded installer file or error
  * @see extractSteamFromNSIS Primary extraction method using 7-Zip-JBinding-4Android
  */
 suspend fun downloadInstaller(): Result<File> = withContext(Dispatchers.IO) {
  try {
   val downloadDir = File(context.cacheDir, "steam")
   if (!downloadDir.exists()) {
    downloadDir.mkdirs()
   }

   val installerFile = File(downloadDir, INSTALLER_FILENAME)

   // Delete existing file if present
   if (installerFile.exists()) {
    installerFile.delete()
   }

   AppLogger.i(TAG, "Downloading Steam installer from $STEAM_INSTALLER_URL")

   // Direct download with OkHttp
   val request = okhttp3.Request.Builder()
    .url(STEAM_INSTALLER_URL)
    .build()

   val response = okHttpClient.newCall(request).execute()

   if (!response.isSuccessful) {
    return@withContext Result.failure(
     Exception("Failed to download: HTTP ${response.code}")
    )
   }

   response.body?.byteStream()?.use { input ->
    installerFile.outputStream().use { output ->
     input.copyTo(output)
    }
   }

   AppLogger.i(TAG, "Steam installer downloaded: ${installerFile.absolutePath}")
   Result.success(installerFile)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to download Steam installer", e)
   Result.failure(e)
  }
 }

 /**
  * Extract Steam Client from SteamSetup.exe NSIS installer
  *
  * Uses 7-Zip-JBinding-4Android to extract Steam.exe and related files directly
  * from the NSIS installer, bypassing the need for Wine execution.
  *
  * This method extracts the Steam client files from SteamSetup.exe using
  * 7-Zip-JBinding-4Android (ARM64 compatible, supports LZMA/BZIP2/ZLIB compression).
  *
  * @param setupExe Downloaded SteamSetup.exe file
  * @param targetDir Target directory (e.g., container/drive_c/Program Files (x86)/Steam)
  * @param onProgress Optional progress callback (filesExtracted, totalFiles)
  * @return Result indicating success or failure
  */
 suspend fun extractSteamFromNSIS(
  setupExe: File,
  targetDir: File,
  onProgress: ((filesExtracted: Int, totalFiles: Int) -> Unit)? = null
 ): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   if (!setupExe.exists()) {
    return@withContext Result.failure(
     Exception("SteamSetup.exe not found: ${setupExe.absolutePath}")
    )
   }

   targetDir.parentFile?.mkdirs()

   AppLogger.i(TAG, "Extracting Steam Client from NSIS installer using 7-Zip-JBinding library (ARM64 compatible)")
   AppLogger.i(TAG, "Source: ${setupExe.absolutePath}")
   AppLogger.i(TAG, "Target: ${targetDir.absolutePath}")

   // Use 7-Zip-JBinding library for NSIS extraction (supports LZMA, BZIP2, ZLIB/Deflate)
   val extractor = NsisExtractor(setupExe)
   val extractResult = extractor.extractSteamFiles(targetDir, onProgress)

   if (extractResult.isFailure) {
    return@withContext Result.failure(
     extractResult.exceptionOrNull() ?: Exception("NSIS extraction failed")
    )
   }

   val extractedCount = extractResult.getOrDefault(0)
   AppLogger.i(TAG, "Extracted $extractedCount files from NSIS installer")

   // Verify Steam.exe exists (case-sensitive on Android)
   val steamExe = File(targetDir, "Steam.exe")
   if (!steamExe.exists()) {
    return@withContext Result.failure(
     Exception("Steam.exe not found after extraction (extracted $extractedCount files)")
    )
   }

   AppLogger.i(TAG, "NSIS extraction successful: Steam.exe found (${steamExe.length()} bytes)")
   Result.success(Unit)

  } catch (e: Exception) {
   AppLogger.e(TAG, "Failed to extract Steam from NSIS installer", e)
   Result.failure(e)
  }
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
}
