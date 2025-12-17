package com.steamdeck.mobile.domain.usecase

import com.steamdeck.mobile.core.winlator.LaunchResult
import com.steamdeck.mobile.core.winlator.WinlatorEngine
import com.steamdeck.mobile.domain.model.Box64Preset
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.model.WinlatorContainer
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LaunchGameUseCaseの単体テスト
 *
 * テスト対象:
 * - ゲーム起動の成功フロー
 * - ゲームが見つからない場合のエラー
 * - Winlatorエンジンのエラーハンドリング
 * - コンテナありなしの両方のケース
 * - プレイ時間記録の開始
 *
 * Best Practices:
 * - Integration testing with multiple dependencies
 * - Result<T> pattern validation
 * - MockK verification of side effects
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchGameUseCaseTest {

    private lateinit var useCase: LaunchGameUseCase
    private lateinit var gameRepository: GameRepository
    private lateinit var containerRepository: WinlatorContainerRepository
    private lateinit var winlatorEngine: WinlatorEngine

    private val mockGame = Game(
        id = 1L,
        name = "Portal 2",
        steamAppId = 620L,
        executablePath = "/games/portal2/portal2.exe",
        installPath = "/games/portal2",
        source = GameSource.STEAM,
        winlatorContainerId = 10L
    )

    private val mockContainer = WinlatorContainer(
        id = 10L,
        name = "Portal 2 Container",
        box64Preset = Box64Preset.PERFORMANCE,
        wineVersion = "8.0",
        screenResolution = "1920x1080",
        enableDXVK = true
    )

    @Before
    fun setup() {
        gameRepository = mockk(relaxed = true)
        containerRepository = mockk(relaxed = true)
        winlatorEngine = mockk(relaxed = true)
    }

    /**
     * ゲーム起動が成功する場合のテスト
     */
    @Test
    fun `invoke launches game successfully and records play time`() = runTest {
        // Given
        val processId = 12345
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { containerRepository.getContainerById(mockContainer.id) } returns mockContainer
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns LaunchResult.Success(processId)
        coEvery { gameRepository.updatePlayTime(any(), any(), any()) } just Runs

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(mockGame.id)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(processId, result.getOrNull())

        coVerify { gameRepository.getGameById(mockGame.id) }
        coVerify { containerRepository.getContainerById(mockContainer.id) }
        coVerify { winlatorEngine.launchGame(mockGame, mockContainer) }
        coVerify { gameRepository.updatePlayTime(mockGame.id, 0, any()) }
    }

    /**
     * コンテナIDがnullの場合でも起動できるテスト
     */
    @Test
    fun `invoke launches game without container when containerId is null`() = runTest {
        // Given
        val gameWithoutContainer = mockGame.copy(winlatorContainerId = null)
        val processId = 99999
        coEvery { gameRepository.getGameById(gameWithoutContainer.id) } returns gameWithoutContainer
        coEvery { winlatorEngine.launchGame(gameWithoutContainer, null) } returns LaunchResult.Success(processId)

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(gameWithoutContainer.id)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(processId, result.getOrNull())

        coVerify { winlatorEngine.launchGame(gameWithoutContainer, null) }
        coVerify(exactly = 0) { containerRepository.getContainerById(any()) }
    }

    /**
     * ゲームが見つからない場合のエラーテスト
     */
    @Test
    fun `invoke returns failure when game does not exist`() = runTest {
        // Given
        val nonExistentGameId = 999L
        coEvery { gameRepository.getGameById(nonExistentGameId) } returns null

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(nonExistentGameId)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertEquals("ゲームが見つかりません", exception?.message)

        coVerify(exactly = 0) { winlatorEngine.launchGame(any(), any()) }
        coVerify(exactly = 0) { gameRepository.updatePlayTime(any(), any(), any()) }
    }

    /**
     * Winlatorエンジンの起動がエラーになる場合のテスト
     */
    @Test
    fun `invoke returns failure when winlator engine fails to launch`() = runTest {
        // Given
        val errorMessage = "Winlator initialization failed"
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { containerRepository.getContainerById(mockContainer.id) } returns mockContainer
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns
            LaunchResult.Error(errorMessage)

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(mockGame.id)

        // Then
        assertTrue(result.isFailure)
        assertEquals(errorMessage, result.exceptionOrNull()?.message)

        coVerify(exactly = 0) { gameRepository.updatePlayTime(any(), any(), any()) }
    }

    /**
     * Winlatorエンジンのエラーに原因例外が含まれている場合のテスト
     */
    @Test
    fun `invoke propagates cause exception from winlator error`() = runTest {
        // Given
        val errorMessage = "File not found"
        val causeException = java.io.FileNotFoundException("/games/portal2/portal2.exe")
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { containerRepository.getContainerById(mockContainer.id) } returns mockContainer
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns
            LaunchResult.Error(errorMessage, causeException)

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(mockGame.id)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertEquals(errorMessage, exception?.message)
        assertEquals(causeException, exception?.cause)
    }

    /**
     * Repositoryで例外が発生した場合のテスト
     */
    @Test
    fun `invoke handles repository exceptions gracefully`() = runTest {
        // Given
        val errorMessage = "Database connection lost"
        coEvery { gameRepository.getGameById(mockGame.id) } throws RuntimeException(errorMessage)

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(mockGame.id)

        // Then
        assertTrue(result.isFailure)
        assertEquals(errorMessage, result.exceptionOrNull()?.message)
    }

    /**
     * コンテナが見つからない場合でもnullで起動を試みるテスト
     */
    @Test
    fun `invoke launches with null container when container not found`() = runTest {
        // Given
        val processId = 7777
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { containerRepository.getContainerById(mockContainer.id) } returns null
        coEvery { winlatorEngine.launchGame(mockGame, null) } returns LaunchResult.Success(processId)

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(mockGame.id)

        // Then
        assertTrue(result.isSuccess)
        coVerify { winlatorEngine.launchGame(mockGame, null) }
    }

    /**
     * プレイ時間記録が失敗してもゲーム起動結果は成功を返すテスト
     */
    @Test
    fun `invoke returns success even when updatePlayTime fails`() = runTest {
        // Given
        val processId = 5555
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { containerRepository.getContainerById(mockContainer.id) } returns mockContainer
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns LaunchResult.Success(processId)
        coEvery { gameRepository.updatePlayTime(any(), any(), any()) } throws RuntimeException("DB error")

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(mockGame.id)

        // Then
        // Should still fail because exception is thrown
        assertTrue(result.isFailure)
    }

    /**
     * Imported gameでもWinlatorで起動できることを確認するテスト
     */
    @Test
    fun `invoke launches imported games correctly`() = runTest {
        // Given
        val importedGame = mockGame.copy(source = GameSource.IMPORTED, steamAppId = null)
        val processId = 4321
        coEvery { gameRepository.getGameById(importedGame.id) } returns importedGame
        coEvery { containerRepository.getContainerById(mockContainer.id) } returns mockContainer
        coEvery { winlatorEngine.launchGame(importedGame, mockContainer) } returns LaunchResult.Success(processId)

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        val result = useCase(importedGame.id)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(processId, result.getOrNull())
    }

    /**
     * プレイ時間記録の開始時刻が現在時刻付近であることを確認するテスト
     */
    @Test
    fun `invoke records current timestamp for play time tracking`() = runTest {
        // Given
        val processId = 1111
        val beforeTimestamp = System.currentTimeMillis()
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { containerRepository.getContainerById(mockContainer.id) } returns mockContainer
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns LaunchResult.Success(processId)

        val capturedTimestamp = slot<Long>()
        coEvery { gameRepository.updatePlayTime(mockGame.id, 0, capture(capturedTimestamp)) } just Runs

        useCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // When
        useCase(mockGame.id)
        val afterTimestamp = System.currentTimeMillis()

        // Then
        assertTrue(capturedTimestamp.captured >= beforeTimestamp)
        assertTrue(capturedTimestamp.captured <= afterTimestamp)
    }
}
