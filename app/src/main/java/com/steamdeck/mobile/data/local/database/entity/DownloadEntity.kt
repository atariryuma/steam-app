package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * download履歴・state格納doエンティティ
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

 /** 関連dogameID */
 val gameId: Long? = null,

 /** File name */
 val fileName: String,

 /** downloadURL */
 val url: String,

 /** downloadstate */
 val status: DownloadStatus,

 /** installationstate (downloadcompleted後 processing) */
 val installationStatus: InstallationStatus = InstallationStatus.NOT_INSTALLED,

 /** progress率（0-100） */
 val progress: Int = 0,

 /** download済みバイト数 */
 val downloadedBytes: Long = 0,

 /** 総fileサイズ（バイト） */
 val totalBytes: Long = 0,

 /** download速度（バイト/秒） */
 val speedBytesPerSecond: Long = 0,

 /** Destination path */
 val destinationPath: String = "",

 /**
  * downloadstartdate and time（Unix timestamp）
  *
  * Note: defaultvalue して System.currentTimeMillis() useしています 、
  * 実際 利用時 明示的 value渡すこ 推奨do。
  */
 val startedTimestamp: Long = System.currentTimeMillis(),

 /**
  * createdate and time（Unix timestamp）
  *
  * Note: defaultvalue して System.currentTimeMillis() useしています 、
  * 実際 利用時 明示的 value渡すこ 推奨do。
  */
 val createdAt: Long = System.currentTimeMillis(),

 /**
  * updatedate and time（Unix timestamp）
  *
  * Note: defaultvalue して System.currentTimeMillis() useしています 、
  * 実際 利用時 明示的 value渡すこ 推奨do。
  */
 val updatedAt: Long = System.currentTimeMillis(),

 /** downloadcompleteddate and time（Unix timestamp） */
 val completedTimestamp: Long? = null,

 /** errorメッセージ */
 val errorMessage: String? = null
)

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
 CANCELLED
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
 FAILED
}
