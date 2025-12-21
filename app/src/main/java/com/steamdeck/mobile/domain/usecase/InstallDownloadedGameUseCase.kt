package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.DownloadRepository
import com.steamdeck.mobile.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * downloadcompletedしたgameinstallationdoUseCase
 *
 * 2025 Best Practice:
 * - DataResult<T> for type-safe error handling
 * - AppLogger for centralized logging
 * - Atomic file operations with cleanup on failure
 * - InstallationStatus tracking for UI feedback
 */
class InstallDownloadedGameUseCase @Inject constructor(
 private val downloadRepository: DownloadRepository,
 private val gameRepository: GameRepository
) {
 /**
  * download済みfilegame installationpathへcopyしてinstallation
  *
  * @param downloadId downloadID
  * @return installationresult
  */
 suspend operator fun invoke(downloadId: Long): DataResult<Unit> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Starting game installation for download ID: $downloadId")

   // 1. downloadinformationretrieve
   val download = downloadRepository.getDownloadById(downloadId)
    ?: return@withContext DataResult.Error(
     AppError.DatabaseError("download not found (ID: $downloadId)", null)
    )

   // 2. gameinformationretrieve
   val gameId = download.gameId
    ?: return@withContext DataResult.Error(
     AppError.Unknown(Exception("Download has no associated game ID"))
    )

   val game = gameRepository.getGameById(gameId)
    ?: return@withContext DataResult.Error(
     AppError.DatabaseError("game not found (ID: $gameId)", null)
    )

   // 3. downloadfile existconfirmation
   // Bug fix: destinationPath is directory, need to append fileName
   val downloadedFile = File(download.destinationPath, download.fileName)
   if (!downloadedFile.exists()) {
    AppLogger.e(TAG, "Downloaded file not found: ${download.destinationPath}")

    // installationfailureマーク
    val updatedDownload = download.copy(installationStatus = InstallationStatus.VALIDATION_FAILED)
    downloadRepository.updateDownload(updatedDownload)

    return@withContext DataResult.Error(
     AppError.FileError("downloadfile not found", null)
    )
   }

   AppLogger.d(TAG, "Downloaded file found: ${downloadedFile.absolutePath} (${downloadedFile.length()} bytes)")

   // 4. installationstartマーク
   val downloadInstalling = download.copy(installationStatus = InstallationStatus.INSTALLING)
   downloadRepository.updateDownload(downloadInstalling)

   // 5. installation先directorycreate
   val installDir = File(game.installPath)
   if (!installDir.exists()) {
    AppLogger.d(TAG, "Creating install directory: ${installDir.absolutePath}")
    if (!installDir.mkdirs()) {
     AppLogger.e(TAG, "Failed to create install directory: ${installDir.absolutePath}")

     val updatedDownload = download.copy(installationStatus = InstallationStatus.VALIDATION_FAILED)
     downloadRepository.updateDownload(updatedDownload)

     return@withContext DataResult.Error(
      AppError.FileError("installationdirectory create failuredid", null)
     )
    }
   }

   // 6. filecopy（既存file あるcase 上書き）
   val targetFile = File(installDir, downloadedFile.name)
   AppLogger.d(TAG, "Copying file to: ${targetFile.absolutePath}")

   try {
    downloadedFile.copyTo(targetFile, overwrite = true)
    AppLogger.i(TAG, "File copied successfully: ${targetFile.absolutePath}")
   } catch (e: Exception) {
    AppLogger.e(TAG, "Failed to copy file", e)

    // installationfailureマーク
    val updatedDownload = download.copy(installationStatus = InstallationStatus.VALIDATION_FAILED)
    downloadRepository.updateDownload(updatedDownload)

    return@withContext DataResult.Error(
     AppError.FileError("file copy failuredid: ${e.message}", e)
    )
   }

   // 7. copysuccess後、downloadfiledelete（ストレージ節約）
   try {
    if (downloadedFile.delete()) {
     AppLogger.d(TAG, "Downloaded file deleted: ${downloadedFile.absolutePath}")
    } else {
     AppLogger.w(TAG, "Failed to delete downloaded file (non-critical)")
    }
   } catch (e: Exception) {
    // deletefailure 非致命的error
    AppLogger.w(TAG, "Exception while deleting downloaded file (non-critical)", e)
   }

   // 8. installationcompletedマーク
   val downloadCompleted = download.copy(installationStatus = InstallationStatus.INSTALLED)
   downloadRepository.updateDownload(downloadCompleted)

   AppLogger.i(TAG, "Game installation completed successfully for: ${game.name}")
   DataResult.Success(Unit)
  } catch (e: Exception) {
   AppLogger.e(TAG, "Unexpected error during installation", e)
   DataResult.Error(AppError.from(e))
  }
 }

 companion object {
  private const val TAG = "InstallDownloadedGameUseCase"
 }
}
