package com.steamdeck.mobile.data.local.database

import androidx.room.TypeConverter
import com.steamdeck.mobile.core.winlator.Box64Preset
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import com.steamdeck.mobile.data.local.database.entity.GameSource
import com.steamdeck.mobile.data.local.database.entity.SteamInstallStatus

/**
 * Type conversion class for Room
 * Converts Enum and custom classes to database primitive types
 */
class Converters {
 // GameSource converters
 @TypeConverter
 fun fromGameSource(value: GameSource): String = value.name

 @TypeConverter
 fun toGameSource(value: String): GameSource = GameSource.valueOf(value)

 // Box64Preset converters
 @TypeConverter
 fun fromBox64Preset(value: Box64Preset): String = value.name

 @TypeConverter
 fun toBox64Preset(value: String): Box64Preset = Box64Preset.valueOf(value)

 // DownloadStatus converters
 @TypeConverter
 fun fromDownloadStatus(value: DownloadStatus): String = value.name

 @TypeConverter
 fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

 // SteamInstallStatus converters
 @TypeConverter
 fun fromSteamInstallStatus(value: SteamInstallStatus): String = value.name

 @TypeConverter
 fun toSteamInstallStatus(value: String): SteamInstallStatus = SteamInstallStatus.valueOf(value)
}
