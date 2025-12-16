package com.steamdeck.mobile.data.local.database

import androidx.room.TypeConverter
import com.steamdeck.mobile.data.local.database.entity.Box64Preset
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import com.steamdeck.mobile.data.local.database.entity.GameSource

/**
 * Room用の型変換クラス
 * Enumやカスタムクラスをデータベースのプリミティブ型に変換
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
}
