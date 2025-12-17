package com.steamdeck.mobile.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.dao.GameDao
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GameRepositoryImpl の統合テスト
 *
 * Room In-Memory Database を使用した実際の DB 操作テスト
 *
 * テスト対象:
 * - ゲームの挿入・取得・更新・削除
 * - Flow によるリアルタイム更新
 * - 検索機能
 * - お気に入り機能
 * - プレイ時間記録
 *
 * Best Practices 2025:
 * - In-memory database for fast, isolated tests
 * - Turbine for Flow testing
 * - AndroidJUnit4 test runner
 * - Real repository integration testing
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GameRepositoryImplTest {

    private lateinit var database: SteamDeckDatabase
    private lateinit var gameDao: GameDao
    private lateinit var repository: GameRepositoryImpl

    private val mockGame1 = Game(
        id = 0, // Auto-generated
        name = "Portal 2",
        steamAppId = 620L,
        executablePath = "/games/portal2/portal2.exe",
        installPath = "/games/portal2",
        source = GameSource.STEAM,
        playTimeMinutes = 180L,
        isFavorite = false
    )

    private val mockGame2 = Game(
        id = 0,
        name = "Half-Life 2",
        steamAppId = 220L,
        executablePath = "/games/hl2/hl2.exe",
        installPath = "/games/hl2",
        source = GameSource.STEAM,
        playTimeMinutes = 300L,
        isFavorite = true
    )

    private val mockImportedGame = Game(
        id = 0,
        name = "Custom Game",
        steamAppId = null,
        executablePath = "/custom/game.exe",
        installPath = "/custom",
        source = GameSource.IMPORTED,
        playTimeMinutes = 0L,
        isFavorite = false
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // In-memory database for fast, isolated tests
        database = Room.inMemoryDatabaseBuilder(
            context,
            SteamDeckDatabase::class.java
        )
            .allowMainThreadQueries() // For testing only
            .build()

        gameDao = database.gameDao()
        repository = GameRepositoryImpl(gameDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * ゲームの挿入と取得が正しく動作するテスト
     */
    @Test
    fun insertAndRetrieveGame() = runTest {
        // When
        val insertedId = repository.insertGame(mockGame1)
        val retrievedGame = repository.getGameById(insertedId)

        // Then
        assertNotNull(retrievedGame)
        assertEquals(insertedId, retrievedGame?.id)
        assertEquals("Portal 2", retrievedGame?.name)
        assertEquals(620L, retrievedGame?.steamAppId)
        assertEquals(GameSource.STEAM, retrievedGame?.source)
    }

    /**
     * getAllGamesがFlowで全ゲームを返すテスト
     */
    @Test
    fun getAllGamesReturnsAllInsertedGames() = runTest {
        // Given
        repository.insertGame(mockGame1)
        repository.insertGame(mockGame2)

        // When & Then
        repository.getAllGames().test {
            val games = awaitItem()
            assertEquals(2, games.size)
            assertTrue(games.any { it.name == "Portal 2" })
            assertTrue(games.any { it.name == "Half-Life 2" })
        }
    }

    /**
     * 複数ゲームを一括挿入するテスト
     */
    @Test
    fun insertGamesInsertsMultipleGames() = runTest {
        // When
        repository.insertGames(listOf(mockGame1, mockGame2, mockImportedGame))

        // Then
        repository.getAllGames().test {
            val games = awaitItem()
            assertEquals(3, games.size)
        }
    }

    /**
     * ゲームの更新が正しく動作するテスト
     */
    @Test
    fun updateGameModifiesExistingGame() = runTest {
        // Given
        val insertedId = repository.insertGame(mockGame1)
        val insertedGame = repository.getGameById(insertedId)!!

        // When
        val updatedGame = insertedGame.copy(
            name = "Portal 2 Updated",
            playTimeMinutes = 500L
        )
        repository.updateGame(updatedGame)

        // Then
        val retrievedGame = repository.getGameById(insertedId)
        assertEquals("Portal 2 Updated", retrievedGame?.name)
        assertEquals(500L, retrievedGame?.playTimeMinutes)
    }

    /**
     * ゲームの削除が正しく動作するテスト
     */
    @Test
    fun deleteGameRemovesGameFromDatabase() = runTest {
        // Given
        val insertedId = repository.insertGame(mockGame1)
        val insertedGame = repository.getGameById(insertedId)!!

        // When
        repository.deleteGame(insertedGame)

        // Then
        val retrievedGame = repository.getGameById(insertedId)
        assertNull(retrievedGame)

        repository.getAllGames().test {
            val games = awaitItem()
            assertTrue(games.isEmpty())
        }
    }

    /**
     * 全ゲーム削除が正しく動作するテスト
     */
    @Test
    fun deleteAllGamesRemovesAllGames() = runTest {
        // Given
        repository.insertGames(listOf(mockGame1, mockGame2, mockImportedGame))

        // When
        repository.deleteAllGames()

        // Then
        repository.getAllGames().test {
            val games = awaitItem()
            assertTrue(games.isEmpty())
        }
    }

    /**
     * Steam App IDによる検索が正しく動作するテスト
     */
    @Test
    fun getGameBySteamAppIdReturnsCorrectGame() = runTest {
        // Given
        repository.insertGame(mockGame1)
        repository.insertGame(mockGame2)

        // When
        val retrievedGame = repository.getGameBySteamAppId(620L)

        // Then
        assertNotNull(retrievedGame)
        assertEquals("Portal 2", retrievedGame?.name)
        assertEquals(620L, retrievedGame?.steamAppId)
    }

    /**
     * ゲーム検索が部分一致で動作するテスト
     */
    @Test
    fun searchGamesReturnsMatchingGames() = runTest {
        // Given
        repository.insertGames(listOf(mockGame1, mockGame2, mockImportedGame))

        // When & Then - Search for "Half"
        repository.searchGames("Half").test {
            val games = awaitItem()
            assertEquals(1, games.size)
            assertEquals("Half-Life 2", games[0].name)
        }

        // When & Then - Search for "Portal"
        repository.searchGames("Portal").test {
            val games = awaitItem()
            assertEquals(1, games.size)
            assertEquals("Portal 2", games[0].name)
        }
    }

    /**
     * 検索が大文字小文字を区別しないことを確認するテスト
     */
    @Test
    fun searchGamesIsCaseInsensitive() = runTest {
        // Given
        repository.insertGame(mockGame1)

        // When & Then
        repository.searchGames("portal").test {
            val games = awaitItem()
            assertEquals(1, games.size)
            assertEquals("Portal 2", games[0].name)
        }

        repository.searchGames("PORTAL").test {
            val games = awaitItem()
            assertEquals(1, games.size)
        }
    }

    /**
     * ソース別のゲーム取得が正しく動作するテスト
     */
    @Test
    fun getGamesBySourceFiltersBySource() = runTest {
        // Given
        repository.insertGames(listOf(mockGame1, mockGame2, mockImportedGame))

        // When & Then - STEAM games
        repository.getGamesBySource(GameSource.STEAM).test {
            val steamGames = awaitItem()
            assertEquals(2, steamGames.size)
            assertTrue(steamGames.all { it.source == GameSource.STEAM })
        }

        // When & Then - IMPORTED games
        repository.getGamesBySource(GameSource.IMPORTED).test {
            val importedGames = awaitItem()
            assertEquals(1, importedGames.size)
            assertEquals(GameSource.IMPORTED, importedGames[0].source)
        }
    }

    /**
     * お気に入りゲームのみを取得するテスト
     */
    @Test
    fun getFavoriteGamesReturnsOnlyFavorites() = runTest {
        // Given
        repository.insertGames(listOf(mockGame1, mockGame2, mockImportedGame))

        // When & Then
        repository.getFavoriteGames().test {
            val favoriteGames = awaitItem()
            assertEquals(1, favoriteGames.size)
            assertEquals("Half-Life 2", favoriteGames[0].name)
            assertTrue(favoriteGames[0].isFavorite)
        }
    }

    /**
     * お気に入り状態の切り替えが正しく動作するテスト
     */
    @Test
    fun updateFavoriteStatusTogglesFavorite() = runTest {
        // Given
        val insertedId = repository.insertGame(mockGame1)

        // When - Set to favorite
        repository.updateFavoriteStatus(insertedId, true)

        // Then
        val favoritedGame = repository.getGameById(insertedId)
        assertTrue(favoritedGame?.isFavorite == true)

        // When - Remove from favorites
        repository.updateFavoriteStatus(insertedId, false)

        // Then
        val unfavoritedGame = repository.getGameById(insertedId)
        assertFalse(unfavoritedGame?.isFavorite == true)
    }

    /**
     * プレイ時間の更新が正しく動作するテスト
     */
    @Test
    fun updatePlayTimeIncreasesPlayTimeAndUpdatesTimestamp() = runTest {
        // Given
        val insertedId = repository.insertGame(mockGame1)
        val originalGame = repository.getGameById(insertedId)!!
        val originalPlayTime = originalGame.playTimeMinutes

        // When
        val additionalMinutes = 60L
        val timestamp = System.currentTimeMillis()
        repository.updatePlayTime(insertedId, additionalMinutes, timestamp)

        // Then
        val updatedGame = repository.getGameById(insertedId)
        assertEquals(originalPlayTime + additionalMinutes, updatedGame?.playTimeMinutes)
        assertEquals(timestamp, updatedGame?.lastPlayedTimestamp)
    }

    /**
     * Flowがリアルタイムで更新を通知することを確認するテスト
     */
    @Test
    fun getAllGamesFlowEmitsUpdatesWhenGamesChange() = runTest {
        // Given
        val flow = repository.getAllGames()

        flow.test {
            // Initial state: empty
            val emptyList = awaitItem()
            assertTrue(emptyList.isEmpty())

            // Insert first game
            repository.insertGame(mockGame1)
            val oneGame = awaitItem()
            assertEquals(1, oneGame.size)

            // Insert second game
            repository.insertGame(mockGame2)
            val twoGames = awaitItem()
            assertEquals(2, twoGames.size)
        }
    }

    /**
     * 存在しないゲームIDを検索した場合nullが返されるテスト
     */
    @Test
    fun getGameByIdReturnsNullForNonExistentId() = runTest {
        // When
        val nonExistentGame = repository.getGameById(99999L)

        // Then
        assertNull(nonExistentGame)
    }

    /**
     * 存在しないSteam App IDを検索した場合nullが返されるテスト
     */
    @Test
    fun getGameBySteamAppIdReturnsNullForNonExistentAppId() = runTest {
        // Given
        repository.insertGame(mockGame1)

        // When
        val nonExistentGame = repository.getGameBySteamAppId(999999L)

        // Then
        assertNull(nonExistentGame)
    }

    /**
     * 検索結果が空の場合、空のリストが返されるテスト
     */
    @Test
    fun searchGamesReturnsEmptyListWhenNoMatches() = runTest {
        // Given
        repository.insertGames(listOf(mockGame1, mockGame2))

        // When & Then
        repository.searchGames("NonexistentGame").test {
            val games = awaitItem()
            assertTrue(games.isEmpty())
        }
    }

    /**
     * 大量のゲームを挿入・取得できることを確認するテスト
     */
    @Test
    fun handlesLargeNumberOfGames() = runTest {
        // Given
        val largeGameList = (1..100).map { index ->
            mockGame1.copy(
                id = 0,
                name = "Game $index",
                steamAppId = index.toLong()
            )
        }

        // When
        repository.insertGames(largeGameList)

        // Then
        repository.getAllGames().test {
            val games = awaitItem()
            assertEquals(100, games.size)
        }
    }

    /**
     * 同一Steam App IDで重複挿入を試みた場合のテスト（UNIQUE制約）
     */
    @Test
    fun insertGameWithDuplicateSteamAppIdThrowsException() = runTest {
        // Given
        repository.insertGame(mockGame1)

        // When & Then
        try {
            repository.insertGame(mockGame1.copy(id = 0, name = "Duplicate"))
            fail("Expected exception for duplicate Steam App ID")
        } catch (e: Exception) {
            // Expected exception due to UNIQUE constraint
            assertTrue(e.message?.contains("UNIQUE") == true || e is android.database.sqlite.SQLiteConstraintException)
        }
    }
}
