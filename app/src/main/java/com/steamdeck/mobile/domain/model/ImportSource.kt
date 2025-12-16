package com.steamdeck.mobile.domain.model

/**
 * インポート元の種類
 */
enum class ImportSource {
    USB_OTG,     // USB OTGデバイス
    SMB_CIFS,    // SMB/CIFSネットワーク共有
    FTP,         // FTPサーバー
    LOCAL        // ローカルストレージ (SAF)
}

/**
 * インポート可能なファイル情報
 */
data class ImportableFile(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val source: ImportSource,
    val lastModified: Long = 0L
) {
    /**
     * ファイルサイズをフォーマット
     */
    val sizeFormatted: String
        get() {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
                else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
            }
        }

    /**
     * 実行可能ファイルかどうか
     */
    val isExecutable: Boolean
        get() = name.endsWith(".exe", ignoreCase = true) ||
                name.endsWith(".msi", ignoreCase = true)
}

/**
 * SMB/CIFS接続設定
 */
data class SmbConfig(
    val host: String,
    val shareName: String,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val port: Int = 445
) {
    /**
     * 接続URLを生成
     */
    fun toUrl(): String {
        return buildString {
            append("smb://")
            if (username.isNotEmpty()) {
                if (domain.isNotEmpty()) {
                    append("$domain;$username")
                } else {
                    append(username)
                }
                if (password.isNotEmpty()) {
                    append(":$password")
                }
                append("@")
            }
            append(host)
            if (port != 445) {
                append(":$port")
            }
            append("/$shareName/")
        }
    }
}

/**
 * FTP接続設定
 */
data class FtpConfig(
    val host: String,
    val port: Int = 21,
    val username: String = "anonymous",
    val password: String = "",
    val useTLS: Boolean = false,
    val passiveMode: Boolean = true
)
