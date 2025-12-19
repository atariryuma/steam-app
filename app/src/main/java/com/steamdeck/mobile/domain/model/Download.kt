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
  * download速度calculation（bytes/sec）
  */
 fun calculateSpeed(elapsedMillis: Long): Long {
  if (elapsedMillis == 0L) return 0L
  return (downloadedBytes * 1000) / elapsedMillis
 }

 /**
  * 残り時間calculation（秒）
  */
 fun calculateRemainingTime(speedBytesPerSec: Long): Long {
  if (speedBytesPerSec == 0L) return Long.MAX_VALUE
  val remainingBytes = totalBytes - downloadedBytes
  return remainingBytes / speedBytesPerSec
 }

 /**
  * download済みサイズ人間 読み すい形式 retrieve
  */
 val downloadedSizeFormatted: String
  get() = formatBytes(downloadedBytes)

 /**
  * 総サイズ人間 読み すい形式 retrieve
  */
 val totalSizeFormatted: String
  get() = formatBytes(totalBytes)

 /**
  * progress率文字列 retrieve（Example: "45%"）
  */
 val progressFormatted: String
  get() = "$progress%"

 companion object {
  /**
   * バイトサイズ人間 読み すい形式 conversion
   *
   * Best Practice: 明示的 Locale.US指定してロケール依存 フォーマット問題回避
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
   * 速度人間 読み すい形式 conversion
   */
  fun formatSpeed(bytesPerSec: Long): String {
   return "${formatBytes(bytesPerSec)}/s"
  }

  /**
   * 残り時間人間 読み すい形式 conversion
   */
  fun formatRemainingTime(seconds: Long): String {
   if (seconds == Long.MAX_VALUE) return "不明"
   val hours = seconds / 3600
   val minutes = (seconds % 3600) / 60
   val secs = seconds % 60

   return when {
    hours > 0 -> "${hours}時間${minutes}minutes"
    minutes > 0 -> "${minutes}minutes${secs}秒"
    else -> "${secs}秒"
   }
  }
 }
}

/**
 * downloadstate
 */
enum class DownloadStatus {
 /** downloadwaiting */
 PENDING,

 /** downloadin */
 DOWNLOADING,

 /** pausein */
 PAUSED,

 /** completed */
 COMPLETED,

 /** error */
 FAILED,

 /** cancel */
 CANCELLED;

 /**
  * state 日本語表示
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
  * アクティブなstateかどうか
  */
 val isActive: Boolean
  get() = this == DOWNLOADING || this == PENDING
}

/**
 * installationstate (downloadcompleted後)
 */
enum class InstallationStatus {
 /** 未installation (download みcompleted) */
 NOT_INSTALLED,

 /** installationwaiting */
 PENDING,

 /** installationin */
 INSTALLING,

 /** installationcompleted */
 INSTALLED,

 /** installationfailure */
 FAILED;

 /**
  * state 日本語表示
  */
 val displayName: String
  get() = when (this) {
   NOT_INSTALLED -> "未installation"
   PENDING -> "waiting"
   INSTALLING -> "installationin"
   INSTALLED -> "installation済み"
   FAILED -> "failure"
  }
}
