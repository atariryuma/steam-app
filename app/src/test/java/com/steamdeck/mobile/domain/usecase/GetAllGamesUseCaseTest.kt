package com.steamdeck.mobile.domain.usecase

import app.cash.turbine.test
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.repository.GameRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * GetAllGamesUseCaseの単体テスト
 *
 * テスト対象:
 * - Repository からのゲーム一覧取得
 * - Flow の適切な伝播
 * - 空リストのハンドリング
 *
 * Best Practices:
 * - UseCase layer is thin, so focus on integration with repository
 * - Turbine for Flow testing
 * - Simple delegation pattern validation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetAllGamesUseCaseTest {

    private lateinit var useCase: GetAllGamesUseCase
    private lateinit var gameRepository: GameRepository

    private val mockGame1 = Game(
        id = 1L,
        name = "Portal 2",
        steamAppId = 620L,
        executablePath = "/games/portal2/portal2.exe",
        installPath = "/games/portal2",
        source = GameSource.STEAM
    )

    private val mockGame2 = Game(
        id = 2L,
        name = "Half-Life 2",
        steamAppId = 220L,
        executablePath = "/games/hl2/hl2.exe",
        installPath = "/games/hl2",
        source = GameSource.STEAM
    )

    @Before
    fun setup() {
        gameRepository = mockk()
    }

    /**
     * ゲーム一覧が正しく取得されるテスト
     */
    @Test
    fun `invoke returns flow of games from repository`() = runTest {
        // Given
        val mockGames = listOf(mockGame1, mockGame2)
        coEvery { gameRepository.getAllGames() } returns flowOf(mockGames)

        useCase = GetAllGamesUseCase(gameRepository)

        // When
        val result = useCase()

        // Then
        result.test {
            val games = awaitItem()
            assertEquals(2, games.size)
            assertEquals(mockGame1, games[0])
            assertEquals(mockGame2, games[1])
            awaitComplete()
        }
    }

    /**
     * 空のゲームリストが返されるテスト
     */
    @Test
    fun `invoke returns empty list when no games exist`() = runTest {
        // Given
        coEvery { gameRepository.getAllGames() } returns flowOf(emptyList())

        useCase = GetAllGamesUseCase(gameRepository)

        // When
        val result = useCase()

        // Then
        result.test {
            val games = awaitItem()
            assertTrue(games.isEmpty())
            awaitComplete()
        }
    }

    /**
     * Flowが複数の値を発行する場合のテスト
     */
    @Test
    fun `invoke propagates multiple emissions from repository`() = runTest {
        // Given
        val firstEmission = listOf(mockGame1)
        val secondEmission = listOf(mockGame1, mockGame2)

        coEvery { gameRepository.getAllGames() } returns flowOf(firstEmission, secondEmission)

        useCase = GetAllGamesUseCase(gameRepository)

        // When
        val result = useCase()

        // Then
        result.test {
            val first = awaitItem()
            assertEquals(1, first.size)

            val second = awaitItem()
            assertEquals(2, second.size)

            awaitComplete()
        }
    }

    /**
     * 大量のゲームが返される場合のテスト
     */
    @Test
    fun `invoke handles large game lists`() = runTest {
        // Given
        val largeGameList = (1..1000).map { index ->
            Game(
                id = index.toLong(),
                name = "Game $index",
                steamAppId = index.toLong(),
                executablePath = "/games/game$index/game.exe",
                installPath = "/games/game$index",
                source = GameSource.STEAM
            )
        }
        coEvery { gameRepository.getAllGames() } returns flowOf(largeGameList)

        useCase = GetAllGamesUseCase(gameRepository)

        // When
        val result = useCase()

        // Then
        result.test {
            val games = awaitItem()
            assertEquals(1000, games.size)
            assertEquals("Game 1", games.first().name)
            assertEquals("Game 1000", games.last().name)
            awaitComplete()
        }
    }

    /**
     * 異なるソースのゲームが混在する場合のテスト
     */
    @Test
    fun `invoke returns games from multiple sources`() = runTest {
        // Given
        val steamGame = mockGame1.copy(source = GameSource.STEAM)
        val importedGame = mockGame2.copy(source = GameSource.IMPORTED)
        val mixedGames = listOf(steamGame, importedGame)

        coEvery { gameRepository.getAllGames() } returns flowOf(mixedGames)

        useCase = GetAllGamesUseCase(gameRepository)

        // When
        val result = useCase()

        // Then
        result.test {
            val games = awaitItem()
            assertEquals(2, games.size)
            assertEquals(GameSource.STEAM, games[0].source)
            assertEquals(GameSource.IMPORTED, games[1].source)
            awaitComplete()
        }
    }
}
