package com.steamdeck.mobile.data.local.database.entity

/**
 * Steam Client installation state
 */
enum class SteamInstallStatus {
 /** Installing */
 INSTALLING,

 /** Installation completed */
 INSTALLED,

 /** Installation failed */
 FAILED,

 /** Uninstalled */
 UNINSTALLED
}
