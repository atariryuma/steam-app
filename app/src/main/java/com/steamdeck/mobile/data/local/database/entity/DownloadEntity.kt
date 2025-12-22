package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Download history and state storage entity
 *
 * Performance optimization (2025 best practice):
 * - Added installationStatus for automatic game installation
 * - Enables seamless download-to-play workflow
 * - Indexes on frequently queried columns for faster lookups
 */
@Entity(
 tableName = "downloads",
 indices = [
  Index(value = ["gameId"]),
  Index(value = ["status"]),
  Index(value = ["installationStatus"])
 ]
)
data class DownloadEntity(
 @PrimaryKey(autoGenerate = true)
 val id: Long = 0,

 /** Related game ID */
 val gameId: Long? = null,

 /** File name */
 val fileName: String,

 /** Download URL */
 val url: String,

 /** Download status */
 val status: DownloadStatus,

 /** Installation status (processing after download completion) */
 val installationStatus: InstallationStatus = InstallationStatus.NOT_INSTALLED,

 /** Progress percentage (0-100) */
 val progress: Int = 0,

 /** Downloaded bytes */
 val downloadedBytes: Long = 0,

 /** Total file size (bytes) */
 val totalBytes: Long = 0,

 /** Download speed (bytes/second) */
 val speedBytesPerSecond: Long = 0,

 /** Destination path */
 val destinationPath: String = "",

 /**
  * Download start date/time (Unix timestamp)
  *
  * Note: Uses System.currentTimeMillis() as default value,
  * but it is recommended to pass an explicit value when using.
  */
 val startedTimestamp: Long = System.currentTimeMillis(),

 /**
  * Created date/time (Unix timestamp)
  *
  * Note: Uses System.currentTimeMillis() as default value,
  * but it is recommended to pass an explicit value when using.
  */
 val createdAt: Long = System.currentTimeMillis(),

 /**
  * Updated date/time (Unix timestamp)
  *
  * Note: Uses System.currentTimeMillis() as default value,
  * but it is recommended to pass an explicit value when using.
  */
 val updatedAt: Long = System.currentTimeMillis(),

 /** Download completed date/time (Unix timestamp) */
 val completedTimestamp: Long? = null,

 /** Error message */
 val errorMessage: String? = null
)

/**
 * Download status
 */
enum class DownloadStatus {
 /** Waiting for download */
 PENDING,

 /** Downloading */
 DOWNLOADING,

 /** Paused */
 PAUSED,

 /** Completed */
 COMPLETED,

 /** Failed */
 FAILED,

 /** Cancelled */
 CANCELLED
}

/**
 * Installation status (after download completion)
 */
enum class InstallationStatus {
 /** Not installed (download only completed) */
 NOT_INSTALLED,

 /** Waiting for installation */
 PENDING,

 /** Installing */
 INSTALLING,

 /** Installation completed */
 INSTALLED,

 /** Installation failed */
 FAILED
}
