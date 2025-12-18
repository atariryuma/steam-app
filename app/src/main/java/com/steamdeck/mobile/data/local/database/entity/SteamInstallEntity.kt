package com.steamdeck.mobile.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Steam Client インストール情報エンティティ
 *
 * Best Practice: Room entity with @ColumnInfo for explicit column naming
 * Reference: https://developer.android.com/training/data-storage/room/defining-data
 */
@Entity(tableName = "steam_installations")
data class SteamInstallEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Winlator コンテナ ID */
    @ColumnInfo(name = "container_id")
    val containerId: String,

    /** インストールパス (例: C:\Program Files (x86)\Steam) */
    @ColumnInfo(name = "install_path")
    val installPath: String,

    /** インストール状態 */
    @ColumnInfo(name = "status")
    val status: SteamInstallStatus,

    /** Steam バージョン */
    @ColumnInfo(name = "version")
    val version: String? = null,

    /** インストール日時 (Unix timestamp) */
    @ColumnInfo(name = "installed_at")
    val installedAt: Long = System.currentTimeMillis(),

    /** 最終起動日時 (Unix timestamp) */
    @ColumnInfo(name = "last_launched_at")
    val lastLaunchedAt: Long? = null
)
