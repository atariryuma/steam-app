package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.data.remote.steam.model.SteamGame
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.repository.GameRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * SyncSteamLibraryUseCaseの単体テスト
 *
 * テスト対象:
 * - Steam APIからのゲーム一覧取得と同期
 * - 重複ゲームのスキップ処理
 * - 画像ダウンロード処理
 * - エラーハンドリング
 *
 * Best Practices:
 * - Android Context mocking
 * - File system mocking (avoid actual I/O)
 * - Complex integration testing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncSteamLibraryUseCaseTest {

    private lateinit var useCase: SyncSteamLibraryUseCase
    private lateinit var context: Context
    private lateinit var steamRepository: SteamRepository
    private lateinit var gameRepository: GameRepository

    private val mockApiKey = "ABC123DEF"
    private val mockSteamId = "76561198012345678"

    private val mockSteamGame1 = SteamGame(
        appId = 620L,
        name = "Portal 2",
        playtimeMinutes = 180L,
        playtime2Weeks = 60L,
        imgIconUrl = "icon_hash_620",
        imgLogoUrl = "logo_hash_620",
        hasCommunityVisibleStats = true
    )

    private val mockSteamGame2 = SteamGame(
        appId = 220L,
        name = "Half-Life 2",
        playtimeMinutes = 300L,
        playtime2Weeks = null,
        imgIconUrl = "icon_hash_220",
        imgLogoUrl = "logo_hash_220",
        hasCommunityVisibleStats = false
    )

    private val mockExistingGame = Game(
        id = 1L,
        name = "Portal 2",
        steamAppId = 620L, // Same as mockSteamGame1
        executablePath = "",
        installPath = "",
        source = GameSource.STEAM
    )

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        steamRepository = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)

        // Mock context file directories
        val mockFilesDir = mockk<File>(relaxed = true)
        every { mockFilesDir.absolutePath } returns "/data/files"
        every { context.filesDir } returns mockFilesDir
    }

    /**
     * Steam同期が成功して全ゲームが追加されるテスト
     */
    @Test
    fun `invoke syncs all games successfully when no existing games`() = runTest {
        // Given
        val steamGames = listOf(mockSteamGame1, mockSteamGame2)
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(steamGames)
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(emptyList())
        coEvery { steamRepository.downloadGameImage(any(), any()) } returns Result.success(Unit)
        coEvery { gameRepository.insertGame(any()) } returns 1L

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        coVerify(exactly = 2) { gameRepository.insertGame(any()) }
        coVerify(exactly = 4) { steamRepository.downloadGameImage(any(), any()) } // 2 games * 2 images
    }

    /**
     * 既存ゲームが重複してスキップされるテスト
     */
    @Test
    fun `invoke skips existing games and only adds new ones`() = runTest {
        // Given
        val steamGames = listOf(mockSteamGame1, mockSteamGame2)
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(steamGames)
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(listOf(mockExistingGame))
        coEvery { steamRepository.downloadGameImage(any(), any()) } returns Result.success(Unit)
        coEvery { gameRepository.insertGame(any()) } returns 2L

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()) // Only 1 new game (mockSteamGame2)

        coVerify(exactly = 1) { gameRepository.insertGame(any()) }
        coVerify(exactly = 2) { steamRepository.downloadGameImage(any(), any()) } // Only for new game
    }

    /**
     * Steamゲームが存在しない場合、同期数が0になるテスト
     */
    @Test
    fun `invoke returns 0 when Steam library is empty`() = runTest {
        // Given
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(emptyList())

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())

        coVerify(exactly = 0) { gameRepository.insertGame(any()) }
        coVerify(exactly = 0) { steamRepository.downloadGameImage(any(), any()) }
    }

    /**
     * Steam API呼び出しが失敗した場合のテスト
     */
    @Test
    fun `invoke returns failure when Steam API call fails`() = runTest {
        // Given
        val errorMessage = "Invalid API key"
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns
            Result.failure(RuntimeException(errorMessage))

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(errorMessage, result.exceptionOrNull()?.message)

        coVerify(exactly = 0) { gameRepository.insertGame(any()) }
    }

    /**
     * 画像ダウンロードが失敗してもゲーム同期は継続されるテスト
     */
    @Test
    fun `invoke continues syncing even when image download fails`() = runTest {
        // Given
        val steamGames = listOf(mockSteamGame1)
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(steamGames)
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(emptyList())
        coEvery { steamRepository.downloadGameImage(any(), any()) } returns Result.failure(
            RuntimeException("Network error")
        )
        coEvery { gameRepository.insertGame(any()) } returns 1L

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        coVerify { gameRepository.insertGame(any()) }
    }

    /**
     * 挿入されるゲームの属性が正しいかを確認するテスト
     */
    @Test
    fun `invoke inserts games with correct attributes`() = runTest {
        // Given
        val steamGames = listOf(mockSteamGame1)
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(steamGames)
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(emptyList())
        coEvery { steamRepository.downloadGameImage(any(), any()) } returns Result.success(Unit)

        val capturedGame = slot<Game>()
        coEvery { gameRepository.insertGame(capture(capturedGame)) } returns 1L

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isSuccess)

        val insertedGame = capturedGame.captured
        assertEquals("Portal 2", insertedGame.name)
        assertEquals(620L, insertedGame.steamAppId)
        assertEquals("", insertedGame.executablePath)
        assertEquals("", insertedGame.installPath)
        assertEquals(GameSource.STEAM, insertedGame.source)
        assertEquals(180L, insertedGame.playTimeMinutes)
        assertNull(insertedGame.winlatorContainerId)
        assertNull(insertedGame.lastPlayedTimestamp)
        assertFalse(insertedGame.isFavorite)
        assertTrue(insertedGame.iconPath?.contains("620_icon.jpg") == true)
        assertTrue(insertedGame.bannerPath?.contains("620_banner.jpg") == true)
    }

    /**
     * 大量のゲームが同期される場合のテスト
     */
    @Test
    fun `invoke handles large game libraries`() = runTest {
        // Given
        val largeGameList = (1..500).map { index ->
            SteamGame(
                appId = index.toLong(),
                name = "Game $index",
                playtimeMinutes = index.toLong() * 10,
                playtime2Weeks = null,
                imgIconUrl = "icon_$index",
                imgLogoUrl = "logo_$index"
            )
        }
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(largeGameList)
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(emptyList())
        coEvery { steamRepository.downloadGameImage(any(), any()) } returns Result.success(Unit)
        coEvery { gameRepository.insertGame(any()) } returns 1L

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(500, result.getOrNull())

        coVerify(exactly = 500) { gameRepository.insertGame(any()) }
        coVerify(exactly = 1000) { steamRepository.downloadGameImage(any(), any()) }
    }

    /**
     * ゲーム挿入中にエラーが発生した場合のテスト
     */
    @Test
    fun `invoke returns failure when game insertion fails`() = runTest {
        // Given
        val steamGames = listOf(mockSteamGame1)
        val errorMessage = "Database constraint violation"
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(steamGames)
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(emptyList())
        coEvery { steamRepository.downloadGameImage(any(), any()) } returns Result.success(Unit)
        coEvery { gameRepository.insertGame(any()) } throws RuntimeException(errorMessage)

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(errorMessage, result.exceptionOrNull()?.message)
    }

    /**
     * 画像URLが正しく生成されていることを確認するテスト
     */
    @Test
    fun `invoke downloads images from correct URLs`() = runTest {
        // Given
        val steamGames = listOf(mockSteamGame1)
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(steamGames)
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(emptyList())

        val capturedUrls = mutableListOf<String>()
        coEvery { steamRepository.downloadGameImage(capture(capturedUrls), any()) } returns Result.success(Unit)
        coEvery { gameRepository.insertGame(any()) } returns 1L

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        useCase(mockApiKey, mockSteamId)

        // Then
        assertEquals(2, capturedUrls.size)
        assertTrue(capturedUrls[0].contains("620")) // Icon URL
        assertTrue(capturedUrls[1].contains("620")) // Header URL
        assertTrue(capturedUrls[0].contains("icon_hash_620") || capturedUrls[0].contains("header.jpg"))
    }

    /**
     * 全ゲームが既に存在する場合、何も追加されないテスト
     */
    @Test
    fun `invoke returns 0 when all Steam games already exist locally`() = runTest {
        // Given
        val steamGames = listOf(mockSteamGame1, mockSteamGame2)
        val existingGames = listOf(
            mockExistingGame, // Portal 2 (620)
            mockExistingGame.copy(id = 2L, steamAppId = 220L, name = "Half-Life 2") // HL2 (220)
        )
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(steamGames)
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(existingGames)

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        val result = useCase(mockApiKey, mockSteamId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())

        coVerify(exactly = 0) { gameRepository.insertGame(any()) }
        coVerify(exactly = 0) { steamRepository.downloadGameImage(any(), any()) }
    }

    /**
     * SteamゲームにplaytimeMinutesが0の場合も正しく処理されるテスト
     */
    @Test
    fun `invoke handles games with zero playtime`() = runTest {
        // Given
        val unplayedGame = mockSteamGame1.copy(playtimeMinutes = 0L)
        coEvery { steamRepository.getOwnedGames(mockApiKey, mockSteamId) } returns Result.success(listOf(unplayedGame))
        coEvery { gameRepository.getGamesBySource(GameSource.STEAM) } returns flowOf(emptyList())
        coEvery { steamRepository.downloadGameImage(any(), any()) } returns Result.success(Unit)

        val capturedGame = slot<Game>()
        coEvery { gameRepository.insertGame(capture(capturedGame)) } returns 1L

        useCase = SyncSteamLibraryUseCase(context, steamRepository, gameRepository)

        // When
        useCase(mockApiKey, mockSteamId)

        // Then
        assertEquals(0L, capturedGame.captured.playTimeMinutes)
    }
}
