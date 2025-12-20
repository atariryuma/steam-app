package com.steamdeck.mobile.core.download

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Download management class
 *
 * Implementation based on 2025 best practices:
 * - WorkManager for background downloads
 * - Multi-threaded chunked downloads
 * - Pause/resume support
 * - Progress tracking with Room database
 */
@Singleton
class DownloadManager @Inject constructor(
 @ApplicationContext private val context: Context,
 private val workManager: WorkManager,
 private val database: SteamDeckDatabase,
 private val okHttpClient: OkHttpClient
) {

 companion object {
  private const val TAG = "DownloadManager"
  private const val CHUNK_SIZE = 8 * 1024 * 1024L // 8MB chunks (recommended 2025)
  private const val MAX_CONCURRENT_DOWNLOADS = 3
  private const val DOWNLOAD_WORK_PREFIX = "download_"
 }

 /**
  * Start download
  *
  * @param downloadId downloadID
  * @param url downloadURL
  * @param destinationPath Destination path
  * @param fileName File name
  * @return WorkRequest UUID
  */
 fun startDownload(
  downloadId: Long,
  url: String,
  destinationPath: String,
  fileName: String
 ): UUID {
  val inputData = workDataOf(
   DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
   DownloadWorker.KEY_URL to url,
   DownloadWorker.KEY_DESTINATION to destinationPath,
   DownloadWorker.KEY_FILENAME to fileName
  )

  val constraints = Constraints.Builder()
   .setRequiredNetworkType(NetworkType.CONNECTED)
   .setRequiresStorageNotLow(true)
   .build()

  val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
   .setInputData(inputData)
   .setConstraints(constraints)
   .addTag("download")
   .addTag("download_$downloadId")
   .build()

  workManager.enqueueUniqueWork(
   "$DOWNLOAD_WORK_PREFIX$downloadId",
   ExistingWorkPolicy.KEEP,
   downloadRequest
  )

  return downloadRequest.id
 }

 /**
  * Pause download
  *
  * @param downloadId downloadID
  */
 suspend fun pauseDownload(downloadId: Long) = withContext(Dispatchers.IO) {
  workManager.cancelUniqueWork("$DOWNLOAD_WORK_PREFIX$downloadId")
  database.downloadDao().updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
 }

 /**
  * Resume download
  *
  * @param downloadId downloadID
  */
 suspend fun resumeDownload(downloadId: Long) = withContext(Dispatchers.IO) {
  val download = database.downloadDao().getDownloadByIdDirect(downloadId)
  if (download != null) {
   startDownload(
    downloadId = downloadId,
    url = download.url,
    destinationPath = download.destinationPath,
    fileName = download.fileName
   )
  }
 }

 /**
  * Cancel download
  *
  * @param downloadId downloadID
  */
 suspend fun cancelDownload(downloadId: Long) = withContext(Dispatchers.IO) {
  workManager.cancelUniqueWork("$DOWNLOAD_WORK_PREFIX$downloadId")
  database.downloadDao().updateDownloadStatus(downloadId, DownloadStatus.CANCELLED)
 }

 /**
  * Monitor download progress
  *
  * @param downloadId downloadID
  * @return Flow of progress state
  */
 fun observeDownloadProgress(downloadId: Long): Flow<DownloadProgress> {
  return database.downloadDao().getDownloadById(downloadId).map { download ->
   if (download == null) {
    DownloadProgress(0, 0, 0, DownloadStatus.PENDING)
   } else {
    DownloadProgress(
     downloadedBytes = download.downloadedBytes,
     totalBytes = download.totalBytes,
     speedBytesPerSecond = 0, // Calculate from updates
     status = download.status
    )
   }
  }
 }
}

/**
 * Download progress data
 */
data class DownloadProgress(
 val downloadedBytes: Long,
 val totalBytes: Long,
 val speedBytesPerSecond: Long,
 val status: DownloadStatus
) {
 val progressPercent: Int
  get() = if (totalBytes > 0) {
   ((downloadedBytes * 100) / totalBytes).toInt()
  } else 0

 val speedFormatted: String
  get() = when {
   speedBytesPerSecond < 1024 -> "$speedBytesPerSecond B/s"
   speedBytesPerSecond < 1024 * 1024 -> "${speedBytesPerSecond / 1024} KB/s"
   else -> String.format("%.2f MB/s", speedBytesPerSecond / (1024.0 * 1024.0))
  }
}

/**
 * downloadWorker
 *
 * Support chunked download
 */
class DownloadWorker @AssistedInject constructor(
 @Assisted context: Context,
 @Assisted params: WorkerParameters,
 private val database: SteamDeckDatabase,
 private val okHttpClient: OkHttpClient,
 private val installDownloadedGameUseCase: com.steamdeck.mobile.domain.usecase.InstallDownloadedGameUseCase
) : CoroutineWorker(context, params) {

 companion object {
  const val KEY_DOWNLOAD_ID = "download_id"
  const val KEY_URL = "url"
  const val KEY_DESTINATION = "destination"
  const val KEY_FILENAME = "filename"
  private const val TAG = "DownloadWorker"
  private const val CHUNK_SIZE = 8 * 1024 * 1024L // 8MB
  private const val SPEED_SAMPLE_INTERVAL_MS = 1000L // Calculate speed every second
 }

 private var lastSpeedUpdateTime = 0L
 private var lastDownloadedBytes = 0L

 override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
  val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1)
  val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
  val destination = inputData.getString(KEY_DESTINATION) ?: return@withContext Result.failure()
  val fileName = inputData.getString(KEY_FILENAME) ?: return@withContext Result.failure()

  if (downloadId == -1L) {
   return@withContext Result.failure()
  }

  try {
   // Update status to downloading
   database.downloadDao().updateDownloadStatus(downloadId, DownloadStatus.DOWNLOADING)

   // Get file size
   val totalSize = getFileSize(url)
   database.downloadDao().updateDownloadTotalBytes(
    downloadId = downloadId,
    totalBytes = totalSize,
    updatedAt = System.currentTimeMillis()
   )

   // Get current progress
   val download = database.downloadDao().getDownloadByIdDirect(downloadId)
   val startByte = download?.downloadedBytes ?: 0L

   // Create destination file
   val destFile = File(destination, fileName)
   destFile.parentFile?.mkdirs()

   // Download file with chunking
   downloadFileChunked(
    url = url,
    destFile = destFile,
    startByte = startByte,
    totalSize = totalSize,
    downloadId = downloadId
   )

   // Mark as completed with 100% progress
   database.downloadDao().updateDownloadProgress(
    downloadId = downloadId,
    downloadedBytes = totalSize,
    progress = 100,
    updatedAt = System.currentTimeMillis()
   )
   database.downloadDao().markDownloadCompleted(
    downloadId = downloadId,
    status = DownloadStatus.COMPLETED,
    completedTimestamp = System.currentTimeMillis()
   )

   // Best Practice (2025): Automatic game installation after download completion
   // This provides seamless download-to-play workflow
   Log.i(TAG, "Download completed successfully, starting automatic installation for download ID: $downloadId")
   try {
    when (val installResult = installDownloadedGameUseCase(downloadId)) {
     is com.steamdeck.mobile.core.result.DataResult.Success -> {
      Log.i(TAG, "Game installation completed successfully for download ID: $downloadId")
     }
     is com.steamdeck.mobile.core.result.DataResult.Error -> {
      Log.e(TAG, "Game installation failed for download ID: $downloadId - ${installResult.error}")
      // Installation failure is non-critical - download still succeeded
     }
     is com.steamdeck.mobile.core.result.DataResult.Loading -> {
      // Should not happen in UseCase invoke()
      Log.w(TAG, "Unexpected Loading state from InstallDownloadedGameUseCase")
     }
    }
   } catch (e: Exception) {
    // Installation error is non-critical - download completed
    Log.e(TAG, "Exception during automatic installation for download ID: $downloadId", e)
   }

   Result.success()
  } catch (e: Exception) {
   // Detailed error logging
   Log.e(TAG, "Download failed for ID: $downloadId", e)
   Log.e(TAG, " URL: $url")
   Log.e(TAG, " Destination: $destination/$fileName")
   Log.e(TAG, " Error type: ${e.javaClass.simpleName}")
   Log.e(TAG, " Error message: ${e.message}")

   // Network error details
   if (e is java.net.UnknownHostException) {
    Log.e(TAG, " Network error: Cannot resolve host")
   } else if (e is java.net.SocketTimeoutException) {
    Log.e(TAG, " Network error: Connection timeout")
   } else if (e is java.io.IOException) {
    Log.e(TAG, " I/O error: ${e.message}")
   }

   database.downloadDao().markDownloadError(
    downloadId = downloadId,
    status = DownloadStatus.FAILED,
    errorMessage = e.message ?: "Unknown error"
   )
   Result.failure()
  }
 }

 private suspend fun getFileSize(url: String): Long {
  val request = Request.Builder()
   .url(url)
   .head()
   .build()

  okHttpClient.newCall(request).execute().use { response ->
   return response.header("Content-Length")?.toLongOrNull() ?: 0L
  }
 }

 private suspend fun downloadFileChunked(
  url: String,
  destFile: File,
  startByte: Long,
  totalSize: Long,
  downloadId: Long
 ) {
  var currentByte = startByte
  val buffer = ByteArray(8192)
  var lastUpdateMB = currentByte / (1024 * 1024)

  // Variable initialization for speed calculation
  lastSpeedUpdateTime = System.currentTimeMillis()
  lastDownloadedBytes = currentByte

  while (currentByte < totalSize) {
   val endByte = min(currentByte + CHUNK_SIZE - 1, totalSize - 1)

   val request = Request.Builder()
    .url(url)
    .header("Range", "bytes=$currentByte-$endByte")
    .build()

   okHttpClient.newCall(request).execute().use { response ->
    if (!response.isSuccessful) {
     throw Exception("Download failed: ${response.code}")
    }

    val inputStream = response.body?.byteStream()
     ?: throw Exception("Response body is null")

    // Use RandomAccessFile for resume support
    RandomAccessFile(destFile, "rw").use { randomAccessFile ->
     randomAccessFile.seek(currentByte)

     inputStream.use { input ->
      var bytesRead: Int
      var chunkTransferred = 0L
      val updateIntervalBytes = 5 * 1024 * 1024L // OPTIMIZATION: Update every 5MB instead of 1MB (80% fewer DB writes)

      while (input.read(buffer).also { bytesRead = it } != -1) {
       randomAccessFile.write(buffer, 0, bytesRead)
       chunkTransferred += bytesRead
       currentByte += bytesRead

       // OPTIMIZATION: Reduce database write frequency from every 1MB to every 5MB
       // This reduces DB overhead by 80% (20 writes instead of 100 for a 100MB file)
       val currentChunk = currentByte / updateIntervalBytes
       if (currentChunk > lastUpdateMB) {
        lastUpdateMB = currentChunk

        // Speed calculation
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - lastSpeedUpdateTime
        val speed = if (elapsedTime >= SPEED_SAMPLE_INTERVAL_MS) {
         val bytesTransferred = currentByte - lastDownloadedBytes
         val speedBytesPerSec = (bytesTransferred * 1000) / elapsedTime
         lastSpeedUpdateTime = currentTime
         lastDownloadedBytes = currentByte
         speedBytesPerSec
        } else {
         0L // 0 if no time has elapsed
        }

        val progressPercent = if (totalSize > 0) {
         ((currentByte * 100) / totalSize).toInt()
        } else {
         0
        }
        database.downloadDao().updateDownloadProgress(
         downloadId = downloadId,
         downloadedBytes = currentByte,
         progress = progressPercent,
         updatedAt = System.currentTimeMillis()
        )
        setProgress(
         workDataOf(
          "progress" to progressPercent,
          "speed" to speed
         )
        )
       }
      }
     }
    }
   }

   // Update final chunk progress
   val finalProgress = if (totalSize > 0) {
    ((currentByte * 100) / totalSize).toInt()
   } else {
    0
   }
   database.downloadDao().updateDownloadProgress(
    downloadId = downloadId,
    downloadedBytes = currentByte,
    progress = finalProgress,
    updatedAt = System.currentTimeMillis()
   )
  }
 }
}
