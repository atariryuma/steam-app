package com.steamdeck.mobile.core.ssl

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * SSL Certificate Installer for Wine/GnuTLS HTTPS Support
 *
 * Downloads and installs Mozilla CA certificate bundle to enable HTTPS connections
 * in Wine applications (Steam, web browsers, etc.)
 *
 * Wine 9.0+ uses GnuTLS for SSL/TLS, which requires certificates at:
 * /etc/ssl/certs/ca-certificates.crt (hard-coded path, env vars have no effect)
 *
 * References:
 * - Wine GnuTLS: https://github.com/wine-mirror/wine/blob/master/dlls/secur32/schannel_gnutls.c
 * - Mozilla CA Bundle: https://curl.se/docs/caextract.html
 */
object SslCertificateInstaller {
    private const val TAG = "SslCertificateInstaller"

    // Mozilla CA Bundle (CCADB - Common CA Database from Mozilla)
    // Using direct HTTP URL from Salesforce CDN (no redirect, pure HTTP)
    private const val CA_BUNDLE_URL = "http://ccadb.my.salesforce-sites.com/mozilla/IncludedRootsPEMTxt?TrustBitsInclude=Websites"

    // Certificate file names
    private const val CA_BUNDLE_FILENAME = "ca-certificates.crt"
    private const val CA_BUNDLE_SYMLINK = "ca-bundle.crt"

    // Validation constraints
    private const val MIN_BUNDLE_SIZE = 200_000L  // 200KB
    private const val MAX_BUNDLE_SIZE = 300_000L  // 300KB
    private const val PEM_HEADER = "-----BEGIN CERTIFICATE-----"

    // Download timeout
    private const val DOWNLOAD_TIMEOUT_SECONDS = 60L

    /**
     * Install SSL/TLS certificates to rootfs
     *
     * This method:
     * 1. Creates /etc/ssl/certs directory if needed
     * 2. Downloads Mozilla CA bundle from curl.se (HTTP)
     * 3. Validates bundle (size + PEM header check)
     * 4. Installs to /etc/ssl/certs/ca-certificates.crt
     * 5. Creates compatibility symlink: ca-bundle.crt
     *
     * @param context Android application context (for cache directory)
     * @param rootfsDir Rootfs directory (e.g., /data/data/com.steamdeck.mobile/files/rootfs)
     * @return true if installation succeeded, false otherwise (non-fatal)
     */
    suspend fun install(context: Context, rootfsDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting SSL certificate installation for Wine/GnuTLS")

            // Create /etc/ssl/certs directory
            val certsDir = File(rootfsDir, "etc/ssl/certs")
            if (!certsDir.exists()) {
                val created = certsDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create directory: ${certsDir.absolutePath}")
                    return@withContext false
                }
                Log.i(TAG, "Created directory: ${certsDir.absolutePath}")
            }

            val caBundleFile = File(certsDir, CA_BUNDLE_FILENAME)

            // Idempotent: Skip if already exists and valid
            if (caBundleFile.exists() && validateCaBundle(caBundleFile)) {
                Log.i(TAG, "SSL certificates already installed and valid")
                return@withContext true
            }

            // Download Mozilla CA bundle
            Log.i(TAG, "Downloading Mozilla CA bundle from $CA_BUNDLE_URL")
            val tempCertFile = downloadCaBundle(context)
            if (tempCertFile == null) {
                Log.e(TAG, "CA bundle download failed")
                return@withContext false
            }

            // Validate downloaded bundle
            if (!validateCaBundle(tempCertFile)) {
                Log.e(TAG, "CA bundle validation failed")
                tempCertFile.delete()
                return@withContext false
            }

            Log.i(TAG, "Downloaded CA bundle: ${tempCertFile.length()} bytes")

            // Install to rootfs
            val installSuccess = installCaBundle(tempCertFile, certsDir)
            tempCertFile.delete()

            if (installSuccess) {
                Log.i(TAG, "SSL certificate installation completed successfully")
            }

            installSuccess

        } catch (e: Exception) {
            Log.e(TAG, "SSL certificate installation failed", e)
            false
        }
    }

    /**
     * Download Mozilla CA certificate bundle
     *
     * Downloads from curl.se over HTTP (no bootstrap problem - no HTTPS needed)
     * Uses OkHttp with 60-second timeout
     *
     * @param context Android application context
     * @return Downloaded file in cache directory, or null on failure
     */
    private suspend fun downloadCaBundle(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            // Create OkHttp client with timeout
            val client = OkHttpClient.Builder()
                .connectTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

            // Build request
            val request = Request.Builder()
                .url(CA_BUNDLE_URL)
                .build()

            // Execute request
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code} ${response.message}")
                    return@withContext null
                }

                // Save to temp file
                val tempFile = File(context.cacheDir, "cacert-download.pem")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                tempFile
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            null
        }
    }

    /**
     * Validate CA certificate bundle
     *
     * Checks:
     * 1. File exists
     * 2. Size is within expected range (200-300KB)
     * 3. First line is PEM header
     *
     * @param file Certificate bundle file
     * @return true if valid, false otherwise
     */
    private fun validateCaBundle(file: File): Boolean {
        if (!file.exists()) {
            return false
        }

        // Check file size
        val fileSize = file.length()
        if (fileSize < MIN_BUNDLE_SIZE || fileSize > MAX_BUNDLE_SIZE) {
            Log.w(TAG, "Bundle size out of range: $fileSize bytes (expected 200-300KB)")
            return false
        }

        // Verify PEM header
        try {
            BufferedReader(InputStreamReader(file.inputStream())).use { reader ->
                val firstLine = reader.readLine()
                if (firstLine == null || firstLine.trim() != PEM_HEADER) {
                    Log.w(TAG, "Missing or invalid PEM header")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read file for validation", e)
            return false
        }

        Log.d(TAG, "Bundle validated: $fileSize bytes")
        return true
    }

    /**
     * Install CA bundle to rootfs /etc/ssl/certs directory
     *
     * Installs:
     * 1. ca-certificates.crt (primary bundle)
     * 2. ca-bundle.crt (symlink for compatibility)
     *
     * @param certFile Downloaded certificate file
     * @param certsDir Target /etc/ssl/certs directory
     * @return true if installation succeeded
     */
    private fun installCaBundle(certFile: File, certsDir: File): Boolean {
        try {
            // Install primary bundle
            val primaryBundle = File(certsDir, CA_BUNDLE_FILENAME)
            certFile.copyTo(primaryBundle, overwrite = true)

            // Set readable permissions
            primaryBundle.setReadable(true, false)

            Log.i(TAG, "Installed CA bundle: ${primaryBundle.absolutePath}")

            // Create compatibility symlink (ca-bundle.crt -> ca-certificates.crt)
            // Some applications look for ca-bundle.crt instead
            val symlinkBundle = File(certsDir, CA_BUNDLE_SYMLINK)
            if (symlinkBundle.exists()) {
                symlinkBundle.delete()
            }

            try {
                // Try to create symbolic link (requires API 21+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    android.system.Os.symlink(
                        primaryBundle.absolutePath,
                        symlinkBundle.absolutePath
                    )
                    Log.i(TAG, "Created symlink: $CA_BUNDLE_SYMLINK -> $CA_BUNDLE_FILENAME")
                } else {
                    // Fallback: Copy file instead of symlink on older Android
                    primaryBundle.copyTo(symlinkBundle, overwrite = true)
                    Log.i(TAG, "Created copy (symlink not supported): $CA_BUNDLE_SYMLINK")
                }
            } catch (e: Exception) {
                // Symlink creation is non-critical
                Log.w(TAG, "Symlink creation failed (non-fatal): ${e.message}")
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to install CA bundle", e)
            return false
        }
    }

    /**
     * Check if SSL certificates are installed
     *
     * @param rootfsDir Rootfs directory
     * @return true if ca-certificates.crt exists and is valid
     */
    fun isInstalled(rootfsDir: File): Boolean {
        val caCert = File(rootfsDir, "etc/ssl/certs/$CA_BUNDLE_FILENAME")
        return validateCaBundle(caCert)
    }

    /**
     * Blocking wrapper for install() - for Java interop
     *
     * @param context Android application context
     * @param rootfsDir Rootfs directory
     * @return true if installation succeeded
     */
    @JvmStatic
    fun installBlocking(context: Context, rootfsDir: File): Boolean {
        return kotlinx.coroutines.runBlocking {
            install(context, rootfsDir)
        }
    }
}
