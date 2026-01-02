package com.steamdeck.mobile.core.steam

import com.steamdeck.mobile.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NSIS installer extractor using 7-Zip-JBinding-4Android
 *
 * Extracts all files from SteamSetup.exe (NSIS installer) to a target directory.
 * This bypasses Wine WoW64 limitations by directly extracting installer contents.
 *
 * Technical details:
 * - Uses 7-Zip native library with ARM64 support
 * - Supports all NSIS compression formats (LZMA, BZIP2, ZLIB/Deflate)
 * - Extracts ~132 files including Steam.exe, steamclient.dll, libcef.dll, etc.
 * - Total extracted size: ~180MB
 */
@Singleton
class NsisExtractor @Inject constructor() {
 companion object {
  private const val TAG = "NsisExtractor"
 }

 /**
  * Extract all files from NSIS installer to target directory
  *
  * @param nsisFile SteamSetup.exe file (NSIS installer)
  * @param targetDir Target directory for extraction (e.g., C:\Program Files (x86)\Steam)
  * @param progressCallback Optional progress callback (filesExtracted, totalFiles)
  * @return Result indicating success or failure with extracted file count
  */
 suspend fun extractAll(
  nsisFile: File,
  targetDir: File,
  progressCallback: ((filesExtracted: Int, totalFiles: Int) -> Unit)? = null
 ): Result<Int> = withContext(Dispatchers.IO) {
  try {
   AppLogger.i(TAG, "Starting NSIS extraction from: ${nsisFile.absolutePath}")
   AppLogger.i(TAG, "Target directory: ${targetDir.absolutePath}")

   if (!nsisFile.exists()) {
    return@withContext Result.failure(
     Exception("NSIS file not found: ${nsisFile.absolutePath}")
    )
   }

   // Ensure target directory exists
   if (!targetDir.exists()) {
    targetDir.mkdirs()
   }

   var archive: IInArchive? = null
   var randomAccessFile: RandomAccessFile? = null
   var filesExtracted = 0

   try {
    // Open NSIS archive using 7-Zip
    randomAccessFile = RandomAccessFile(nsisFile, "r")
    val inStream = RandomAccessFileInStream(randomAccessFile)

    // Auto-detect archive format (NSIS)
    archive = SevenZip.openInArchive(null, inStream)

    val totalFiles = archive.numberOfItems
    AppLogger.i(TAG, "Found $totalFiles items in NSIS archive")

    // Extract all items with frequent progress updates
    // Update progress every 5 files (instead of 10) for smoother UI feedback
    for (item in archive.simpleInterface.archiveItems) {
     if (!item.isFolder) {
      extractItem(item, targetDir)
      filesExtracted++

      // Report progress every 5 files OR on first/last file
      if (filesExtracted == 1 || filesExtracted % 5 == 0 || filesExtracted == totalFiles) {
       progressCallback?.invoke(filesExtracted, totalFiles)
       AppLogger.d(TAG, "Extracted $filesExtracted/$totalFiles files (${filesExtracted * 100 / totalFiles}%)")
      }
     }
    }

    // Final progress update
    progressCallback?.invoke(filesExtracted, totalFiles)
    AppLogger.i(TAG, "NSIS extraction completed: $filesExtracted files extracted")
    Result.success(filesExtracted)

   } finally {
    archive?.close()
    randomAccessFile?.close()
   }

  } catch (e: Exception) {
   AppLogger.e(TAG, "NSIS extraction failed", e)
   Result.failure(e)
  }
 }

 /**
  * Extract a single item from archive
  * Uses streaming extraction to avoid OutOfMemoryError on large files (e.g., 281MB bins_cef_win32.zip)
  */
 private fun extractItem(item: ISimpleInArchiveItem, targetDir: File) {
  val itemPath = item.path ?: return

  // Skip certain directories/files we don't need
  if (shouldSkip(itemPath)) {
   return
  }

  val targetFile = File(targetDir, itemPath)

  // Create parent directories
  targetFile.parentFile?.mkdirs()

  // Extract file using streaming ISequentialOutStream (memory-efficient, 64KB chunks)
  val outputStream = FileOutputStream(targetFile)
  try {
   val sequentialOutStream = object : ISequentialOutStream {
    override fun write(data: ByteArray): Int {
     outputStream.write(data)
     return data.size
    }
   }

   val result = item.extractSlow(sequentialOutStream)
   if (result != ExtractOperationResult.OK) {
    throw Exception("Extraction failed with result: $result")
   }

   AppLogger.d(TAG, "Extracted: $itemPath (${targetFile.length()} bytes)")
  } finally {
   outputStream.close()
  }
 }

 /**
  * Check if item should be skipped during extraction
  */
 private fun shouldSkip(path: String): Boolean {
  // Skip installer-specific files (we only want Steam runtime files)
  return path.startsWith("\$PLUGINSDIR/") ||
         path == "uninstall.exe" ||
         path.endsWith(".nsi") ||
         path.endsWith(".nsh")
 }
}
