package com.steamdeck.mobile.core.winlator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for decompressing tar-based archives (.txz, .tzst).
 *
 * Zstandard (.tzst) support is provided by Apache Commons Compress with zstd-jni.
 *
 * Reference: https://commons.apache.org/proper/commons-compress/
 */
@Singleton
class ZstdDecompressor @Inject constructor() {

    companion object {
        private const val TAG = "ZstdDecompressor"
        private const val BUFFER_SIZE = 8192 // 8KB buffer
    }

    /**
     * Decompresses a .tzst file to a .tar file.
     *
     * @param inputFile .tzst compressed file
     * @param outputFile Output .tar file
     * @param progressCallback Optional progress callback (0.0 to 1.0)
     * @return Result indicating success or failure
     */
    suspend fun decompress(
        inputFile: File,
        outputFile: File,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        Result.failure(
            UnsupportedOperationException(
                "Zstandard decompression is temporarily unavailable. " +
                "Please use XZ-compressed archives (.txz) instead. " +
                "See extractTxz() method."
            )
        )
    }

    /**
     * Decompresses and extracts a .tzst archive in one step.
     *
     * Uses Apache Commons Compress with zstd-jni for Zstandard decompression.
     *
     * @param tzstFile .tzst compressed archive
     * @param targetDir Directory to extract to
     * @param progressCallback Optional progress callback (0.0 to 1.0)
     * @return Result with extracted directory
     */
    suspend fun decompressAndExtract(
        tzstFile: File,
        targetDir: File,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!tzstFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Input file not found: ${tzstFile.absolutePath}")
                )
            }

            targetDir.mkdirs()

            val tzstFileSize = tzstFile.length()
            var bytesProcessed = 0L

            Log.i(TAG, "Extracting tzst archive: ${tzstFile.name} (${tzstFileSize / 1024}KB)")
            progressCallback?.invoke(0.0f, "Extracting ${tzstFile.name}...")

            BufferedInputStream(FileInputStream(tzstFile)).use { bufferedInput ->
                ZstdCompressorInputStream(bufferedInput).use { zstdInput ->
                    TarArchiveInputStream(zstdInput).use { tarInput ->
                        var entry: TarArchiveEntry? = tarInput.nextEntry as TarArchiveEntry?

                        while (entry != null) {
                            val outputFile = File(targetDir, entry.name)

                            // Security: Prevent path traversal attacks
                            if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                                Log.w(TAG, "Skipping suspicious entry: ${entry.name}")
                                entry = tarInput.nextEntry as TarArchiveEntry?
                                continue
                            }

                            if (entry.isDirectory) {
                                // Create directory
                                outputFile.mkdirs()
                                Log.d(TAG, "Created directory: ${entry.name}")
                            } else if (entry.isSymbolicLink) {
                                // Skip symlinks (not supported on Android)
                                Log.d(TAG, "Skipping symlink: ${entry.name} -> ${entry.linkName}")
                            } else {
                                // Extract file
                                outputFile.parentFile?.mkdirs()

                                FileOutputStream(outputFile).use { output ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var len: Int
                                    while (tarInput.read(buffer).also { len = it } > 0) {
                                        output.write(buffer, 0, len)
                                        bytesProcessed += len
                                    }
                                }

                                // Set executable permissions if present
                                val mode = entry.mode
                                val isExecutable = (mode and 0x49) != 0 // Check execute bits

                                if (isExecutable) {
                                    outputFile.setExecutable(true, false)
                                    Log.d(TAG, "Set executable: ${entry.name} (mode: ${mode.toString(8)})")
                                }

                                Log.d(TAG, "Extracted: ${entry.name} (${entry.size} bytes)")
                            }

                            // Report progress (estimate 10x compression ratio)
                            val progress = (bytesProcessed.toFloat() / (tzstFileSize * 10)).coerceIn(0f, 0.95f)
                            progressCallback?.invoke(progress, "Extracting...")

                            entry = tarInput.nextEntry as TarArchiveEntry?
                        }
                    }
                }
            }

            Log.i(TAG, "Tzst extraction complete: ${targetDir.absolutePath}")
            progressCallback?.invoke(1.0f, "Extraction complete")
            Result.success(targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Tzst extraction failed", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts a .tar archive using Apache Commons Compress.
     *
     * Preserves Unix file permissions including executable bits.
     * Based on best practices from Apache Commons Compress documentation.
     *
     * @param tarFile .tar archive
     * @param targetDir Target directory
     * @param progressCallback Optional progress callback
     * @return Result with target directory
     */
    private suspend fun extractTar(
        tarFile: File,
        targetDir: File,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetDir.mkdirs()

            val tarFileSize = tarFile.length()
            var bytesProcessed = 0L

            Log.i(TAG, "Extracting tar archive: ${tarFile.name} (${tarFileSize / 1024}KB)")

            BufferedInputStream(FileInputStream(tarFile)).use { bufferedInput ->
                TarArchiveInputStream(bufferedInput).use { tarInput ->
                    var entry: TarArchiveEntry? = tarInput.nextEntry as TarArchiveEntry?

                    while (entry != null) {
                        val outputFile = File(targetDir, entry.name)

                        // Security: Prevent path traversal attacks
                        if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                            Log.w(TAG, "Skipping suspicious entry: ${entry.name}")
                            entry = tarInput.nextEntry as TarArchiveEntry?
                            continue
                        }

                        if (entry.isDirectory) {
                            // Create directory
                            outputFile.mkdirs()
                            Log.d(TAG, "Created directory: ${entry.name}")
                        } else {
                            // Extract file
                            outputFile.parentFile?.mkdirs()

                            FileOutputStream(outputFile).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var len: Int
                                while (tarInput.read(buffer).also { len = it } > 0) {
                                    output.write(buffer, 0, len)
                                    bytesProcessed += len
                                }
                            }

                            // Set executable permissions if present
                            // Unix mode: 0755 (rwxr-xr-x) or 0777 (rwxrwxrwx) indicates executable
                            val mode = entry.mode
                            val isExecutable = (mode and 0x49) != 0 // Check owner/group/other execute bits

                            if (isExecutable) {
                                outputFile.setExecutable(true, false)
                                Log.d(TAG, "Set executable: ${entry.name} (mode: ${mode.toString(8)})")
                            }

                            Log.d(TAG, "Extracted: ${entry.name} (${entry.size} bytes)")
                        }

                        // Report progress
                        val progress = (bytesProcessed.toFloat() / tarFileSize).coerceIn(0f, 1f)
                        progressCallback?.invoke(progress)

                        entry = tarInput.nextEntry as TarArchiveEntry?
                    }
                }
            }

            Log.i(TAG, "Tar extraction complete: ${targetDir.absolutePath}")
            progressCallback?.invoke(1.0f)
            Result.success(targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Tar extraction failed", e)
            Result.failure(e)
        }
    }

    /**
     * Gets the decompressed size estimate.
     *
     * @param tzstFile .tzst file
     * @return Estimated decompressed size in bytes, or null if cannot determine
     */
    fun getDecompressedSize(tzstFile: File): Long? {
        Log.w(TAG, "Zstandard decompression is temporarily unavailable")
        return null
    }

    /**
     * Extracts a .txz (tar + xz) archive.
     *
     * Winlator's rootfs uses XZ compression instead of Zstandard.
     *
     * @param txzFile .txz compressed archive
     * @param targetDir Directory to extract to
     * @param progressCallback Optional progress callback (0.0 to 1.0, status message)
     * @return Result with extracted directory
     */
    suspend fun extractTxz(
        txzFile: File,
        targetDir: File,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!txzFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Input file not found: ${txzFile.absolutePath}")
                )
            }

            targetDir.mkdirs()

            val txzFileSize = txzFile.length()
            var bytesProcessed = 0L

            Log.i(TAG, "Extracting txz archive: ${txzFile.name} (${txzFileSize / 1024 / 1024}MB)")
            progressCallback?.invoke(0.0f, "Extracting ${txzFile.name}...")

            BufferedInputStream(FileInputStream(txzFile)).use { bufferedInput ->
                XZCompressorInputStream(bufferedInput).use { xzInput ->
                    TarArchiveInputStream(xzInput).use { tarInput ->
                        var entry: TarArchiveEntry? = tarInput.nextEntry as TarArchiveEntry?

                        while (entry != null) {
                            val outputFile = File(targetDir, entry.name)

                            // Security: Prevent path traversal attacks
                            if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                                Log.w(TAG, "Skipping suspicious entry: ${entry.name}")
                                entry = tarInput.nextEntry as TarArchiveEntry?
                                continue
                            }

                            if (entry.isDirectory) {
                                // Create directory
                                outputFile.mkdirs()
                                Log.d(TAG, "Created directory: ${entry.name}")
                            } else if (entry.isSymbolicLink) {
                                // Skip symlinks on Windows (not supported)
                                Log.d(TAG, "Skipping symlink: ${entry.name} -> ${entry.linkName}")
                            } else {
                                // Extract file
                                outputFile.parentFile?.mkdirs()

                                FileOutputStream(outputFile).use { output ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var len: Int
                                    while (tarInput.read(buffer).also { len = it } > 0) {
                                        output.write(buffer, 0, len)
                                        bytesProcessed += len
                                    }
                                }

                                // Set executable permissions if present
                                val mode = entry.mode
                                val isExecutable = (mode and 0x49) != 0 // Check execute bits

                                if (isExecutable) {
                                    outputFile.setExecutable(true, false)
                                    Log.d(TAG, "Set executable: ${entry.name} (mode: ${mode.toString(8)})")
                                }

                                Log.d(TAG, "Extracted: ${entry.name} (${entry.size} bytes)")
                            }

                            // Report progress
                            val progress = (bytesProcessed.toFloat() / (txzFileSize * 5)).coerceIn(0f, 1f)
                            progressCallback?.invoke(progress, "Extracting...")

                            entry = tarInput.nextEntry as TarArchiveEntry?
                        }
                    }
                }
            }

            Log.i(TAG, "Txz extraction complete: ${targetDir.absolutePath}")
            progressCallback?.invoke(1.0f, "Extraction complete")
            Result.success(targetDir)
        } catch (e: Exception) {
            Log.e(TAG, "Txz extraction failed", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a file is a valid zstd compressed file.
     *
     * @param file File to check
     * @return true if valid zstd file
     */
    fun isValidZstd(file: File): Boolean {
        Log.w(TAG, "Zstandard validation is temporarily unavailable")
        return false
    }
}
