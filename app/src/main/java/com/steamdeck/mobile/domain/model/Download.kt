package com.steamdeck.mobile.domain.model

/**
 * ダウンロード情報のドメインモデル
 */
data class Download(
    val id: Long = 0,
    val gameId: Long? = null,
    val fileName: String,
    val url: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val destinationPath: String,
    val startedTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null,
    val errorMessage: String? = null
) {
    /**
     * ダウンロード速度を計算（bytes/sec）
     */
    fun calculateSpeed(elapsedMillis: Long): Long {
        if (elapsedMillis == 0L) return 0L
        return (downloadedBytes * 1000) / elapsedMillis
    }

    /**
     * 残り時間を計算（秒）
     */
    fun calculateRemainingTime(speedBytesPerSec: Long): Long {
        if (speedBytesPerSec == 0L) return Long.MAX_VALUE
        val remainingBytes = totalBytes - downloadedBytes
        return remainingBytes / speedBytesPerSec
    }

    /**
     * ダウンロード済みサイズを人間が読みやすい形式で取得
     */
    val downloadedSizeFormatted: String
        get() = formatBytes(downloadedBytes)

    /**
     * 総サイズを人間が読みやすい形式で取得
     */
    val totalSizeFormatted: String
        get() = formatBytes(totalBytes)

    /**
     * 進捗率を文字列で取得（例: "45%"）
     */
    val progressFormatted: String
        get() = "$progress%"

    companion object {
        /**
         * バイトサイズを人間が読みやすい形式に変換
         */
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.2f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }

        /**
         * 速度を人間が読みやすい形式に変換
         */
        fun formatSpeed(bytesPerSec: Long): String {
            return "${formatBytes(bytesPerSec)}/s"
        }

        /**
         * 残り時間を人間が読みやすい形式に変換
         */
        fun formatRemainingTime(seconds: Long): String {
            if (seconds == Long.MAX_VALUE) return "不明"
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60

            return when {
                hours > 0 -> "${hours}時間${minutes}分"
                minutes > 0 -> "${minutes}分${secs}秒"
                else -> "${secs}秒"
            }
        }
    }
}

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
    CANCELLED;

    /**
     * 状態の日本語表示
     */
    val displayName: String
        get() = when (this) {
            PENDING -> "待機中"
            DOWNLOADING -> "ダウンロード中"
            PAUSED -> "一時停止"
            COMPLETED -> "完了"
            ERROR -> "エラー"
            CANCELLED -> "キャンセル"
        }

    /**
     * アクティブな状態かどうか
     */
    val isActive: Boolean
        get() = this == DOWNLOADING || this == PENDING
}
