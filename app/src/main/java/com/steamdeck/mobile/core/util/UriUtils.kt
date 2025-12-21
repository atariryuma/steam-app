package com.steamdeck.mobile.core.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.steamdeck.mobile.core.logging.AppLogger
import java.io.File
import java.io.FileOutputStream

/**
 * URI Utilities for converting Content URIs to file paths
 *
 * Handles Storage Access Framework (SAF) content:// URIs and converts them to
 * usable file paths. For Android 10+ (Scoped Storage), creates copies in app-private
 * storage when necessary.
 *
 * Best Practices (2025):
 * - Use Storage Access Framework URIs (content://) on Android 10+
 * - Copy external files to app-private storage for reliable access
 * - Handle DocumentsContract.Document URIs
 * - Fallback to legacy path extraction for older Android versions
 *
 * References:
 * - https://developer.android.com/training/data-storage/shared/documents-files
 * - https://developer.android.com/reference/android/provider/DocumentsContract
 */
object UriUtils {
    private const val TAG = "UriUtils"

    /**
     * Convert Content URI to usable file path
     *
     * Strategy:
     * 1. If already a file path (starts with /), return as-is
     * 2. If content:// URI, try to extract real path
     * 3. Fallback: Copy to app-private storage and return copy path
     *
     * @param context Android context
     * @param uriString URI string (content:// or file path)
     * @return Absolute file path, or null if conversion fails
     */
    fun getFilePathFromUri(context: Context, uriString: String): String? {
        // Already a file path
        if (uriString.startsWith("/")) {
            AppLogger.d(TAG, "URI is already file path: $uriString")
            return uriString
        }

        // Not a content URI
        if (!uriString.startsWith("content://")) {
            AppLogger.w(TAG, "Invalid URI scheme: $uriString")
            return null
        }

        val uri = Uri.parse(uriString)
        AppLogger.d(TAG, "Converting content URI to file path: $uri")

        // Try different strategies based on Android version and URI type
        return when {
            // Strategy 1: DocumentsContract (Android 4.4+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri) -> {
                getPathFromDocumentUri(context, uri)
            }
            // Strategy 2: MediaStore content provider
            uri.authority == "media" -> {
                getPathFromMediaUri(context, uri)
            }
            // Strategy 3: External storage documents
            uri.authority == "com.android.externalstorage.documents" -> {
                getPathFromExternalStorageUri(context, uri)
            }
            // Strategy 4: Downloads provider
            uri.authority == "com.android.providers.downloads.documents" -> {
                getPathFromDownloadsUri(context, uri)
            }
            // Fallback: Copy to app-private storage
            else -> {
                AppLogger.w(TAG, "Unknown URI authority: ${uri.authority}, copying to app storage")
                copyUriToAppStorage(context, uri)
            }
        }
    }

    /**
     * Get file path from DocumentsContract URI
     */
    private fun getPathFromDocumentUri(context: Context, uri: Uri): String? {
        // Handle tree URIs (directory selection)
        if (uri.toString().contains("/tree/")) {
            AppLogger.d(TAG, "Tree URI detected, extracting directory path")
            return getPathFromTreeUri(context, uri)
        }

        val docId = DocumentsContract.getDocumentId(uri)
        AppLogger.d(TAG, "Document ID: $docId")

        return when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                getPathFromExternalStorageUri(context, uri)
            }
            "com.android.providers.downloads.documents" -> {
                getPathFromDownloadsUri(context, uri)
            }
            "com.android.providers.media.documents" -> {
                getPathFromMediaDocumentUri(context, docId)
            }
            else -> {
                // Unknown provider, copy to app storage
                AppLogger.w(TAG, "Unknown document provider: ${uri.authority}")
                copyUriToAppStorage(context, uri)
            }
        }
    }

    /**
     * Get file path from tree URI (directory selection)
     * Format: content://com.android.externalstorage.documents/tree/primary:Documents/Winlator
     */
    private fun getPathFromTreeUri(context: Context, uri: Uri): String? {
        return try {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
            AppLogger.d(TAG, "Tree Document ID: $treeDocumentId")

            val split = treeDocumentId.split(":")
            val type = split[0]

            if ("primary".equals(type, ignoreCase = true)) {
                val relativePath = if (split.size > 1) split[1] else ""

                // On Android 10+, direct file access is restricted
                // Return a virtual path that represents the directory
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // For directories, we can't copy them directly
                    // Return the tree URI as-is (will be used for SAF operations)
                    AppLogger.d(TAG, "Android 10+: Using SAF tree URI for directory access")
                    return uri.toString()
                } else {
                    // Legacy: Direct file path access
                    val externalStoragePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                    return "$externalStoragePath/$relativePath"
                }
            }

            null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get path from tree URI: $uri", e)
            // Return the tree URI as-is for SAF operations
            uri.toString()
        }
    }

    /**
     * Get file path from external storage URI
     * Format: content://com.android.externalstorage.documents/document/primary:Download/file.exe
     */
    private fun getPathFromExternalStorageUri(context: Context, uri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(":")
        val type = split[0]

        if ("primary".equals(type, ignoreCase = true)) {
            val relativePath = if (split.size > 1) split[1] else ""

            // Try direct /sdcard path access (works on Android 10+ for readable files)
            val directPath = "/sdcard/$relativePath"
            val directFile = File(directPath)

            AppLogger.d(TAG, "Trying direct path: $directPath, exists=${directFile.exists()}, canRead=${directFile.canRead()}")

            if (directFile.exists() && directFile.canRead()) {
                AppLogger.i(TAG, "Using direct /sdcard path: $directPath")
                return directPath
            }

            // Fallback: Try /storage/emulated/0 path
            val storagePath = "/storage/emulated/0/$relativePath"
            val storageFile = File(storagePath)

            AppLogger.d(TAG, "Trying storage path: $storagePath, exists=${storageFile.exists()}, canRead=${storageFile.canRead()}")

            if (storageFile.exists() && storageFile.canRead()) {
                AppLogger.i(TAG, "Using storage emulated path: $storagePath")
                return storagePath
            }

            // Last resort: Copy to app-private storage
            AppLogger.w(TAG, "Direct path access failed for both /sdcard and /storage/emulated/0, copying to app storage")
            return copyUriToAppStorage(context, uri)
        }

        return null
    }

    /**
     * Get file path from downloads URI
     */
    private fun getPathFromDownloadsUri(context: Context, uri: Uri): String? {
        // Copy to app storage (most reliable method for Android 10+)
        return copyUriToAppStorage(context, uri)
    }

    /**
     * Get file path from media document URI
     */
    private fun getPathFromMediaDocumentUri(context: Context, docId: String): String? {
        val split = docId.split(":")
        val type = split[0]
        val id = split[1]

        val contentUri = when (type) {
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> return null
        }

        val selection = "_id=?"
        val selectionArgs = arrayOf(id)

        return getDataColumn(context, contentUri, selection, selectionArgs)
    }

    /**
     * Get file path from MediaStore URI
     */
    private fun getPathFromMediaUri(context: Context, uri: Uri): String? {
        return getDataColumn(context, uri, null, null)
    }

    /**
     * Query ContentResolver for file path
     */
    private fun getDataColumn(
        context: Context,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get data column from URI: $uri", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * Copy content URI to app-private storage
     *
     * This is the most reliable method for Android 10+ (Scoped Storage).
     * Files are copied to app's internal storage (files/games/) for persistent access.
     *
     * @param context Android context
     * @param uri Content URI
     * @return Absolute file path to copied file
     */
    fun copyUriToAppStorage(context: Context, uri: Uri): String? {
        try {
            // Get original filename
            val fileName = getFileNameFromUri(context.contentResolver, uri) ?: run {
                AppLogger.e(TAG, "Failed to get filename from URI: $uri")
                return null
            }

            // Create games directory in app-private storage
            val gamesDir = File(context.filesDir, "games")
            if (!gamesDir.exists()) {
                gamesDir.mkdirs()
                AppLogger.d(TAG, "Created games directory: ${gamesDir.absolutePath}")
            }

            // Create destination file
            val destFile = File(gamesDir, fileName)

            // Copy file content
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            AppLogger.i(TAG, "Copied file to app storage: ${destFile.absolutePath}")
            return destFile.absolutePath

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to copy URI to app storage: $uri", e)
            return null
        }
    }

    /**
     * Get filename from Content URI using OpenableColumns
     */
    private fun getFileNameFromUri(contentResolver: ContentResolver, uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return cursor.getString(displayNameIndex)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get filename from URI: $uri", e)
        } finally {
            cursor?.close()
        }

        // Fallback: Use last path segment
        return uri.lastPathSegment
    }

    /**
     * Get filename from URI or file path
     */
    fun getFileName(uriOrPath: String): String {
        return if (uriOrPath.startsWith("content://")) {
            Uri.parse(uriOrPath).lastPathSegment ?: "unknown"
        } else {
            File(uriOrPath).name
        }
    }
}
