package com.steamdeck.mobile.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Steam Client installationinformationエンティティ
 *
 * Best Practice: Room entity with @ColumnInfo for explicit column naming
 * Reference: https://developer.android.com/training/data-storage/room/defining-data
 */
@Entity(
 tableName = "steam_installations",
 indices = [
  Index(value = ["container_id"]),
  Index(value = ["status"])
 ]
)
data class SteamInstallEntity(
 @PrimaryKey(autoGenerate = true)
 val id: Long = 0,

 /** Winlator container ID */
 @ColumnInfo(name = "container_id")
 val containerId: String,

 /** installationpath (Example: C:\Program Files (x86)\Steam) */
 @ColumnInfo(name = "install_path")
 val installPath: String,

 /** installationstate */
 @ColumnInfo(name = "status")
 val status: SteamInstallStatus,

 /** Steam バージョン */
 @ColumnInfo(name = "version")
 val version: String? = null,

 /** installationdate and time (Unix timestamp) */
 @ColumnInfo(name = "installed_at")
 val installedAt: Long = System.currentTimeMillis(),

 /** 最終launchdate and time (Unix timestamp) */
 @ColumnInfo(name = "last_launched_at")
 val lastLaunchedAt: Long? = null
)
