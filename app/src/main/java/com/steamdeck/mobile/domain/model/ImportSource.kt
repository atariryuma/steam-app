package com.steamdeck.mobile.domain.model

/**
 * Import source type
 */
enum class ImportSource {
 USB_OTG,  // USB OTG device
 SMB_CIFS, // SMB/CIFS network share
 FTP,   // FTP server
 LOCAL  // Local storage (SAF)
}

/**
 * Importable file information
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
  * Formatted file size
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
  * Whether the file is executable
  */
 val isExecutable: Boolean
  get() = name.endsWith(".exe", ignoreCase = true) ||
    name.endsWith(".msi", ignoreCase = true)
}

/**
 * SMB/CIFS connection configuration
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
  * Generate connection URL
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
 * FTP connection configuration
 */
data class FtpConfig(
 val host: String,
 val port: Int = 21,
 val username: String = "anonymous",
 val password: String = "",
 val useTLS: Boolean = false,
 val passiveMode: Boolean = true
)
