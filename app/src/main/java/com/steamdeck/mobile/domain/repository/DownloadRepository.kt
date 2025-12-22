package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * Download management repository interface
 */
interface DownloadRepository {
 /**
  * Retrieve all downloads
  */
 fun getAllDownloads(): Flow<List<Download>>

 /**
  * Retrieve active downloads (downloading or waiting)
  */
 fun getActiveDownloads(): Flow<List<Download>>

 /**
  * Retrieve downloads related to game ID
  */
 fun getDownloadsByGameId(gameId: Long): Flow<List<Download>>

 /**
  * Retrieve download by ID
  */
 suspend fun getDownloadById(downloadId: Long): Download?

 /**
  * Add download
  */
 suspend fun insertDownload(download: Download): Long

 /**
  * Update download
  */
 suspend fun updateDownload(download: Download)

 /**
  * Delete download
  */
 suspend fun deleteDownload(download: Download)

 /**
  * Update download progress
  */
 suspend fun updateDownloadProgress(downloadId: Long, progress: Int, downloadedBytes: Long, status: DownloadStatus)

 /**
  * Mark download as completed
  */
 suspend fun markDownloadCompleted(downloadId: Long, status: DownloadStatus, completedTimestamp: Long)

 /**
  * Record download error
  */
 suspend fun markDownloadError(downloadId: Long, status: DownloadStatus, errorMessage: String)

 /**
  * Delete completed downloads
  */
 suspend fun deleteCompletedDownloads()

 /**
  * Delete all downloads
  */
 suspend fun deleteAllDownloads()
}
