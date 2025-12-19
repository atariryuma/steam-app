package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ダウンロード履歴・状態を格納するエンティティ
 *
 * Performance optimization (2025 best practice):
 * - Added installationStatus for automatic game installation
 * - Enables seamless download-to-play workflow
 * - Indexes on frequently queried columns for faster lookups
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["gameId"]),
        Index(value = ["status"]),
        Index(value = ["installationStatus"])
    ]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 関連するゲームID */
    val gameId: Long? = null,

    /** ファイル名 */
    val fileName: String,

    /** ダウンロードURL */
    val url: String,

    /** ダウンロード状態 */
    val status: DownloadStatus,

    /** インストール状態 (ダウンロード完了後の処理) */
    val installationStatus: InstallationStatus = InstallationStatus.NOT_INSTALLED,

    /** 進捗率（0-100） */
    val progress: Int = 0,

    /** ダウンロード済みバイト数 */
    val downloadedBytes: Long = 0,

    /** 総ファイルサイズ（バイト） */
    val totalBytes: Long = 0,

    /** ダウンロード速度（バイト/秒） */
    val speedBytesPerSecond: Long = 0,

    /** 保存先パス */
    val destinationPath: String = "",

    /**
     * ダウンロード開始日時（Unix timestamp）
     *
     * 注意: デフォルト値として System.currentTimeMillis() を使用していますが、
     * 実際の利用時には明示的に値を渡すことを推奨します。
     */
    val startedTimestamp: Long = System.currentTimeMillis(),

    /**
     * 作成日時（Unix timestamp）
     *
     * 注意: デフォルト値として System.currentTimeMillis() を使用していますが、
     * 実際の利用時には明示的に値を渡すことを推奨します。
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * 更新日時（Unix timestamp）
     *
     * 注意: デフォルト値として System.currentTimeMillis() を使用していますが、
     * 実際の利用時には明示的に値を渡すことを推奨します。
     */
    val updatedAt: Long = System.currentTimeMillis(),

    /** ダウンロード完了日時（Unix timestamp） */
    val completedTimestamp: Long? = null,

    /** エラーメッセージ */
    val errorMessage: String? = null
)

/**
 * ダウンロード状態
 */
enum class DownloadStatus {
    /** ダウンロード待機中 */
    PENDING,

    /** ダウンロード中 */
    DOWNLOADING,

    /** 一時停止中 */
    PAUSED,

    /** 完了 */
    COMPLETED,

    /** エラー */
    FAILED,

    /** キャンセル */
    CANCELLED
}

/**
 * インストール状態 (ダウンロード完了後)
 */
enum class InstallationStatus {
    /** 未インストール (ダウンロードのみ完了) */
    NOT_INSTALLED,

    /** インストール待機中 */
    PENDING,

    /** インストール中 */
    INSTALLING,

    /** インストール完了 */
    INSTALLED,

    /** インストール失敗 */
    FAILED
}
