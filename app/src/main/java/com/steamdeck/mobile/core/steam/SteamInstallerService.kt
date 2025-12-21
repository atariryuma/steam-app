package com.steamdeck.mobile.core.steam

import android.content.Context
import android.util.Log
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
  // Requires Windows 10 registry configuration (handled by WinlatorEmulator.setWindowsVersion())
  // Works with WoW64 mode when Wine is configured as Windows 10
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

   Log.i(TAG, "Downloading Steam Client from $STEAM_CLIENT_URL")

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

   Log.i(TAG, "Steam Client downloaded: ${zipFile.absolutePath} (${zipFile.length() / 1024 / 1024}MB)")
   Result.success(zipFile)

  } catch (e: Exception) {
   Log.e(TAG, "Failed to download Steam Client", e)
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

   Log.i(TAG, "Extracting Steam Client to ${targetDir.absolutePath}")

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

   // Verify steam.exe exists
   val steamExe = File(targetDir, "steam.exe")
   if (!steamExe.exists()) {
    return@withContext Result.failure(
     Exception("steam.exe not found after extraction")
    )
   }

   Log.i(TAG, "Steam Client extracted successfully: ${targetDir.absolutePath}")
   Result.success(Unit)

  } catch (e: Exception) {
   Log.e(TAG, "Failed to extract Steam Client", e)
   Result.failure(e)
  }
 }

 /**
  * Steam installer download (SteamSetup.exe)
  *
  * NOTE: This method is NOT deprecated. It's the primary installation method.
  * Requires Windows 10 registry configuration (handled by WinlatorEmulator.setWindowsVersion()).
  *
  * @deprecated marking removed - this is the current implementation
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

   Log.i(TAG, "Downloading Steam installer from $STEAM_INSTALLER_URL")

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

   Log.i(TAG, "Steam installer downloaded: ${installerFile.absolutePath}")
   Result.success(installerFile)

  } catch (e: Exception) {
   Log.e(TAG, "Failed to download Steam installer", e)
   Result.failure(e)
  }
 }

 /**
  * Extract Steam Client from SteamSetup.exe NSIS installer
  *
  * Uses sevenzipjbinding to extract Steam.exe and related files directly
  * from the NSIS installer, bypassing the need for Wine execution.
  *
  * This method extracts the Steam client files from SteamSetup.exe using 7-Zip,
  * which has supported NSIS extraction since version 4.42.
  *
  * @param setupExe Downloaded SteamSetup.exe file
  * @param targetDir Target directory (e.g., container/drive_c/Program Files (x86)/Steam)
  * @return Result indicating success or failure
  */
 suspend fun extractSteamFromNSIS(setupExe: File, targetDir: File): Result<Unit> = withContext(Dispatchers.IO) {
  try {
   if (!setupExe.exists()) {
    return@withContext Result.failure(
     Exception("SteamSetup.exe not found: ${setupExe.absolutePath}")
    )
   }

   if (!targetDir.parentFile?.exists()!!) {
    targetDir.parentFile?.mkdirs()
   }

   Log.i(TAG, "Extracting Steam Client from NSIS installer: ${setupExe.absolutePath}")
   Log.i(TAG, "Target directory: ${targetDir.absolutePath}")

   var archive: net.sf.sevenzipjbinding.IInArchive? = null
   var randomAccessFile: java.io.RandomAccessFile? = null

   try {
    randomAccessFile = java.io.RandomAccessFile(setupExe, "r")

    // Open NSIS archive using sevenzipjbinding
    archive = net.sf.sevenzipjbinding.SevenZip.openInArchive(
     null, // Auto-detect format (NSIS)
     object : net.sf.sevenzipjbinding.IInStream {
      override fun read(data: ByteArray): Int {
       return randomAccessFile.read(data)
      }

      override fun seek(offset: Long, seekOrigin: Int): Long {
       return when (seekOrigin) {
        0 -> { // BEGIN
         randomAccessFile.seek(offset)
         offset
        }
        1 -> { // CURRENT
         val newPos = randomAccessFile.filePointer + offset
         randomAccessFile.seek(newPos)
         newPos
        }
        2 -> { // END
         val newPos = randomAccessFile.length() + offset
         randomAccessFile.seek(newPos)
         newPos
        }
        else -> throw IllegalArgumentException("Invalid seek origin: $seekOrigin")
       }
      }

      override fun close() {
       // Don't close here, will be closed in finally block
      }
     }
    )

    val itemCount = archive.numberOfItems
    Log.i(TAG, "NSIS archive contains $itemCount items")

    var extractedFiles = 0
    val indices = IntArray(itemCount) { it }

    // Extract all files
    archive.extract(indices, false, object : net.sf.sevenzipjbinding.IArchiveExtractCallback {
     private var currentIndex = -1
     private var currentOutputStream: java.io.FileOutputStream? = null
     private var currentFile: File? = null

     override fun setTotal(total: Long) {
      Log.d(TAG, "Total bytes to extract: $total")
     }

     override fun setCompleted(complete: Long) {
      // Progress tracking (optional)
     }

     override fun getStream(
      index: Int,
      extractAskMode: net.sf.sevenzipjbinding.ExtractAskMode
     ): net.sf.sevenzipjbinding.ISequentialOutStream? {
      currentIndex = index

      if (extractAskMode != net.sf.sevenzipjbinding.ExtractAskMode.EXTRACT) {
       return null
      }

      val path = archive.getStringProperty(index, net.sf.sevenzipjbinding.PropID.PATH) ?: ""
      val isFolder = archive.getProperty(index, net.sf.sevenzipjbinding.PropID.IS_FOLDER) as? Boolean ?: false

      if (isFolder || path.isEmpty()) {
       return null
      }

      // Create output file
      currentFile = File(targetDir, path.replace("\\", "/"))
      currentFile?.parentFile?.mkdirs()

      currentOutputStream = java.io.FileOutputStream(currentFile)

      return object : net.sf.sevenzipjbinding.ISequentialOutStream {
       override fun write(data: ByteArray): Int {
        currentOutputStream?.write(data)
        return data.size
       }
      }
     }

     override fun prepareOperation(extractAskMode: net.sf.sevenzipjbinding.ExtractAskMode) {
      // Prepare for extraction
     }

     override fun setOperationResult(extractOperationResult: net.sf.sevenzipjbinding.ExtractOperationResult) {
      currentOutputStream?.close()
      currentOutputStream = null

      if (extractOperationResult == net.sf.sevenzipjbinding.ExtractOperationResult.OK) {
       extractedFiles++
       if (extractedFiles % 10 == 0) {
        Log.d(TAG, "Extracted $extractedFiles files...")
       }
      } else {
       Log.w(TAG, "Failed to extract file at index $currentIndex: $extractOperationResult")
      }
     }
    })

    Log.i(TAG, "Extracted $extractedFiles files from NSIS installer")

    // Verify steam.exe exists
    val steamExe = File(targetDir, "steam.exe")
    if (!steamExe.exists()) {
     return@withContext Result.failure(
      Exception("steam.exe not found after extraction. Expected: ${steamExe.absolutePath}")
     )
    }

    Log.i(TAG, "Steam Client extracted successfully: ${targetDir.absolutePath}")
    Result.success(Unit)

   } finally {
    archive?.close()
    randomAccessFile?.close()
   }

  } catch (e: Exception) {
   Log.e(TAG, "Failed to extract Steam from NSIS installer", e)
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
    Log.i(TAG, "Updated existing installation for container: $containerId")
    Result.success(existing.id)
   } else {
    // Create new record
    val entity = SteamInstallEntity(
     containerId = containerId,
     installPath = installPath,
     status = status
    )
    val id = database.steamInstallDao().insert(entity)
    Log.i(TAG, "Created new installation record for container: $containerId")
    Result.success(id)
   }

  } catch (e: Exception) {
   Log.e(TAG, "Failed to save installation", e)
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
    Log.e(TAG, "Failed to update installation", e)
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
