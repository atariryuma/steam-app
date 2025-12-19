package com.steamdeck.mobile.domain.repository

import android.net.Uri
import com.steamdeck.mobile.domain.model.FtpConfig
import com.steamdeck.mobile.domain.model.ImportableFile
import com.steamdeck.mobile.domain.model.SmbConfig
import kotlinx.coroutines.flow.Flow

/**
 * fileインポート リポジトリinterface
 */
interface FileImportRepository {

 /**
  * USB OTGデバイスfromfilelistretrieve
  *
  * @param path directorypath（ルート "/"）
  * @return filelist
  */
 suspend fun listUsbFiles(path: String = "/"): Result<List<ImportableFile>>

 /**
  * SMB/CIFS共有fromfilelistretrieve
  *
  * @param config SMBconnectionconfiguration
  * @param path directorypath（共有内 相対path）
  * @return filelist
  */
 suspend fun listSmbFiles(config: SmbConfig, path: String = "/"): Result<List<ImportableFile>>

 /**
  * FTPサーバーfromfilelistretrieve
  *
  * @param config FTPconnectionconfiguration
  * @param path directorypath
  * @return filelist
  */
 suspend fun listFtpFiles(config: FtpConfig, path: String = "/"): Result<List<ImportableFile>>

 /**
  * ローカルストレージ (SAF) fromfileretrieve
  *
  * @param uri ユーザー 選択したdirectoryURI
  * @return filelist
  */
 suspend fun listLocalFiles(uri: Uri): Result<List<ImportableFile>>

 /**
  * fileインポート（copy）
  *
  * @param file インポート元file
  * @param destinationPath インポート先path（内部ストレージ）
  * @return progress状況 Flow
  */
 fun importFile(file: ImportableFile, destinationPath: String): Flow<ImportProgress>

 /**
  * USB OTGデバイス connectionされているかconfirmation
  *
  * @return connectionされているcasetrue
  */
 suspend fun isUsbDeviceConnected(): Boolean

 /**
  * SMB/CIFSconnectionテスト
  *
  * @param config SMBconnectionconfiguration
  * @return connectionsuccess casetrue
  */
 suspend fun testSmbConnection(config: SmbConfig): Result<Boolean>

 /**
  * FTPconnectionテスト
  *
  * @param config FTPconnectionconfiguration
  * @return connectionsuccess casetrue
  */
 suspend fun testFtpConnection(config: FtpConfig): Result<Boolean>
}

/**
 * インポートprogressstate
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
