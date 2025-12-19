package com.steamdeck.mobile.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
// TODO: Re-enable when libaums v0.10.0 migration is complete
// import com.github.mjdev.libaums.UsbMassStorageDevice
// import com.github.mjdev.libaums.fs.UsbFile
import com.steamdeck.mobile.domain.model.FtpConfig
import com.steamdeck.mobile.domain.model.ImportSource
import com.steamdeck.mobile.domain.model.ImportableFile
import com.steamdeck.mobile.domain.model.SmbConfig
import com.steamdeck.mobile.domain.repository.FileImportRepository
import com.steamdeck.mobile.domain.repository.ImportProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ファイルインポートリポジトリの実装
 */
@Singleton
class FileImportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FileImportRepository {

    companion object {
        private const val TAG = "FileImportRepository"
        private const val BUFFER_SIZE = 8192
    }

    // TODO: Re-enable when libaums v0.10.0 migration is complete
    // private var currentUsbDevice: UsbMassStorageDevice? = null
    private var cifsContext: CIFSContext? = null

    // ================================================================================
    // USB OTG Implementation
    // ================================================================================

    override suspend fun isUsbDeviceConnected(): Boolean = withContext(Dispatchers.IO) {
        // TODO: Re-implement when libaums v0.10.0 migration is complete
        Log.w(TAG, "USB support temporarily disabled")
        false
    }

    override suspend fun listUsbFiles(path: String): Result<List<ImportableFile>> =
        withContext(Dispatchers.IO) {
            // TODO: Re-implement when libaums v0.10.0 migration is complete
            Log.w(TAG, "USB file listing temporarily disabled")
            Result.failure(Exception("USB support is temporarily disabled during library migration"))
        }

    /* TODO: Re-enable when libaums v0.10.0 migration is complete
    private fun findUsbDirectory(root: UsbFile, path: String): UsbFile? {
        if (path.isEmpty()) return root

        val parts = path.split('/')
        var current = root

        for (part in parts) {
            if (part.isEmpty()) continue
            current = current.listFiles().find { it.name == part && it.isDirectory }
                ?: return null
        }

        return current
    }
    */

    // ================================================================================
    // SMB/CIFS Implementation
    // ================================================================================

    override suspend fun testSmbConnection(config: SmbConfig): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val cifsContext = createCifsContext(config)
                val smbFile = SmbFile(config.toUrl(), cifsContext)
                smbFile.exists() // Test connection
                Result.success(true)
            } catch (e: Exception) {
                Log.e(TAG, "SMB connection test failed", e)
                Result.failure(e)
            }
        }

    override suspend fun listSmbFiles(
        config: SmbConfig,
        path: String
    ): Result<List<ImportableFile>> = withContext(Dispatchers.IO) {
        try {
            val cifsContext = createCifsContext(config)
            val url = config.toUrl() + path.trim('/')
            val smbFile = SmbFile(url, cifsContext)

            if (!smbFile.exists()) {
                return@withContext Result.failure(Exception("ディレクトリが見つかりません: $path"))
            }

            if (!smbFile.isDirectory) {
                return@withContext Result.failure(Exception("パスがディレクトリではありません: $path"))
            }

            val files = smbFile.listFiles().map { file ->
                ImportableFile(
                    name = file.name.trim('/'),
                    path = file.path,
                    size = file.length(),
                    isDirectory = file.isDirectory,
                    source = ImportSource.SMB_CIFS,
                    lastModified = file.lastModified
                )
            }

            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list SMB files", e)
            Result.failure(e)
        }
    }

    private fun createCifsContext(config: SmbConfig): CIFSContext {
        // Return cached context if available
        cifsContext?.let { return it }

        val properties = Properties()
        // Use SMB2/3 for Windows 10/11 compatibility
        properties.setProperty("jcifs.smb.client.minVersion", "SMB202")
        properties.setProperty("jcifs.smb.client.maxVersion", "SMB311")
        properties.setProperty("jcifs.smb.client.dfs.disabled", "true")

        val baseContext = BaseContext(PropertyConfiguration(properties))

        val auth = if (config.username.isNotEmpty()) {
            NtlmPasswordAuthenticator(
                config.domain.ifEmpty { null },
                config.username,
                config.password
            )
        } else {
            null
        }

        val newContext = if (auth != null) {
            baseContext.withCredentials(auth)
        } else {
            baseContext
        }

        cifsContext = newContext
        return newContext
    }

    // ================================================================================
    // FTP Implementation
    // ================================================================================

    override suspend fun testFtpConnection(config: FtpConfig): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val ftpClient = if (config.useTLS) FTPSClient() else FTPClient()
                ftpClient.connect(config.host, config.port)
                val success = ftpClient.login(config.username, config.password)
                ftpClient.disconnect()
                Result.success(success)
            } catch (e: Exception) {
                Log.e(TAG, "FTP connection test failed", e)
                Result.failure(e)
            }
        }

    override suspend fun listFtpFiles(
        config: FtpConfig,
        path: String
    ): Result<List<ImportableFile>> = withContext(Dispatchers.IO) {
        var ftpClient: FTPClient? = null
        try {
            ftpClient = if (config.useTLS) FTPSClient() else FTPClient()
            ftpClient.connect(config.host, config.port)

            if (!ftpClient.login(config.username, config.password)) {
                return@withContext Result.failure(Exception("FTP ログインに失敗しました"))
            }

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
            if (config.passiveMode) {
                ftpClient.enterLocalPassiveMode()
            }

            val ftpFiles = ftpClient.listFiles(path)
            val files = ftpFiles.map { ftpFile ->
                ImportableFile(
                    name = ftpFile.name,
                    path = "$path/${ftpFile.name}",
                    size = ftpFile.size,
                    isDirectory = ftpFile.isDirectory,
                    source = ImportSource.FTP,
                    lastModified = ftpFile.timestamp?.timeInMillis ?: 0L
                )
            }

            ftpClient.logout()
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list FTP files", e)
            Result.failure(e)
        } finally {
            ftpClient?.disconnect()
        }
    }

    // ================================================================================
    // Local Storage (SAF) Implementation
    // ================================================================================

    override suspend fun listLocalFiles(uri: Uri): Result<List<ImportableFile>> =
        withContext(Dispatchers.IO) {
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    uri,
                    DocumentsContract.getTreeDocumentId(uri)
                )

                val files = mutableListOf<ImportableFile>()
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(0)
                        val name = cursor.getString(1)
                        val size = cursor.getLong(2)
                        val mimeType = cursor.getString(3)

                        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)

                        files.add(
                            ImportableFile(
                                name = name,
                                path = docUri.toString(),
                                size = size,
                                isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                                source = ImportSource.LOCAL
                            )
                        )
                    }
                }

                Result.success(files)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list local files", e)
                Result.failure(e)
            }
        }

    // ================================================================================
    // File Import Implementation
    // ================================================================================

    override fun importFile(
        file: ImportableFile,
        destinationPath: String
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress.Preparing("ファイルを準備中..."))

        try {
            val destFile = File(destinationPath)
            destFile.parentFile?.mkdirs()

            val startTime = System.currentTimeMillis()
            var lastProgressTime = startTime
            var lastTransferred = 0L

            when (file.source) {
                ImportSource.USB_OTG -> {
                    // TODO: Re-enable when libaums v0.10.0 migration is complete
                    throw NotImplementedError("USB copy is temporarily disabled during library migration")
                }

                ImportSource.SMB_CIFS -> {
                    copyFromSmb(file, destFile) { transferred, total ->
                        val currentTime = System.currentTimeMillis()
                        val elapsedMs = currentTime - lastProgressTime
                        if (elapsedMs >= 500) {
                            val speed = ((transferred - lastTransferred) * 1000) / elapsedMs
                            emit(ImportProgress.Copying(transferred, total, speed))
                            lastProgressTime = currentTime
                            lastTransferred = transferred
                        }
                    }
                }

                ImportSource.FTP -> {
                    copyFromFtp(file, destFile) { transferred, total ->
                        val currentTime = System.currentTimeMillis()
                        val elapsedMs = currentTime - lastProgressTime
                        if (elapsedMs >= 500) {
                            val speed = ((transferred - lastTransferred) * 1000) / elapsedMs
                            emit(ImportProgress.Copying(transferred, total, speed))
                            lastProgressTime = currentTime
                            lastTransferred = transferred
                        }
                    }
                }

                ImportSource.LOCAL -> {
                    copyFromLocal(file, destFile) { transferred, total ->
                        val currentTime = System.currentTimeMillis()
                        val elapsedMs = currentTime - lastProgressTime
                        if (elapsedMs >= 500) {
                            val speed = ((transferred - lastTransferred) * 1000) / elapsedMs
                            emit(ImportProgress.Copying(transferred, total, speed))
                            lastProgressTime = currentTime
                            lastTransferred = transferred
                        }
                    }
                }
            }

            emit(ImportProgress.Success(destFile.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file", e)
            emit(ImportProgress.Error("インポートに失敗しました: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /* TODO: Re-enable when libaums v0.10.0 migration is complete
    private suspend fun copyFromUsb(
        file: ImportableFile,
        dest: File,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        // TODO: Re-implement when libaums v0.10.0 migration is complete
        throw NotImplementedError("USB copy is temporarily disabled during library migration")
    }
    */

    private suspend fun copyFromSmb(
        file: ImportableFile,
        dest: File,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        val cifsCtx = cifsContext ?: throw IllegalStateException("SMB context not initialized")
        val smbFile = SmbFile(file.path, cifsCtx)

        val totalSize = smbFile.length()
        var transferred = 0L

        smbFile.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    transferred += bytesRead
                    onProgress(transferred, totalSize)
                }
            }
        }
    }

    private suspend fun copyFromFtp(
        file: ImportableFile,
        dest: File,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        // FTP copy implementation requires FTP config to be passed or stored
        // For now, parse config from file path or throw detailed error
        throw NotImplementedError(
            "FTP copy requires FTP configuration (host, port, credentials). " +
            "Please use importFile with FTP config stored in repository context."
        )
    }

    private suspend fun copyFromLocal(
        file: ImportableFile,
        dest: File,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        val uri = Uri.parse(file.path)
        val totalSize = file.size
        var transferred = 0L

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    transferred += bytesRead
                    onProgress(transferred, totalSize)
                }
            }
        } ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")
    }
}
