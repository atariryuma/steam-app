package com.steamdeck.mobile.di.module

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.dao.DownloadDao
import com.steamdeck.mobile.data.local.database.dao.GameDao
import com.steamdeck.mobile.data.local.database.dao.WinlatorContainerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * データベース関連の依存性注入モジュール
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * データベースマイグレーション 1→2
     *
     * 変更内容:
     * - downloads.error カラムを削除
     * - DownloadStatus.ERROR を削除
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // downloadsテーブルからerrorカラムを削除するため、新しいテーブルを作成
            database.execSQL("""
                CREATE TABLE downloads_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    gameId INTEGER,
                    fileName TEXT NOT NULL,
                    url TEXT NOT NULL,
                    status TEXT NOT NULL,
                    progress INTEGER NOT NULL DEFAULT 0,
                    downloadedBytes INTEGER NOT NULL DEFAULT 0,
                    totalBytes INTEGER NOT NULL DEFAULT 0,
                    speedBytesPerSecond INTEGER NOT NULL DEFAULT 0,
                    destinationPath TEXT NOT NULL DEFAULT '',
                    startedTimestamp INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    completedTimestamp INTEGER,
                    errorMessage TEXT
                )
            """.trimIndent())

            // 既存データをコピー（ERRORはFAILEDに変換）
            database.execSQL("""
                INSERT INTO downloads_new
                SELECT
                    id, gameId, fileName, url,
                    CASE WHEN status = 'ERROR' THEN 'FAILED' ELSE status END as status,
                    progress, downloadedBytes, totalBytes, speedBytesPerSecond,
                    destinationPath, startedTimestamp, createdAt, updatedAt,
                    completedTimestamp, errorMessage
                FROM downloads
            """.trimIndent())

            // 古いテーブルを削除
            database.execSQL("DROP TABLE downloads")

            // 新しいテーブルをリネーム
            database.execSQL("ALTER TABLE downloads_new RENAME TO downloads")
        }
    }

    /**
     * データベースマイグレーション 2→3
     *
     * 変更内容:
     * - controller_profiles テーブルを追加
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // controller_profilesテーブルを作成
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS controller_profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    controllerId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    buttonA TEXT NOT NULL,
                    buttonB TEXT NOT NULL,
                    buttonX TEXT NOT NULL,
                    buttonY TEXT NOT NULL,
                    buttonL1 TEXT NOT NULL,
                    buttonR1 TEXT NOT NULL,
                    buttonL2 TEXT NOT NULL,
                    buttonR2 TEXT NOT NULL,
                    buttonStart TEXT NOT NULL,
                    buttonSelect TEXT NOT NULL,
                    dpadUp TEXT NOT NULL,
                    dpadDown TEXT NOT NULL,
                    dpadLeft TEXT NOT NULL,
                    dpadRight TEXT NOT NULL,
                    leftStickButton TEXT NOT NULL,
                    rightStickButton TEXT NOT NULL,
                    vibrationEnabled INTEGER NOT NULL DEFAULT 1,
                    deadzone REAL NOT NULL DEFAULT 0.1,
                    createdAt INTEGER NOT NULL,
                    lastUsedAt INTEGER NOT NULL
                )
            """.trimIndent())

            // インデックスを作成（パフォーマンス最適化）
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_controller_profiles_controllerId
                ON controller_profiles(controllerId)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_controller_profiles_lastUsedAt
                ON controller_profiles(lastUsedAt)
            """.trimIndent())
        }
    }

    /**
     * SteamDeckDatabaseインスタンスを提供
     */
    @Provides
    @Singleton
    fun provideSteamDeckDatabase(
        @ApplicationContext context: Context
    ): SteamDeckDatabase {
        return Room.databaseBuilder(
            context,
            SteamDeckDatabase::class.java,
            SteamDeckDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            // Best Practice: fallbackToDestructiveMigration()削除（本番環境ではユーザーデータ保護）
            // Reference: https://medium.com/androiddevelopers/understanding-migrations-with-room-f01e04b07929
            // .fallbackToDestructiveMigration() // <- MVP段階のみ使用、本番では削除
            .build()
    }

    /**
     * GameDaoを提供
     */
    @Provides
    @Singleton
    fun provideGameDao(database: SteamDeckDatabase): GameDao {
        return database.gameDao()
    }

    /**
     * WinlatorContainerDaoを提供
     */
    @Provides
    @Singleton
    fun provideWinlatorContainerDao(database: SteamDeckDatabase): WinlatorContainerDao {
        return database.winlatorContainerDao()
    }

    /**
     * DownloadDaoを提供
     */
    @Provides
    @Singleton
    fun provideDownloadDao(database: SteamDeckDatabase): DownloadDao {
        return database.downloadDao()
    }
}
