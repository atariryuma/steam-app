package com.steamdeck.mobile.data.local.database.dao

import androidx.room.*
import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * ダウンロード履歴へのデータアクセスオブジェクト
 */
@Dao
interface DownloadDao {
    /**
     * すべてのダウンロードを取得
     */
    @Query("SELECT * FROM downloads ORDER BY startedTimestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    /**
     * アクティブなダウンロード（ダウンロード中・待機中）を取得
     */
    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY startedTimestamp ASC")
    fun getActiveDownloads(statuses: List<DownloadStatus> = listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PENDING)): Flow<List<DownloadEntity>>

    /**
     * ゲームIDに関連するダウンロードを取得
     */
    @Query("SELECT * FROM downloads WHERE gameId = :gameId ORDER BY startedTimestamp DESC")
    fun getDownloadsByGameId(gameId: Long): Flow<List<DownloadEntity>>

    /**
     * ダウンロードIDでダウンロードを取得
     */
    @Query("SELECT * FROM downloads WHERE id = :downloadId")
    suspend fun getDownloadById(downloadId: Long): DownloadEntity?

    /**
     * ダウンロードを挿入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    /**
     * ダウンロードを更新
     */
    @Update
    suspend fun updateDownload(download: DownloadEntity)

    /**
     * ダウンロードを削除
     */
    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    /**
     * ダウンロード進捗を更新
     */
    @Query("UPDATE downloads SET progress = :progress, downloadedBytes = :downloadedBytes, status = :status WHERE id = :downloadId")
    suspend fun updateDownloadProgress(downloadId: Long, progress: Int, downloadedBytes: Long, status: DownloadStatus)

    /**
     * ダウンロード完了時刻を設定
     */
    @Query("UPDATE downloads SET status = :status, completedTimestamp = :completedTimestamp WHERE id = :downloadId")
    suspend fun markDownloadCompleted(downloadId: Long, status: DownloadStatus, completedTimestamp: Long)

    /**
     * ダウンロードエラーを記録
     */
    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :downloadId")
    suspend fun markDownloadError(downloadId: Long, status: DownloadStatus, errorMessage: String)

    /**
     * 完了したダウンロードを削除
     */
    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteCompletedDownloads(status: DownloadStatus = DownloadStatus.COMPLETED)

    /**
     * すべてのダウンロードを削除
     */
    @Query("DELETE FROM downloads")
    suspend fun deleteAllDownloads()
}
