package com.steamdeck.mobile.domain.model

import androidx.compose.runtime.Immutable

/**
 * Download information domain model
 *
 * @Immutable enables Compose performance optimization by reducing unnecessary recompositions.
 */
@Immutable
data class Download(
 val id: Long = 0,
 val gameId: Long? = null,
 val fileName: String,
 val url: String,
 val status: DownloadStatus,
 val installationStatus: InstallationStatus = InstallationStatus.NOT_INSTALLED,
 val progress: Int = 0,
 val downloadedBytes: Long = 0,
 val totalBytes: Long = 0,
 val destinationPath: String,
 val startedTimestamp: Long = System.currentTimeMillis(),
 val completedTimestamp: Long? = null,
 val errorMessage: String? = null
) {
 /**
  * Calculate download speed (bytes/sec)
  */
 fun calculateSpeed(elapsedMillis: Long): Long {
  if (elapsedMillis == 0L) return 0L
  return (downloadedBytes * 1000) / elapsedMillis
 }

 /**
  * Calculate remaining time (seconds)
  */
 fun calculateRemainingTime(speedBytesPerSec: Long): Long {
  if (speedBytesPerSec == 0L) return Long.MAX_VALUE
  val remainingBytes = totalBytes - downloadedBytes
  return remainingBytes / speedBytesPerSec
 }

 /**
  * Get formatted downloaded size
  */
 val downloadedSizeFormatted: String
  get() = formatBytes(downloadedBytes)

 /**
  * Get formatted total size
  */
 val totalSizeFormatted: String
  get() = formatBytes(totalBytes)

 /**
  * Get formatted progress percentage (Example: "45%")
  */
 val progressFormatted: String
  get() = "$progress%"

 companion object {
  /**
   * Convert bytes to human-readable format
   *
   * Best Practice: Explicit Locale.US to avoid locale-dependent formatting issues
   * Reference: https://developer.android.com/guide/topics/resources/localization
   */
  fun formatBytes(bytes: Long): String {
   if (bytes < 1024) return "$bytes B"
   val kb = bytes / 1024.0
   if (kb < 1024) return String.format(java.util.Locale.US, "%.2f KB", kb)
   val mb = kb / 1024.0
   if (mb < 1024) return String.format(java.util.Locale.US, "%.2f MB", mb)
   val gb = mb / 1024.0
   return String.format(java.util.Locale.US, "%.2f GB", gb)
  }

  /**
   * Convert speed to human-readable format
   */
  fun formatSpeed(bytesPerSec: Long): String {
   return "${formatBytes(bytesPerSec)}/s"
  }

  /**
   * Convert remaining time to human-readable format
   */
  fun formatRemainingTime(seconds: Long): String {
   if (seconds == Long.MAX_VALUE) return "Unknown"
   val hours = seconds / 3600
   val minutes = (seconds % 3600) / 60
   val secs = seconds % 60

   return when {
    hours > 0 -> "${hours}h ${minutes}m"
    minutes > 0 -> "${minutes}m ${secs}s"
    else -> "${secs}s"
   }
  }
 }
}

/**
 * Download status
 */
enum class DownloadStatus {
 /** Download queued */
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
 CANCELLED;

 /**
  * Display name for status
  */
 val displayName: String
  get() = when (this) {
   PENDING -> "waiting"
   DOWNLOADING -> "downloadin"
   PAUSED -> "pause"
   COMPLETED -> "completed"
   FAILED -> "error"
   CANCELLED -> "cancel"
  }

 /**
  * Check if status is active
  */
 val isActive: Boolean
  get() = this == DOWNLOADING || this == PENDING
}

/**
 * Installation status (after download completion)
 */
enum class InstallationStatus {
 /** Not installed (download only completed) */
 NOT_INSTALLED,

 /** Installation queued */
 PENDING,

 /** Installing */
 INSTALLING,

 /** Installation completed */
 INSTALLED,

 /** Installation failed */
 FAILED;

 /**
  * Display name for status
  */
 val displayName: String
  get() = when (this) {
   NOT_INSTALLED -> "Not Installed"
   PENDING -> "waiting"
   INSTALLING -> "Installing"
   INSTALLED -> "Installed"
   FAILED -> "Failed"
  }
}
