package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * ダウンロード管理リポジトリのインターフェース
 */
interface DownloadRepository {
    /**
     * すべてのダウンロードを取得
     */
    fun getAllDownloads(): Flow<List<Download>>

    /**
     * アクティブなダウンロード（ダウンロード中・待機中）を取得
     */
    fun getActiveDownloads(): Flow<List<Download>>

    /**
     * ゲームIDに関連するダウンロードを取得
     */
    fun getDownloadsByGameId(gameId: Long): Flow<List<Download>>

    /**
     * ダウンロードIDでダウンロードを取得
     */
    suspend fun getDownloadById(downloadId: Long): Download?

    /**
     * ダウンロードを追加
     */
    suspend fun insertDownload(download: Download): Long

    /**
     * ダウンロードを更新
     */
    suspend fun updateDownload(download: Download)

    /**
     * ダウンロードを削除
     */
    suspend fun deleteDownload(download: Download)

    /**
     * ダウンロード進捗を更新
     */
    suspend fun updateDownloadProgress(downloadId: Long, progress: Int, downloadedBytes: Long, status: DownloadStatus)

    /**
     * ダウンロード完了を記録
     */
    suspend fun markDownloadCompleted(downloadId: Long, status: DownloadStatus, completedTimestamp: Long)

    /**
     * ダウンロードエラーを記録
     */
    suspend fun markDownloadError(downloadId: Long, status: DownloadStatus, errorMessage: String)

    /**
     * 完了したダウンロードを削除
     */
    suspend fun deleteCompletedDownloads()

    /**
     * すべてのダウンロードを削除
     */
    suspend fun deleteAllDownloads()
}
