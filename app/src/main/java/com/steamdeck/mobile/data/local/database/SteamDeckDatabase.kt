package com.steamdeck.mobile.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.steamdeck.mobile.data.local.database.dao.DownloadDao
import com.steamdeck.mobile.data.local.database.dao.GameDao
import com.steamdeck.mobile.data.local.database.dao.WinlatorContainerDao
import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.data.local.database.entity.GameEntity
import com.steamdeck.mobile.data.local.database.entity.WinlatorContainerEntity

/**
 * SteamDeck Mobile アプリケーションのメインデータベース
 */
@Database(
    entities = [
        GameEntity::class,
        WinlatorContainerEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SteamDeckDatabase : RoomDatabase() {
    /**
     * ゲーム情報へのDAO
     */
    abstract fun gameDao(): GameDao

    /**
     * Winlatorコンテナ設定へのDAO
     */
    abstract fun winlatorContainerDao(): WinlatorContainerDao

    /**
     * ダウンロード履歴へのDAO
     */
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "steamdeck_mobile.db"
    }
}
