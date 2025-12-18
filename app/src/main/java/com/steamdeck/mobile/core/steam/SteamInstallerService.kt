package com.steamdeck.mobile.core.steam

import android.content.Context
import android.util.Log
import com.steamdeck.mobile.core.download.DownloadManager
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.entity.SteamInstallEntity
import com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steam Client インストーラーサービス
 *
 * Steamインストーラーのダウンロードと検証を管理します
 */
@Singleton
class SteamInstallerService @Inject constructor(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val database: SteamDeckDatabase,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "SteamInstallerService"
        private const val STEAM_INSTALLER_URL = "https://steamcdn-a.akamaihd.net/client/installer/SteamSetup.exe"
        private const val INSTALLER_FILENAME = "SteamSetup.exe"
    }

    /**
     * Steam インストーラーをダウンロード
     */
    suspend fun downloadInstaller(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(context.cacheDir, "steam")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val installerFile = File(downloadDir, INSTALLER_FILENAME)

            // 既に存在する場合は削除
            if (installerFile.exists()) {
                installerFile.delete()
            }

            Log.i(TAG, "Downloading Steam installer from $STEAM_INSTALLER_URL")

            // OkHttp で直接ダウンロード (シンプル版)
            val request = okhttp3.Request.Builder()
                .url(STEAM_INSTALLER_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to download: HTTP ${response.code}")
                )
            }

            response.body?.byteStream()?.use { input ->
                installerFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Steam installer downloaded: ${installerFile.absolutePath}")
            Result.success(installerFile)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download Steam installer", e)
            Result.failure(e)
        }
    }

    /**
     * Steam インストール情報を保存
     *
     * 既存のインストール情報がある場合は更新、なければ新規作成 (UPSERT)
     */
    suspend fun saveInstallation(
        containerId: String,
        installPath: String,
        status: SteamInstallStatus
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 既存のインストール情報を確認
            val existing = database.steamInstallDao().getInstallationByContainerId(containerId)

            if (existing != null) {
                // 既存レコードを更新
                val updatedEntity = existing.copy(
                    installPath = installPath,
                    status = status,
                    installedAt = System.currentTimeMillis()
                )
                database.steamInstallDao().update(updatedEntity)
                Log.i(TAG, "Updated existing installation for container: $containerId")
                Result.success(existing.id)
            } else {
                // 新規レコードを作成
                val entity = SteamInstallEntity(
                    containerId = containerId,
                    installPath = installPath,
                    status = status
                )
                val id = database.steamInstallDao().insert(entity)
                Log.i(TAG, "Created new installation record for container: $containerId")
                Result.success(id)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save installation", e)
            Result.failure(e)
        }
    }

    /**
     * Steam インストール情報を更新
     */
    suspend fun updateInstallation(entity: SteamInstallEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                database.steamInstallDao().update(entity)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update installation", e)
                Result.failure(e)
            }
        }

    /**
     * Steam インストール情報を取得
     */
    suspend fun getInstallation(): SteamInstallEntity? = withContext(Dispatchers.IO) {
        database.steamInstallDao().getInstallation()
    }
}
