package com.steamdeck.mobile.core.steam

import com.steamdeck.mobile.core.logging.AppLogger
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * NSIS extractor using 7-Zip-JBinding-4Android library
 *
 * This class extracts files from NSIS installers using the 7-Zip-JBinding library.
 * Supports all NSIS compression formats: LZMA, BZIP2, ZLIB/Deflate
 *
 * ARM64 compatible - uses 7-Zip-JBinding-4Android with ARM64 support
 */
class NsisExtractor(private val nsisFile: File) {

    companion object {
        private const val TAG = "NsisExtractor"
    }

    /**
     * Extract Steam client files from NSIS installer
     *
     * @param targetDir Target directory for extracted files
     * @param onProgress Optional progress callback (filesExtracted, totalFiles)
     * @return Result indicating success or failure with extracted file count
     */
    suspend fun extractSteamFiles(
        targetDir: File,
        onProgress: ((filesExtracted: Int, totalFiles: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        var archive: IInArchive? = null
        var randomAccessFile: RandomAccessFile? = null

        try {
            AppLogger.i(TAG, "Extracting NSIS using 7-Zip-JBinding library (ARM64 compatible)")
            AppLogger.i(TAG, "Source: ${nsisFile.absolutePath}")
            AppLogger.i(TAG, "Target: ${targetDir.absolutePath}")

            // Ensure target directory exists
            targetDir.mkdirs()

            // Open NSIS archive using 7-Zip library
            randomAccessFile = RandomAccessFile(nsisFile, "r")
            val inStream = RandomAccessFileInStream(randomAccessFile)

            // Auto-detect NSIS format (supports LZMA, BZIP2, ZLIB)
            archive = SevenZip.openInArchive(ArchiveFormat.NSIS, inStream)

            val itemCount = archive.numberOfItems
            AppLogger.i(TAG, "Found $itemCount items in NSIS archive")

            var extractedFiles = 0

            // Extract all files
            val callback = object : IArchiveExtractCallback {
                override fun setTotal(total: Long) {
                    AppLogger.d(TAG, "Total bytes to extract: $total")
                }

                override fun setCompleted(completeValue: Long) {
                    // Note: completeValue is bytes processed, not file count
                    // We use extractedFiles counter for accurate file progress
                }

                override fun getStream(index: Int, extractAskMode: net.sf.sevenzipjbinding.ExtractAskMode): ISequentialOutStream? {
                    val path = archive.getProperty(index, PropID.PATH) as? String
                    val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false

                    if (path == null) {
                        AppLogger.w(TAG, "Skipping item $index (no path)")
                        return null
                    }

                    val outputFile = File(targetDir, path)

                    if (isFolder) {
                        outputFile.mkdirs()
                        AppLogger.d(TAG, "Created directory: ${outputFile.name}")
                        return null
                    }

                    // Ensure parent directory exists
                    outputFile.parentFile?.mkdirs()

                    extractedFiles++

                    // Report progress after incrementing counter
                    onProgress?.invoke(extractedFiles, itemCount)

                    AppLogger.d(TAG, "Extracting file $extractedFiles/$itemCount: ${outputFile.name}")

                    // Return output stream for file data
                    val fileOutputStream = FileOutputStream(outputFile)
                    return object : ISequentialOutStream {
                        override fun write(data: ByteArray): Int {
                            fileOutputStream.write(data)
                            return data.size
                        }
                    }
                }

                override fun prepareOperation(extractAskMode: net.sf.sevenzipjbinding.ExtractAskMode) {
                    // Called before extraction starts
                }

                override fun setOperationResult(operationResultCode: net.sf.sevenzipjbinding.ExtractOperationResult) {
                    // Called after extraction completes
                    if (operationResultCode != net.sf.sevenzipjbinding.ExtractOperationResult.OK) {
                        AppLogger.w(TAG, "Extraction result: $operationResultCode")
                    }
                }
            }

            // Extract all items
            archive.extract(null, false, callback)

            // Verify Steam.exe exists (case-sensitive on Android)
            val steamExe = File(targetDir, "Steam.exe")
            if (!steamExe.exists()) {
                AppLogger.w(TAG, "Steam.exe not found after extraction. Extracted $extractedFiles files.")
                AppLogger.w(TAG, "Directory contents: ${targetDir.listFiles()?.joinToString { it.name }}")
            } else {
                AppLogger.i(TAG, "Successfully extracted Steam.exe (${steamExe.length()} bytes)")
            }

            AppLogger.i(TAG, "NSIS extraction complete: $extractedFiles files extracted")
            Result.success(extractedFiles)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to extract NSIS installer", e)
            Result.failure(e)
        } finally {
            // Close archive and file handle
            try {
                archive?.close()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error closing archive", e)
            }

            try {
                randomAccessFile?.close()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error closing file", e)
            }
        }
    }
}
