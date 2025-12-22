package com.steamdeck.mobile.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.steamdeck.mobile.di.module.DatabaseModule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Database Migration Tests
 *
 * CRITICAL: Tests all migrations from v1 to v8 to prevent data loss
 * in production during app updates.
 *
 * Best Practice (2025):
 * - Test EVERY migration path
 * - Verify data preservation
 * - Test schema changes
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SteamDeckDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Test Migration 7 -> 8 (InstallationStatus fields added to GameEntity)
     *
     * Changes:
     * - Added installationStatus column (TEXT NOT NULL DEFAULT 'NOT_INSTALLED')
     * - Added installProgress column (INTEGER NOT NULL DEFAULT 0)
     * - Added statusUpdatedTimestamp column (INTEGER)
     * - Added index on installationStatus
     */
    @Test
    fun migrate7To8_preservesGameData() {
        // Create v7 database and insert test data
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL("""
                INSERT INTO games (
                    id, name, executablePath, iconPath, steamAppId,
                    source, isFavorite, totalPlayTimeMinutes, lastPlayedTimestamp,
                    createdAt, updatedAt
                ) VALUES (
                    1, 'Test Game', '/path/to/game.exe', null, 12345,
                    'STEAM', 1, 120, ${System.currentTimeMillis()},
                    ${System.currentTimeMillis()}, ${System.currentTimeMillis()}
                )
            """.trimIndent())
            close()
        }

        // Run migration to v8
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            DatabaseModule.MIGRATION_7_8
        )

        // Verify data is preserved
        db.query("SELECT * FROM games WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst(), "Game data should be preserved")
            assertEquals("Test Game", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("/path/to/game.exe", cursor.getString(cursor.getColumnIndexOrThrow("executablePath")))
            assertEquals(12345, cursor.getInt(cursor.getColumnIndexOrThrow("steamAppId")))

            // Verify new columns have default values
            assertEquals("NOT_INSTALLED", cursor.getString(cursor.getColumnIndexOrThrow("installationStatus")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("installProgress")))
        }

        db.close()
    }

    /**
     * Test Migration 6 -> 7 (Download indexes added)
     */
    @Test
    fun migrate6To7_preservesDownloadData() {
        // Create v6 database
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL("""
                INSERT INTO downloads (
                    id, gameId, fileName, url, status, progress,
                    downloadedBytes, totalBytes, speedBytesPerSecond,
                    destinationPath, startedTimestamp, createdAt, updatedAt
                ) VALUES (
                    1, 1, 'test.zip', 'https://example.com/test.zip', 'COMPLETED', 100,
                    1024000, 1024000, 0,
                    '/path/to/test.zip', ${System.currentTimeMillis()},
                    ${System.currentTimeMillis()}, ${System.currentTimeMillis()}
                )
            """.trimIndent())
            close()
        }

        // Run migration to v7
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            DatabaseModule.MIGRATION_6_7
        )

        // Verify data is preserved
        db.query("SELECT * FROM downloads WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("test.zip", cursor.getString(cursor.getColumnIndexOrThrow("fileName")))
            assertEquals("COMPLETED", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertEquals(100, cursor.getInt(cursor.getColumnIndexOrThrow("progress")))
        }

        db.close()
    }

    /**
     * Test full migration chain: v1 -> v8
     *
     * CRITICAL: Ensures users upgrading from v1 don't lose data
     */
    @Test
    fun migrateAll_fromV1ToV8() {
        // Create v1 database with initial schema
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO games (
                    id, name, executablePath, iconPath,
                    createdAt, updatedAt
                ) VALUES (
                    1, 'Original Game', '/old/path/game.exe', null,
                    ${System.currentTimeMillis()}, ${System.currentTimeMillis()}
                )
            """.trimIndent())
            close()
        }

        // Run ALL migrations sequentially
        helper.runMigrationsAndValidate(
            TEST_DB,
            8,
            true,
            DatabaseModule.MIGRATION_1_2,
            DatabaseModule.MIGRATION_2_3,
            DatabaseModule.MIGRATION_3_4,
            DatabaseModule.MIGRATION_4_5,
            DatabaseModule.MIGRATION_5_6,
            DatabaseModule.MIGRATION_6_7,
            DatabaseModule.MIGRATION_7_8
        ).apply {
            // Verify original data survived all migrations
            query("SELECT * FROM games WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst(), "Original game data should survive all migrations")
                assertEquals("Original Game", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                assertEquals("/old/path/game.exe", cursor.getString(cursor.getColumnIndexOrThrow("executablePath")))

                // Verify v8 columns exist with defaults
                assertEquals("NOT_INSTALLED", cursor.getString(cursor.getColumnIndexOrThrow("installationStatus")))
            }
            close()
        }
    }
}
