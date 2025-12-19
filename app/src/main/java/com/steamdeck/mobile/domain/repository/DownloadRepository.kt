package com.steamdeck.mobile.domain.repository

import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * downloadmanagementリポジトリ interface
 */
interface DownloadRepository {
 /**
  * all downloadretrieve
  */
 fun getAllDownloads(): Flow<List<Download>>

 /**
  * アクティブなdownload（downloadin・waiting）retrieve
  */
 fun getActiveDownloads(): Flow<List<Download>>

 /**
  * gameID 関連dodownloadretrieve
  */
 fun getDownloadsByGameId(gameId: Long): Flow<List<Download>>

 /**
  * downloadID downloadretrieve
  */
 suspend fun getDownloadById(downloadId: Long): Download?

 /**
  * downloadadd
  */
 suspend fun insertDownload(download: Download): Long

 /**
  * downloadupdate
  */
 suspend fun updateDownload(download: Download)

 /**
  * downloaddelete
  */
 suspend fun deleteDownload(download: Download)

 /**
  * downloadprogressupdate
  */
 suspend fun updateDownloadProgress(downloadId: Long, progress: Int, downloadedBytes: Long, status: DownloadStatus)

 /**
  * downloadcompleted記録
  */
 suspend fun markDownloadCompleted(downloadId: Long, status: DownloadStatus, completedTimestamp: Long)

 /**
  * downloaderror記録
  */
 suspend fun markDownloadError(downloadId: Long, status: DownloadStatus, errorMessage: String)

 /**
  * completedしたdownloaddelete
  */
 suspend fun deleteCompletedDownloads()

 /**
  * all downloaddelete
  */
 suspend fun deleteAllDownloads()
}
