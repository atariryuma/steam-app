package com.steamdeck.mobile.presentation.viewmodel

import app.cash.turbine.test
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.repository.GameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * HomeViewModelの単体テスト
 *
 * テスト対象:
 * - ゲーム一覧の読み込み（Loading → Success）
 * - 空の状態ハンドリング（Loading → Empty）
 * - エラーハンドリング（Loading → Error）
 * - 検索機能
 * - お気に入り切り替え
 * - リフレッシュ機能
 *
 * Best Practices:
 * - Turbine for Flow testing (2025 standard)
 * - MockK for mocking
 * - StandardTestDispatcher for coroutine testing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private lateinit var gameRepository: GameRepository
    private lateinit var testDispatcher: TestDispatcher

    // Mock data
    private val mockGame1 = Game(
        id = 1L,
        name = "Portal 2",
        steamAppId = 620L,
        executablePath = "/games/portal2/portal2.exe",
        installPath = "/games/portal2",
        source = GameSource.STEAM,
        playTimeMinutes = 120L,
        isFavorite = false
    )

    private val mockGame2 = Game(
        id = 2L,
        name = "Half-Life 2",
        steamAppId = 220L,
        executablePath = "/games/hl2/hl2.exe",
        installPath = "/games/hl2",
        source = GameSource.STEAM,
        playTimeMinutes = 300L,
        isFavorite = true
    )

    private val mockGames = listOf(mockGame1, mockGame2)

    @Before
    fun setup() {
        // Setup test dispatcher for coroutines
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        // Mock repository
        gameRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * ゲーム一覧の読み込みが成功する場合のテスト
     */
    @Test
    fun `loadGames emits Loading then Success when games are available`() = runTest {
        // Given
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)

        // When
        viewModel = HomeViewModel(gameRepository)

        // Then
        viewModel.uiState.test {
            // Initial state should be Loading
            val loadingState = awaitItem()
            assertTrue(loadingState is HomeUiState.Loading)

            // Advance coroutines to complete repository call
            testDispatcher.scheduler.advanceUntilIdle()

            // After loading, should emit Success
            val successState = awaitItem() as HomeUiState.Success
            assertEquals(2, successState.games.size)
            assertEquals(mockGame1, successState.games[0])
            assertEquals(mockGame2, successState.games[1])
        }
    }

    /**
     * ゲームが存在しない場合、Empty状態を返すテスト
     */
    @Test
    fun `loadGames emits Empty when no games are available`() = runTest {
        // Given
        coEvery { gameRepository.getAllGames() } returns flowOf(emptyList())

        // When
        viewModel = HomeViewModel(gameRepository)

        // Then
        viewModel.uiState.test {
            // Initial Loading
            assertTrue(awaitItem() is HomeUiState.Loading)

            testDispatcher.scheduler.advanceUntilIdle()

            // Should emit Empty
            val emptyState = awaitItem()
            assertTrue(emptyState is HomeUiState.Empty)
        }
    }

    /**
     * ゲーム読み込み中にエラーが発生した場合のテスト
     */
    @Test
    fun `loadGames emits Error when repository throws exception`() = runTest {
        // Given
        val errorMessage = "Database connection failed"
        coEvery { gameRepository.getAllGames() } throws RuntimeException(errorMessage)

        // When
        viewModel = HomeViewModel(gameRepository)

        // Then
        viewModel.uiState.test {
            assertTrue(awaitItem() is HomeUiState.Loading)

            testDispatcher.scheduler.advanceUntilIdle()

            // Should emit Error with message
            val errorState = awaitItem() as HomeUiState.Error
            assertEquals(errorMessage, errorState.message)
        }
    }

    /**
     * 検索機能が正常に動作するテスト
     */
    @Test
    fun `searchGames filters games correctly`() = runTest {
        // Given
        val query = "Portal"
        val filteredGames = listOf(mockGame1)
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)
        coEvery { gameRepository.searchGames(query) } returns flowOf(filteredGames)

        viewModel = HomeViewModel(gameRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.searchGames(query)

        // Then
        viewModel.searchQuery.test {
            assertEquals(query, awaitItem())
        }

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()

            val successState = awaitItem() as HomeUiState.Success
            assertEquals(1, successState.games.size)
            assertEquals("Portal 2", successState.games[0].name)
        }

        coVerify { gameRepository.searchGames(query) }
    }

    /**
     * 空の検索クエリで全ゲームが再読み込みされるテスト
     */
    @Test
    fun `searchGames with blank query reloads all games`() = runTest {
        // Given
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)

        viewModel = HomeViewModel(gameRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.searchGames("")

        // Then
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val successState = awaitItem() as HomeUiState.Success
            assertEquals(2, successState.games.size)
        }

        // Should call getAllGames again
        coVerify(atLeast = 2) { gameRepository.getAllGames() }
    }

    /**
     * 検索でゲームが見つからない場合のテスト
     */
    @Test
    fun `searchGames emits Empty when no results found`() = runTest {
        // Given
        val query = "NonexistentGame"
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)
        coEvery { gameRepository.searchGames(query) } returns flowOf(emptyList())

        viewModel = HomeViewModel(gameRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.searchGames(query)

        // Then
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val emptyState = awaitItem()
            assertTrue(emptyState is HomeUiState.Empty)
        }
    }

    /**
     * 検索中のエラーハンドリングのテスト
     */
    @Test
    fun `searchGames emits Error when search fails`() = runTest {
        // Given
        val query = "Test"
        val errorMessage = "Search failed"
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)
        coEvery { gameRepository.searchGames(query) } throws RuntimeException(errorMessage)

        viewModel = HomeViewModel(gameRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.searchGames(query)

        // Then
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val errorState = awaitItem() as HomeUiState.Error
            assertEquals("検索エラー", errorState.message)
        }
    }

    /**
     * お気に入り切り替えが正常に動作するテスト
     */
    @Test
    fun `toggleFavorite calls repository correctly`() = runTest {
        // Given
        val gameId = 1L
        val isFavorite = true
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)
        coEvery { gameRepository.updateFavoriteStatus(gameId, isFavorite) } returns Unit

        viewModel = HomeViewModel(gameRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.toggleFavorite(gameId, isFavorite)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { gameRepository.updateFavoriteStatus(gameId, isFavorite) }
    }

    /**
     * お気に入り切り替え時のエラーが適切にハンドリングされるテスト
     */
    @Test
    fun `toggleFavorite handles errors gracefully`() = runTest {
        // Given
        val gameId = 1L
        val isFavorite = true
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)
        coEvery { gameRepository.updateFavoriteStatus(gameId, isFavorite) } throws
            RuntimeException("Update failed")

        viewModel = HomeViewModel(gameRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.toggleFavorite(gameId, isFavorite)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        // Should not crash and state should remain Success
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Success)
        }
    }

    /**
     * リフレッシュ機能が正常に動作するテスト
     */
    @Test
    fun `refresh reloads games with Loading state`() = runTest {
        // Given
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)

        viewModel = HomeViewModel(gameRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.refresh()

        // Then
        viewModel.uiState.test {
            // Should emit Loading first
            val loadingState = awaitItem()
            assertTrue(loadingState is HomeUiState.Loading)

            testDispatcher.scheduler.advanceUntilIdle()

            // Then Success
            val successState = awaitItem() as HomeUiState.Success
            assertEquals(2, successState.games.size)
        }

        // Should call getAllGames at least twice (init + refresh)
        coVerify(atLeast = 2) { gameRepository.getAllGames() }
    }

    /**
     * 複数回のリフレッシュが正常に動作するテスト
     */
    @Test
    fun `multiple refresh calls work correctly`() = runTest {
        // Given
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)

        viewModel = HomeViewModel(gameRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(atLeast = 3) { gameRepository.getAllGames() }
    }
}
