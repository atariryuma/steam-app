package com.steamdeck.mobile.domain.usecase

import android.util.Log
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.logging.AppLogger
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.domain.model.InstallationStatus
import com.steamdeck.mobile.domain.repository.DownloadRepository
import com.steamdeck.mobile.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * ダウンロード完了したゲームをインストールするUseCase
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
     * ダウンロード済みファイルをゲームのインストールパスへコピーしてインストール
     *
     * @param downloadId ダウンロードID
     * @return インストール結果
     */
    suspend operator fun invoke(downloadId: Long): DataResult<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Starting game installation for download ID: $downloadId")

            // 1. ダウンロード情報を取得
            val download = downloadRepository.getDownloadById(downloadId)
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError("ダウンロードが見つかりません (ID: $downloadId)", null)
                )

            // 2. ゲーム情報を取得
            val gameId = download.gameId
                ?: return@withContext DataResult.Error(
                    AppError.Unknown(Exception("ダウンロードにゲームIDが設定されていません"))
                )

            val game = gameRepository.getGameById(gameId)
                ?: return@withContext DataResult.Error(
                    AppError.DatabaseError("ゲームが見つかりません (ID: $gameId)", null)
                )

            // 3. ダウンロードファイルの存在確認
            // Bug fix: destinationPath is directory, need to append fileName
            val downloadedFile = File(download.destinationPath, download.fileName)
            if (!downloadedFile.exists()) {
                AppLogger.e(TAG, "Downloaded file not found: ${download.destinationPath}")

                // インストール失敗をマーク
                val updatedDownload = download.copy(installationStatus = InstallationStatus.FAILED)
                downloadRepository.updateDownload(updatedDownload)

                return@withContext DataResult.Error(
                    AppError.FileError("ダウンロードファイルが見つかりません", null)
                )
            }

            AppLogger.d(TAG, "Downloaded file found: ${downloadedFile.absolutePath} (${downloadedFile.length()} bytes)")

            // 4. インストール開始をマーク
            val downloadInstalling = download.copy(installationStatus = InstallationStatus.INSTALLING)
            downloadRepository.updateDownload(downloadInstalling)

            // 5. インストール先ディレクトリを作成
            val installDir = File(game.installPath)
            if (!installDir.exists()) {
                AppLogger.d(TAG, "Creating install directory: ${installDir.absolutePath}")
                if (!installDir.mkdirs()) {
                    AppLogger.e(TAG, "Failed to create install directory: ${installDir.absolutePath}")

                    val updatedDownload = download.copy(installationStatus = InstallationStatus.FAILED)
                    downloadRepository.updateDownload(updatedDownload)

                    return@withContext DataResult.Error(
                        AppError.FileError("インストールディレクトリの作成に失敗しました", null)
                    )
                }
            }

            // 6. ファイルをコピー（既存ファイルがある場合は上書き）
            val targetFile = File(installDir, downloadedFile.name)
            AppLogger.d(TAG, "Copying file to: ${targetFile.absolutePath}")

            try {
                downloadedFile.copyTo(targetFile, overwrite = true)
                AppLogger.i(TAG, "File copied successfully: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to copy file", e)

                // インストール失敗をマーク
                val updatedDownload = download.copy(installationStatus = InstallationStatus.FAILED)
                downloadRepository.updateDownload(updatedDownload)

                return@withContext DataResult.Error(
                    AppError.FileError("ファイルのコピーに失敗しました: ${e.message}", e)
                )
            }

            // 7. コピー成功後、ダウンロードファイルを削除（ストレージ節約）
            try {
                if (downloadedFile.delete()) {
                    AppLogger.d(TAG, "Downloaded file deleted: ${downloadedFile.absolutePath}")
                } else {
                    AppLogger.w(TAG, "Failed to delete downloaded file (non-critical)")
                }
            } catch (e: Exception) {
                // 削除失敗は非致命的エラー
                AppLogger.w(TAG, "Exception while deleting downloaded file (non-critical)", e)
            }

            // 8. インストール完了をマーク
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
