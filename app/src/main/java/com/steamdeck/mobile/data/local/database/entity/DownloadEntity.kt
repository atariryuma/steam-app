package com.steamdeck.mobile.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ダウンロード履歴・状態を格納するエンティティ
 */
@Entity(tableName = "downloads")
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

    /** 進捗率（0-100） */
    val progress: Int = 0,

    /** ダウンロード済みバイト数 */
    val downloadedBytes: Long = 0,

    /** 総ファイルサイズ（バイト） */
    val totalBytes: Long = 0,

    /** 保存先パス */
    val destinationPath: String,

    /** ダウンロード開始日時（Unix timestamp） */
    val startedTimestamp: Long = System.currentTimeMillis(),

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
    ERROR,

    /** キャンセル */
    CANCELLED
}
