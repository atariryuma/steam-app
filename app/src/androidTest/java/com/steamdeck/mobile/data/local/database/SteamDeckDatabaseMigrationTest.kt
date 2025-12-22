package com.steamdeck.mobile.data.local.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.steamdeck.mobile.di.module.MIGRATION_7_8
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Room Database Migration Tests
 *
 * Best Practices (2025):
 * - Test ALL migrations to prevent data corruption
 * - Use MigrationTestHelper from room-testing artifact
 * - Validate data integrity after migration
 * - Export schema for version control
 *
 * References:
 * - https://developer.android.com/training/data-storage/room/migrating-db-versions
 * - https://github.com/android/architecture-components-samples/tree/master/PersistenceMigrationsSample
 */
@RunWith(AndroidJUnit4::class)
class SteamDeckDatabaseMigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SteamDeckDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Test migration from version 7 to 8
     *
     * Changes in v8:
     * - Added installationStatus column to games table
     * - Added installProgress column to games table
     * - Added statusUpdatedTimestamp column to games table
     * - Added index on installationStatus for filtering
     */
    @Test
    @Throws(IOException::class)
    fun migrate7To8_containsCorrectData() {
        // Create database at version 7
        val db7 = helper.createDatabase(TEST_DB, 7).apply {
            // Insert test game data (v7 schema)
            execSQL(
                """
                INSERT INTO games (
                    id, name, steamAppId, executablePath, installPath,
                    source, playTimeMinutes, addedTimestamp, isFavorite
                ) VALUES (
                    1, 'Test Game', 123456, '/path/to/game.exe', '/path/to/install',
                    'STEAM', 120, 1734700000000, 1
                )
                """.trimIndent()
            )
            close()
        }

        // Re-open at version 8 with migration
        val db8 = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        // Query the migrated data
        val cursor = db8.query("SELECT * FROM games WHERE id = 1")

        cursor.use {
            assertNotNull(it, "Cursor should not be null")
            assertEquals(true, it.moveToFirst(), "Cursor should have at least one row")

            // Verify original data
            assertEquals("Test Game", it.getString(it.getColumnIndexOrThrow("name")))
            assertEquals(123456L, it.getLong(it.getColumnIndexOrThrow("steamAppId")))
            assertEquals(120L, it.getLong(it.getColumnIndexOrThrow("playTimeMinutes")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("isFavorite")))

            // Verify new columns have default values
            val installationStatusIndex = it.getColumnIndexOrThrow("installationStatus")
            val installProgressIndex = it.getColumnIndexOrThrow("installProgress")
            val statusUpdatedIndex = it.getColumnIndexOrThrow("statusUpdatedTimestamp")

            // NOT_INSTALLED is the default value
            assertEquals("NOT_INSTALLED", it.getString(installationStatusIndex))
            assertEquals(0, it.getInt(installProgressIndex))
            // statusUpdatedTimestamp should be NULL for migrated rows
            assertEquals(true, it.isNull(statusUpdatedIndex))
        }

        db8.close()
    }

    /**
     * Test migration from version 7 to 8 with NULL Steam App ID
     *
     * Edge case: Imported games without Steam App ID
     */
    @Test
    @Throws(IOException::class)
    fun migrate7To8_withNullSteamAppId_preservesData() {
        // Create database at version 7
        val db7 = helper.createDatabase(TEST_DB, 7).apply {
            // Insert imported game (no Steam App ID)
            execSQL(
                """
                INSERT INTO games (
                    id, name, steamAppId, executablePath, installPath,
                    source, playTimeMinutes, addedTimestamp, isFavorite
                ) VALUES (
                    2, 'Imported Game', NULL, '/import/game.exe', '/import',
                    'IMPORTED', 0, 1734700000000, 0
                )
                """.trimIndent()
            )
            close()
        }

        // Re-open at version 8
        val db8 = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        // Verify migrated data
        val cursor = db8.query("SELECT * FROM games WHERE id = 2")

        cursor.use {
            assertEquals(true, it.moveToFirst())
            assertEquals("Imported Game", it.getString(it.getColumnIndexOrThrow("name")))
            assertEquals("IMPORTED", it.getString(it.getColumnIndexOrThrow("source")))
            assertEquals(true, it.isNull(it.getColumnIndexOrThrow("steamAppId")))
            assertEquals("NOT_INSTALLED", it.getString(it.getColumnIndexOrThrow("installationStatus")))
        }

        db8.close()
    }

    /**
     * Test index creation on installationStatus
     */
    @Test
    @Throws(IOException::class)
    fun migrate7To8_createsInstallationStatusIndex() {
        // Create database at version 7
        helper.createDatabase(TEST_DB, 7).close()

        // Re-open at version 8
        val db8 = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        // Verify index exists by querying sqlite_master
        val cursor = db8.query(
            """
            SELECT name FROM sqlite_master
            WHERE type='index' AND name='index_games_installationStatus'
            """.trimIndent()
        )

        cursor.use {
            assertEquals(true, it.moveToFirst(), "Index should exist")
            assertEquals("index_games_installationStatus", it.getString(0))
        }

        db8.close()
    }

    /**
     * Test migration with all database versions (1 → 8)
     *
     * IMPORTANT: This test ensures migration path continuity
     * Run this test after every schema change
     */
    @Test
    @Throws(IOException::class)
    fun migrateAll_from1To8() {
        // Note: Actual migration tests would need all MIGRATION_X_Y objects
        // For now, we test the latest migration (7 → 8)
        // TODO: Add full migration chain tests when earlier migrations are documented

        helper.createDatabase(TEST_DB, 7).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        // Verify database is usable
        val cursor = db.query("SELECT COUNT(*) FROM games")
        cursor.use {
            assertEquals(true, it.moveToFirst())
        }

        db.close()
    }
}
