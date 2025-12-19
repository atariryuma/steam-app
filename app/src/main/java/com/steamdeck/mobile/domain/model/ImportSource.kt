package com.steamdeck.mobile.domain.model

/**
 * インポート元 種類
 */
enum class ImportSource {
 USB_OTG,  // USB OTGデバイス
 SMB_CIFS, // SMB/CIFSnetwork共有
 FTP,   // FTPサーバー
 LOCAL  // ローカルストレージ (SAF)
}

/**
 * インポート可能なfileinformation
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
  * fileサイズフォーマット
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
  * execution可能fileかどうか
  */
 val isExecutable: Boolean
  get() = name.endsWith(".exe", ignoreCase = true) ||
    name.endsWith(".msi", ignoreCase = true)
}

/**
 * SMB/CIFSconnectionconfiguration
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
  * connectionURLgenerate
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
 * FTPconnectionconfiguration
 */
data class FtpConfig(
 val host: String,
 val port: Int = 21,
 val username: String = "anonymous",
 val password: String = "",
 val useTLS: Boolean = false,
 val passiveMode: Boolean = true
)
