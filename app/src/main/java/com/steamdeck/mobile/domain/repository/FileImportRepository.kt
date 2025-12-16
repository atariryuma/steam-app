package com.steamdeck.mobile.domain.repository

import android.net.Uri
import com.steamdeck.mobile.domain.model.FtpConfig
import com.steamdeck.mobile.domain.model.ImportableFile
import com.steamdeck.mobile.domain.model.SmbConfig
import kotlinx.coroutines.flow.Flow

/**
 * ファイルインポートのリポジトリインターフェース
 */
interface FileImportRepository {

    /**
     * USB OTGデバイスからファイルを一覧取得
     *
     * @param path ディレクトリパス（ルートは"/"）
     * @return ファイル一覧
     */
    suspend fun listUsbFiles(path: String = "/"): Result<List<ImportableFile>>

    /**
     * SMB/CIFS共有からファイルを一覧取得
     *
     * @param config SMB接続設定
     * @param path ディレクトリパス（共有内の相対パス）
     * @return ファイル一覧
     */
    suspend fun listSmbFiles(config: SmbConfig, path: String = "/"): Result<List<ImportableFile>>

    /**
     * FTPサーバーからファイルを一覧取得
     *
     * @param config FTP接続設定
     * @param path ディレクトリパス
     * @return ファイル一覧
     */
    suspend fun listFtpFiles(config: FtpConfig, path: String = "/"): Result<List<ImportableFile>>

    /**
     * ローカルストレージ (SAF) からファイルを取得
     *
     * @param uri ユーザーが選択したディレクトリURI
     * @return ファイル一覧
     */
    suspend fun listLocalFiles(uri: Uri): Result<List<ImportableFile>>

    /**
     * ファイルをインポート（コピー）
     *
     * @param file インポート元ファイル
     * @param destinationPath インポート先パス（内部ストレージ）
     * @return 進捗状況のFlow
     */
    fun importFile(file: ImportableFile, destinationPath: String): Flow<ImportProgress>

    /**
     * USB OTGデバイスが接続されているか確認
     *
     * @return 接続されている場合true
     */
    suspend fun isUsbDeviceConnected(): Boolean

    /**
     * SMB/CIFS接続をテスト
     *
     * @param config SMB接続設定
     * @return 接続成功の場合true
     */
    suspend fun testSmbConnection(config: SmbConfig): Result<Boolean>

    /**
     * FTP接続をテスト
     *
     * @param config FTP接続設定
     * @return 接続成功の場合true
     */
    suspend fun testFtpConnection(config: FtpConfig): Result<Boolean>
}

/**
 * インポート進捗状態
 */
sealed class ImportProgress {
    object Idle : ImportProgress()

    data class Preparing(val message: String) : ImportProgress()

    data class Copying(
        val bytesTransferred: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Long
    ) : ImportProgress() {
        val progressPercent: Int
            get() = if (totalBytes > 0) {
                ((bytesTransferred * 100) / totalBytes).toInt()
            } else 0

        val speedFormatted: String
            get() {
                return when {
                    speedBytesPerSecond < 1024 -> "$speedBytesPerSecond B/s"
                    speedBytesPerSecond < 1024 * 1024 -> "${speedBytesPerSecond / 1024} KB/s"
                    else -> String.format("%.2f MB/s", speedBytesPerSecond / (1024.0 * 1024.0))
                }
            }
    }

    data class Success(val destinationPath: String) : ImportProgress()

    data class Error(val message: String, val cause: Throwable? = null) : ImportProgress()
}
