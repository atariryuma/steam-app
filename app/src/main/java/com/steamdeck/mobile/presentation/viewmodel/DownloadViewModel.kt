package com.steamdeck.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.core.download.DownloadManager
import com.steamdeck.mobile.data.local.database.dao.DownloadDao
import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Download screen ViewModel
 *
 * Best Practices:
 * - StateFlow for UI state management
 * - Lifecycle-aware real-time updates
 * - WorkManager for background downloads
 *
 * References:
 * - https://proandroiddev.com/real-time-lifecycle-aware-updates-in-jetpack-compose-be2e80e613c2
 * - https://developer.android.com/topic/libraries/architecture/workmanager
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
 private val downloadManager: DownloadManager,
 private val downloadDao: DownloadDao
) : ViewModel() {

 /**
  * All downloads list (real-time updates)
  */
 val downloads: StateFlow<List<DownloadEntity>> = downloadDao.getAllDownloads()
  .stateIn(
   scope = viewModelScope,
   started = SharingStarted.WhileSubscribed(5000),
   initialValue = emptyList()
  )

 /**
  * Number of active downloads
  */
 val activeDownloads: StateFlow<Int> = downloads
  .map { list ->
   list.count { download ->
    download.status == DownloadStatus.DOWNLOADING ||
      download.status == DownloadStatus.PENDING
   }
  }
  .stateIn(
   scope = viewModelScope,
   started = SharingStarted.WhileSubscribed(5000),
   initialValue = 0
  )

 /**
  * Pause download
  */
 fun pauseDownload(downloadId: Long) {
  viewModelScope.launch {
   downloadManager.pauseDownload(downloadId)
  }
 }

 /**
  * Resume download
  */
 fun resumeDownload(downloadId: Long) {
  viewModelScope.launch {
   downloadManager.resumeDownload(downloadId)
  }
 }

 /**
  * Cancel download
  */
 fun cancelDownload(downloadId: Long) {
  viewModelScope.launch {
   downloadManager.cancelDownload(downloadId)
  }
 }

 /**
  * Retry download
  */
 fun retryDownload(downloadId: Long) {
  viewModelScope.launch {
   // Retry logic: resume download
   downloadManager.resumeDownload(downloadId)
  }
 }

 /**
  * Clear completed downloads
  */
 fun clearCompleted() {
  viewModelScope.launch {
   val completedDownloads = downloads.value.filter { it.status == DownloadStatus.COMPLETED }
   completedDownloads.forEach { download ->
    downloadDao.deleteDownload(download.id)
   }
  }
 }

 /**
  * Start download (for external calls)
  */
 fun startDownload(
  url: String,
  fileName: String,
  destinationPath: String,
  gameId: Long? = null
 ) {
  viewModelScope.launch {
   val currentTime = System.currentTimeMillis()

   // Insert download record
   val downloadId = downloadDao.insertDownload(
    DownloadEntity(
     id = 0, // Auto-generated
     gameId = gameId,
     fileName = fileName,
     url = url,
     status = DownloadStatus.PENDING,
     downloadedBytes = 0L,
     totalBytes = 0L,
     speedBytesPerSecond = 0L,
     destinationPath = destinationPath,
     startedTimestamp = currentTime,
     createdAt = currentTime,
     updatedAt = currentTime,
     completedTimestamp = null,
     errorMessage = null
    )
   )

   // Start WorkManager download
   downloadManager.startDownload(
    downloadId = downloadId,
    url = url,
    destinationPath = destinationPath,
    fileName = fileName
   )
  }
 }
}
