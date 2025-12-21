package com.steamdeck.mobile.core.steam

import android.util.Log
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
     * @return Result indicating success or failure with extracted file count
     */
    suspend fun extractSteamFiles(targetDir: File): Result<Int> = withContext(Dispatchers.IO) {
        var archive: IInArchive? = null
        var randomAccessFile: RandomAccessFile? = null

        try {
            Log.i(TAG, "Extracting NSIS using 7-Zip-JBinding library (ARM64 compatible)")
            Log.i(TAG, "Source: ${nsisFile.absolutePath}")
            Log.i(TAG, "Target: ${targetDir.absolutePath}")

            // Ensure target directory exists
            targetDir.mkdirs()

            // Open NSIS archive using 7-Zip library
            randomAccessFile = RandomAccessFile(nsisFile, "r")
            val inStream = RandomAccessFileInStream(randomAccessFile)

            // Auto-detect NSIS format (supports LZMA, BZIP2, ZLIB)
            archive = SevenZip.openInArchive(ArchiveFormat.NSIS, inStream)

            val itemCount = archive.numberOfItems
            Log.i(TAG, "Found $itemCount items in NSIS archive")

            var extractedFiles = 0

            // Extract all files
            val callback = object : IArchiveExtractCallback {
                override fun setTotal(total: Long) {
                    Log.d(TAG, "Total bytes to extract: $total")
                }

                override fun setCompleted(completeValue: Long) {
                    // Progress callback (optional)
                }

                override fun getStream(index: Int, extractAskMode: net.sf.sevenzipjbinding.ExtractAskMode): ISequentialOutStream? {
                    val path = archive.getProperty(index, PropID.PATH) as? String
                    val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false

                    if (path == null) {
                        Log.w(TAG, "Skipping item $index (no path)")
                        return null
                    }

                    val outputFile = File(targetDir, path)

                    if (isFolder) {
                        outputFile.mkdirs()
                        Log.d(TAG, "Created directory: ${outputFile.name}")
                        return null
                    }

                    // Ensure parent directory exists
                    outputFile.parentFile?.mkdirs()

                    extractedFiles++
                    Log.d(TAG, "Extracting file $extractedFiles: ${outputFile.name}")

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
                        Log.w(TAG, "Extraction result: $operationResultCode")
                    }
                }
            }

            // Extract all items
            archive.extract(null, false, callback)

            // Verify steam.exe exists
            val steamExe = File(targetDir, "steam.exe")
            if (!steamExe.exists()) {
                Log.w(TAG, "steam.exe not found after extraction. Extracted $extractedFiles files.")
                Log.w(TAG, "Directory contents: ${targetDir.listFiles()?.joinToString { it.name }}")
            } else {
                Log.i(TAG, "Successfully extracted steam.exe (${steamExe.length()} bytes)")
            }

            Log.i(TAG, "NSIS extraction complete: $extractedFiles files extracted")
            Result.success(extractedFiles)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract NSIS installer", e)
            Result.failure(e)
        } finally {
            // Close archive and file handle
            try {
                archive?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing archive", e)
            }

            try {
                randomAccessFile?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing file", e)
            }
        }
    }
}
