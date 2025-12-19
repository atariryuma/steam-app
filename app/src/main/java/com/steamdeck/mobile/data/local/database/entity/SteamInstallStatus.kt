package com.steamdeck.mobile.data.local.database.entity

/**
 * Steam Client installationstate
 */
enum class SteamInstallStatus {
 /** installationin */
 INSTALLING,

 /** installationcompleted */
 INSTALLED,

 /** installationfailure */
 FAILED,

 /** アンinstallation済み */
 UNINSTALLED
}
