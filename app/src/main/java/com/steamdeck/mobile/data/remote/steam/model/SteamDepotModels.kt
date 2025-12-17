package com.steamdeck.mobile.data.remote.steam.model

import com.google.gson.annotations.SerializedName

/**
 * Steam Depot Download Models
 *
 * Based on DepotDownloader implementation:
 * https://github.com/SteamRE/DepotDownloader
 *
 * Reference: SteamKit ContentDownloader
 */

/**
 * CDN認証トークン
 */
data class CDNAuthToken(
    @SerializedName("token")
    val token: String,

    @SerializedName("expires")
    val expires: Long, // Unix timestamp

    @SerializedName("cdn_url")
    val cdnUrl: String
) {
    fun isExpired(): Boolean = System.currentTimeMillis() / 1000 > expires
}

/**
 * Depot Manifest情報
 */
data class DepotManifest(
    @SerializedName("depot_id")
    val depotId: Long,

    @SerializedName("manifest_id")
    val manifestId: Long,

    @SerializedName("creation_time")
    val creationTime: Long,

    @SerializedName("files")
    val files: List<DepotFile>,

    @SerializedName("total_size")
    val totalSize: Long,

    @SerializedName("total_compressed_size")
    val totalCompressedSize: Long
)

/**
 * Depotファイルエントリ
 */
data class DepotFile(
    @SerializedName("filename")
    val filename: String,

    @SerializedName("size")
    val size: Long,

    @SerializedName("chunks")
    val chunks: List<FileChunk>,

    @SerializedName("flags")
    val flags: Int = 0
) {
    fun isExecutable(): Boolean = (flags and 0x01) != 0
    fun isDirectory(): Boolean = (flags and 0x10) != 0
}

/**
 * ファイルチャンク (CDNからダウンロードする単位)
 */
data class FileChunk(
    @SerializedName("sha")
    val sha: String, // SHA-1 hash

    @SerializedName("crc")
    val crc: Int,

    @SerializedName("offset")
    val offset: Long,

    @SerializedName("cb_original")
    val uncompressedSize: Int,

    @SerializedName("cb_compressed")
    val compressedSize: Int
)

/**
 * App情報 (Depot一覧取得用)
 */
data class AppInfo(
    @SerializedName("appid")
    val appId: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("depots")
    val depots: List<DepotInfo>
)

/**
 * Depot情報
 */
data class DepotInfo(
    @SerializedName("depot_id")
    val depotId: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("maxsize")
    val maxSize: Long,

    @SerializedName("manifests")
    val manifests: Map<String, ManifestInfo>
) {
    /**
     * 最新のpublicマニフェストを取得
     */
    fun getPublicManifest(): ManifestInfo? = manifests["public"]
}

/**
 * Manifest情報
 */
data class ManifestInfo(
    @SerializedName("gid")
    val manifestId: Long,

    @SerializedName("size")
    val size: Long,

    @SerializedName("download")
    val downloadSize: Long
)

/**
 * ダウンロード進捗状態
 */
sealed class SteamDownloadProgress {
    /** 準備中 */
    data class Preparing(val message: String) : SteamDownloadProgress()

    /** CDN認証中 */
    object Authenticating : SteamDownloadProgress()

    /** Manifest取得中 */
    data class FetchingManifest(val depotId: Long) : SteamDownloadProgress()

    /** ダウンロード中 */
    data class Downloading(
        val currentFile: String,
        val fileIndex: Int,
        val totalFiles: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : SteamDownloadProgress() {
        val progressPercent: Int
            get() = if (totalBytes > 0) {
                ((bytesDownloaded * 100) / totalBytes).toInt()
            } else 0

        val speedFormatted: String
            get() = when {
                speedBytesPerSec < 1024 -> "$speedBytesPerSec B/s"
                speedBytesPerSec < 1024 * 1024 -> "${speedBytesPerSec / 1024} KB/s"
                else -> String.format("%.2f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
            }
    }

    /** 検証中 */
    data class Verifying(val fileIndex: Int, val totalFiles: Int) : SteamDownloadProgress()

    /** 完了 */
    data class Completed(val installPath: String, val totalSize: Long) : SteamDownloadProgress()

    /** エラー */
    data class Error(val message: String, val cause: Throwable? = null) : SteamDownloadProgress()
}
