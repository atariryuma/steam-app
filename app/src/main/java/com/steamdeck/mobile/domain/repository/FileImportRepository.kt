package com.steamdeck.mobile.domain.repository

import android.net.Uri
import com.steamdeck.mobile.domain.model.FtpConfig
import com.steamdeck.mobile.domain.model.ImportableFile
import com.steamdeck.mobile.domain.model.SmbConfig
import kotlinx.coroutines.flow.Flow

/**
 * File import repository interface
 */
interface FileImportRepository {

 /**
  * Retrieve file list from USB OTG device
  *
  * @param path Directory path (root is "/")
  * @return File list
  */
 suspend fun listUsbFiles(path: String = "/"): Result<List<ImportableFile>>

 /**
  * Retrieve file list from SMB/CIFS share
  *
  * @param config SMB connection configuration
  * @param path Directory path (relative path within share)
  * @return File list
  */
 suspend fun listSmbFiles(config: SmbConfig, path: String = "/"): Result<List<ImportableFile>>

 /**
  * Retrieve file list from FTP server
  *
  * @param config FTP connection configuration
  * @param path Directory path
  * @return File list
  */
 suspend fun listFtpFiles(config: FtpConfig, path: String = "/"): Result<List<ImportableFile>>

 /**
  * Retrieve files from local storage (SAF)
  *
  * @param uri Directory URI selected by user
  * @return File list
  */
 suspend fun listLocalFiles(uri: Uri): Result<List<ImportableFile>>

 /**
  * Import file (copy)
  *
  * @param file Source file to import
  * @param destinationPath Destination path (internal storage)
  * @return Progress status Flow
  */
 fun importFile(file: ImportableFile, destinationPath: String): Flow<ImportProgress>

 /**
  * Check if USB OTG device is connected
  *
  * @return true if device is connected
  */
 suspend fun isUsbDeviceConnected(): Boolean

 /**
  * Test SMB/CIFS connection
  *
  * @param config SMB connection configuration
  * @return true if connection succeeds
  */
 suspend fun testSmbConnection(config: SmbConfig): Result<Boolean>

 /**
  * Test FTP connection
  *
  * @param config FTP connection configuration
  * @return true if connection succeeds
  */
 suspend fun testFtpConnection(config: FtpConfig): Result<Boolean>
}

/**
 * Import progress state
 */
sealed class ImportProgress {
 object Idle : ImportProgress()

 data class Preparing(val message: String) : ImportProgress()

 data class Copying(
  val bytesTransferred: Long,
  val totalBytes: Long,
  val speedBytesPerSecond: Long
 ) : ImportProgress() {
  val progressPercent: Int
   get() = if (totalBytes > 0) {
    ((bytesTransferred * 100) / totalBytes).toInt()
   } else 0

  val speedFormatted: String
   get() {
    return when {
     speedBytesPerSecond < 1024 -> "$speedBytesPerSecond B/s"
     speedBytesPerSecond < 1024 * 1024 -> "${speedBytesPerSecond / 1024} KB/s"
     else -> String.format("%.2f MB/s", speedBytesPerSecond / (1024.0 * 1024.0))
    }
   }
 }

 data class Success(val destinationPath: String) : ImportProgress()

 data class Error(val message: String, val cause: Throwable? = null) : ImportProgress()
}
