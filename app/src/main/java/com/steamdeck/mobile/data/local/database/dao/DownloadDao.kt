package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * Download history data access object
 */
@Dao
interface DownloadDao {
 /**
  * Get all downloads
  */
 @Query("SELECT * FROM downloads ORDER BY startedTimestamp DESC")
 fun getAllDownloads(): Flow<List<DownloadEntity>>

 /**
  * Get active downloads (downloading/pending)
  */
 @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY startedTimestamp ASC")
 fun getActiveDownloads(statuses: List<DownloadStatus> = listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PENDING)): Flow<List<DownloadEntity>>

 /**
  * Get downloads related to game ID
  */
 @Query("SELECT * FROM downloads WHERE gameId = :gameId ORDER BY startedTimestamp DESC")
 fun getDownloadsByGameId(gameId: Long): Flow<List<DownloadEntity>>

 /**
  * Get download by download ID (Flow version)
  */
 @Query("SELECT * FROM downloads WHERE id = :downloadId")
 fun getDownloadById(downloadId: Long): Flow<DownloadEntity?>

 /**
  * Get download by download ID (direct retrieval)
  */
 @Query("SELECT * FROM downloads WHERE id = :downloadId")
 suspend fun getDownloadByIdDirect(downloadId: Long): DownloadEntity?

 /**
  * Insert download
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertDownload(download: DownloadEntity): Long

 /**
  * Update download
  */
 @Update
 suspend fun updateDownload(download: DownloadEntity)

 /**
  * Delete download by download ID
  */
 @Query("DELETE FROM downloads WHERE id = :downloadId")
 suspend fun deleteDownload(downloadId: Long)


 /**
  * Mark download as completed
  */
 @Query("UPDATE downloads SET status = :status, completedTimestamp = :completedTimestamp WHERE id = :downloadId")
 suspend fun markDownloadCompleted(downloadId: Long, status: DownloadStatus, completedTimestamp: Long)

 /**
  * Record download error
  */
 @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :downloadId")
 suspend fun markDownloadError(downloadId: Long, status: DownloadStatus, errorMessage: String)

 /**
  * Delete completed downloads
  */
 @Query("DELETE FROM downloads WHERE status = :status")
 suspend fun deleteCompletedDownloads(status: DownloadStatus = DownloadStatus.COMPLETED)

 /**
  * Delete all downloads
  */
 @Query("DELETE FROM downloads")
 suspend fun deleteAllDownloads()

 /**
  * Update download status
  */
 @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt WHERE id = :downloadId")
 suspend fun updateDownloadStatus(downloadId: Long, status: DownloadStatus, updatedAt: Long = System.currentTimeMillis())

 /**
  * Update download total bytes
  */
 @Query("UPDATE downloads SET totalBytes = :totalBytes, updatedAt = :updatedAt WHERE id = :downloadId")
 suspend fun updateDownloadTotalBytes(downloadId: Long, totalBytes: Long, updatedAt: Long)

 /**
  * Update download progress (bytes and progress percentage)
  *
  * CRITICAL FIX: All timestamp parameters must be provided by caller to ensure correct runtime values.
  * Default parameter values (System.currentTimeMillis()) are evaluated at compile time, not runtime.
  *
  * Note: progress is NOT calculated automatically. Caller must compute and provide it.
  * progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
  */
 @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, progress = :progress, updatedAt = :updatedAt WHERE id = :downloadId")
 suspend fun updateDownloadProgress(downloadId: Long, downloadedBytes: Long, progress: Int, updatedAt: Long)
}
