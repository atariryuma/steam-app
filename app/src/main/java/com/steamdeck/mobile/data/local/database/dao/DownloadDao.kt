package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * download履歴to dataアクセスobject
 */
@Dao
interface DownloadDao {
 /**
  * all downloadretrieve
  */
 @Query("SELECT * FROM downloads ORDER BY startedTimestamp DESC")
 fun getAllDownloads(): Flow<List<DownloadEntity>>

 /**
  * アクティブなdownload（downloadin・waiting）retrieve
  */
 @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY startedTimestamp ASC")
 fun getActiveDownloads(statuses: List<DownloadStatus> = listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PENDING)): Flow<List<DownloadEntity>>

 /**
  * gameID 関連dodownloadretrieve
  */
 @Query("SELECT * FROM downloads WHERE gameId = :gameId ORDER BY startedTimestamp DESC")
 fun getDownloadsByGameId(gameId: Long): Flow<List<DownloadEntity>>

 /**
  * downloadID downloadretrieve（Flow版）
  */
 @Query("SELECT * FROM downloads WHERE id = :downloadId")
 fun getDownloadById(downloadId: Long): Flow<DownloadEntity?>

 /**
  * downloadID downloadretrieve（directlyretrieve）
  */
 @Query("SELECT * FROM downloads WHERE id = :downloadId")
 suspend fun getDownloadByIdDirect(downloadId: Long): DownloadEntity?

 /**
  * download挿入
  */
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertDownload(download: DownloadEntity): Long

 /**
  * downloadupdate
  */
 @Update
 suspend fun updateDownload(download: DownloadEntity)

 /**
  * downloadID downloaddelete
  */
 @Query("DELETE FROM downloads WHERE id = :downloadId")
 suspend fun deleteDownload(downloadId: Long)


 /**
  * downloadcompleted時刻configuration
  */
 @Query("UPDATE downloads SET status = :status, completedTimestamp = :completedTimestamp WHERE id = :downloadId")
 suspend fun markDownloadCompleted(downloadId: Long, status: DownloadStatus, completedTimestamp: Long)

 /**
  * downloaderror記録
  */
 @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :downloadId")
 suspend fun markDownloadError(downloadId: Long, status: DownloadStatus, errorMessage: String)

 /**
  * completedしたdownloaddelete
  */
 @Query("DELETE FROM downloads WHERE status = :status")
 suspend fun deleteCompletedDownloads(status: DownloadStatus = DownloadStatus.COMPLETED)

 /**
  * all downloaddelete
  */
 @Query("DELETE FROM downloads")
 suspend fun deleteAllDownloads()

 /**
  * downloadステータスupdate
  */
 @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt WHERE id = :downloadId")
 suspend fun updateDownloadStatus(downloadId: Long, status: DownloadStatus, updatedAt: Long = System.currentTimeMillis())

 /**
  * download合計バイト数update
  */
 @Query("UPDATE downloads SET totalBytes = :totalBytes, updatedAt = :updatedAt WHERE id = :downloadId")
 suspend fun updateDownloadTotalBytes(downloadId: Long, totalBytes: Long, updatedAt: Long = System.currentTimeMillis())

 /**
  * downloadprogressupdate（バイト数 progress率）
  *
  * Note: progress 自動calculationされません。呼び出し側 calculationして渡す必要 あります。
  * progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
  */
 @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, progress = :progress, updatedAt = :updatedAt WHERE id = :downloadId")
 suspend fun updateDownloadProgress(downloadId: Long, downloadedBytes: Long, progress: Int = 0, updatedAt: Long = System.currentTimeMillis())
}
