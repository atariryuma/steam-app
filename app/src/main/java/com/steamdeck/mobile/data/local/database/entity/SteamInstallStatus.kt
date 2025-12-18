package com.steamdeck.mobile.data.local.database.entity

/**
 * Steam Client インストール状態
 */
enum class SteamInstallStatus {
    /** インストール中 */
    INSTALLING,

    /** インストール完了 */
    INSTALLED,

    /** インストール失敗 */
    FAILED,

    /** アンインストール済み */
    UNINSTALLED
}
