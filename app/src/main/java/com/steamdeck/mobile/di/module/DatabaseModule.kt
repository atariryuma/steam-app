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
 * database関連 依存性注入module
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

 /**
  * databaseマイグレーション 1→2
  *
  * 変更内容:
  * - downloads.error カラムdelete
  * - DownloadStatus.ERROR delete
  */
 private val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(database: SupportSQLiteDatabase) {
   // downloadsテーブル from errorカラムdeletedoため、新しいテーブルcreate
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

   // 既存datacopy（ERROR FAILED conversion）
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

   // 古いテーブルdelete
   database.execSQL("DROP TABLE downloads")

   // 新しいテーブルリネーム
   database.execSQL("ALTER TABLE downloads_new RENAME TO downloads")
  }
 }

 /**
  * databaseマイグレーション 2→3
  *
  * 変更内容:
  * - controller_profiles テーブルadd
  */
 private val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(database: SupportSQLiteDatabase) {
   // controller_profilesテーブルcreate
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

   // インデックスcreate（パフォーマンス最適化）
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
  * databaseマイグレーション 3→4
  *
  * 変更内容:
  * - steam_installations テーブルadd
  *
  * Best Practice: Explicit schema definition for migration
  * Reference: https://developer.android.com/training/data-storage/room/migrating-db-versions
  */
 private val MIGRATION_3_4 = object : Migration(3, 4) {
  override fun migrate(database: SupportSQLiteDatabase) {
   // steam_installationsテーブルcreate
   database.execSQL("""
    CREATE TABLE IF NOT EXISTS steam_installations (
     id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
     container_id TEXT NOT NULL,
     install_path TEXT NOT NULL,
     status TEXT NOT NULL,
     version TEXT,
     installed_at INTEGER NOT NULL,
     last_launched_at INTEGER
    )
   """.trimIndent())

   // インデックスcreate（パフォーマンス最適化）
   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_steam_installations_container_id
    ON steam_installations(container_id)
   """.trimIndent())

   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_steam_installations_status
    ON steam_installations(status)
   """.trimIndent())
  }
 }

 /**
  * Database Migration 4→5
  *
  * Changes:
  * - Add indexes to games table for performance optimization
  */
 private val MIGRATION_4_5 = object : Migration(4, 5) {
  override fun migrate(database: SupportSQLiteDatabase) {
   // Add performance indexes to games table
   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_source
    ON games(source)
   """.trimIndent())

   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_isFavorite
    ON games(isFavorite)
   """.trimIndent())

   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_lastPlayedTimestamp
    ON games(lastPlayedTimestamp DESC)
   """.trimIndent())

   database.execSQL("""
    CREATE UNIQUE INDEX IF NOT EXISTS index_games_steamAppId
    ON games(steamAppId)
   """.trimIndent())

   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_name
    ON games(name)
   """.trimIndent())
  }
 }

 /**
  * Database Migration 5→6
  *
  * Changes:
  * - Add installationStatus column to downloads table
  */
 private val MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(database: SupportSQLiteDatabase) {
   // Add installationStatus column with default value
   database.execSQL("""
    ALTER TABLE downloads
    ADD COLUMN installationStatus TEXT NOT NULL DEFAULT 'NOT_INSTALLED'
   """.trimIndent())
  }
 }

 /**
  * Database Migration 6→7
  *
  * Changes:
  * - Add indexes to downloads table for query optimization
  * - Ensure games indexes match current schema (fixes older migrations)
  */
 private val MIGRATION_6_7 = object : Migration(6, 7) {
  override fun migrate(database: SupportSQLiteDatabase) {
   // Add indexes for frequently queried columns
   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_downloads_status
    ON downloads(status)
   """.trimIndent())

   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_downloads_gameId
    ON downloads(gameId)
   """.trimIndent())

   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_downloads_installationStatus
    ON downloads(installationStatus)
   """.trimIndent())

   // Ensure games indexes are aligned with current Room schema
   database.execSQL("DROP INDEX IF EXISTS index_games_lastPlayedTimestamp")
   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_lastPlayedTimestamp
    ON games(lastPlayedTimestamp DESC)
   """.trimIndent())
   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_source
    ON games(source)
   """.trimIndent())
   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_isFavorite
    ON games(isFavorite)
   """.trimIndent())
   database.execSQL("""
    CREATE UNIQUE INDEX IF NOT EXISTS index_games_steamAppId
    ON games(steamAppId)
   """.trimIndent())
   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_name
    ON games(name)
   """.trimIndent())
  }
 }

 /**
  * Database Migration 7→8
  *
  * Changes:
  * - Add installation status tracking to games table
  * - Add installationStatus column (NOT_INSTALLED, DOWNLOADING, INSTALLING, INSTALLED, etc.)
  * - Add installProgress column (0-100)
  * - Add statusUpdatedTimestamp column for tracking status changes
  * - Add index on installationStatus for efficient filtering
  */
 private val MIGRATION_7_8 = object : Migration(7, 8) {
  override fun migrate(database: SupportSQLiteDatabase) {
   // Add installation status columns to games table
   database.execSQL("""
    ALTER TABLE games
    ADD COLUMN installationStatus TEXT NOT NULL DEFAULT 'NOT_INSTALLED'
   """.trimIndent())

   database.execSQL("""
    ALTER TABLE games
    ADD COLUMN installProgress INTEGER NOT NULL DEFAULT 0
   """.trimIndent())

   database.execSQL("""
    ALTER TABLE games
    ADD COLUMN statusUpdatedTimestamp INTEGER
   """.trimIndent())

   // Add index on installationStatus for filtering (e.g., show only installed games)
   database.execSQL("""
    CREATE INDEX IF NOT EXISTS index_games_installationStatus
    ON games(installationStatus)
   """.trimIndent())
  }
 }

 /**
  * Provides SteamDeckDatabase instance
  *
  * Security & Performance Best Practices 2025:
  * - All migrations implemented (4→5, 5→6, 6→7, 7→8)
  * - fallbackToDestructiveMigration() REMOVED for production safety
  * - User data is preserved across app updates
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
   .addMigrations(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8
   )
   // Production-ready: Destructive migration removed to protect user data
   // Reference: https://developer.android.com/training/data-storage/room/migrating-db-versions
   // .fallbackToDestructiveMigration() // REMOVED - causes data loss on schema changes
   .build()
 }

 /**
  * GameDao提供
  */
 @Provides
 @Singleton
 fun provideGameDao(database: SteamDeckDatabase): GameDao {
  return database.gameDao()
 }

 /**
  * WinlatorContainerDao提供
  */
 @Provides
 @Singleton
 fun provideWinlatorContainerDao(database: SteamDeckDatabase): WinlatorContainerDao {
  return database.winlatorContainerDao()
 }

 /**
  * DownloadDao提供
  */
 @Provides
 @Singleton
 fun provideDownloadDao(database: SteamDeckDatabase): DownloadDao {
  return database.downloadDao()
 }
}
