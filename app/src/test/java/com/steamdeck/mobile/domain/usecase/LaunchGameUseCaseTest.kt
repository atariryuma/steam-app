package com.steamdeck.mobile.domain.usecase

import android.content.Context
import com.steamdeck.mobile.core.error.AppError
import com.steamdeck.mobile.core.input.ControllerInputRouter
import com.steamdeck.mobile.core.result.DataResult
import com.steamdeck.mobile.core.winlator.LaunchResult
import com.steamdeck.mobile.core.winlator.WinlatorEngine
import com.steamdeck.mobile.domain.emulator.ProcessStatus
import com.steamdeck.mobile.domain.emulator.WindowsEmulator
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

/**
 * LaunchGameUseCaseの単体テスト
 *
 * テスト対象:
 * - ゲーム起動の成功フロー
 * - ゲームが見つからない場合のエラー
 * - Winlatorエンジンのエラーハンドリング
 * - プレイ時間記録の開始
 * - Pre-launch validation integration
 *
 * FIXED (2025-12-25): Aligned with actual LaunchGameUseCase implementation
 * - Removed WinlatorContainerRepository (YAGNI principle)
 * - Added WindowsEmulator, ValidateGameInstallationUseCase, ControllerInputRouter
 * - Uses DataResult<T> error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchGameUseCaseTest {

    private lateinit var useCase: LaunchGameUseCase
    private lateinit var context: Context
    private lateinit var gameRepository: GameRepository
    private lateinit var winlatorEngine: WinlatorEngine
    private lateinit var windowsEmulator: WindowsEmulator
    private lateinit var validateGameInstallationUseCase: ValidateGameInstallationUseCase
    private lateinit var controllerInputRouter: ControllerInputRouter

    // FIXED (2025-12-25): Container ID is String type (matches Container ID unification)
    private val mockGame = Game(
        id = 1L,
        name = "Portal 2",
        steamAppId = 620L,
        executablePath = "/games/portal2/portal2.exe",
        installPath = "/games/portal2",
        source = GameSource.STEAM,
        winlatorContainerId = "default_shared_container"  // String type
    )

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        winlatorEngine = mockk(relaxed = true)
        windowsEmulator = mockk(relaxed = true)
        validateGameInstallationUseCase = mockk(relaxed = true)
        controllerInputRouter = mockk(relaxed = true)

        useCase = LaunchGameUseCase(
            context,
            gameRepository,
            winlatorEngine,
            windowsEmulator,
            validateGameInstallationUseCase,
            controllerInputRouter
        )
    }

    /**
     * ゲーム起動が成功する場合のテスト
     */
    @Test
    fun `invoke launches game successfully and records play time`() = runTest {
        // Given
        val processId = 12345
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { validateGameInstallationUseCase(mockGame.id) } returns DataResult.Success(
            ValidationResult(isValid = true, errors = emptyList())
        )
        every { controllerInputRouter.startRouting(any()) } just Runs
        coEvery { winlatorEngine.launchGame(mockGame, null) } returns LaunchResult.Success(
            pid = processId,
            processId = "1766386680416_$processId"
        )
        every { windowsEmulator.monitorProcess(any()) } returns flowOf(
            ProcessStatus(isRunning = true, exitCode = null)
        )
        coEvery { gameRepository.updatePlayTime(any(), any(), any()) } just Runs

        // When
        val result = useCase(mockGame.id)

        // Then
        assertTrue(result is DataResult.Success)
        val launchInfo = (result as DataResult.Success).data
        assertEquals(processId, launchInfo.processId)

        coVerify { gameRepository.getGameById(mockGame.id) }
        coVerify { validateGameInstallationUseCase(mockGame.id) }
        verify { controllerInputRouter.startRouting(any()) }
        coVerify { winlatorEngine.launchGame(mockGame, null) }
        coVerify { gameRepository.updatePlayTime(mockGame.id, 0, any()) }
    }

    /**
     * ゲームが見つからない場合のエラーテスト
     */
    @Test
    fun `invoke returns error when game does not exist`() = runTest {
        // Given
        val nonExistentGameId = 999L
        coEvery { gameRepository.getGameById(nonExistentGameId) } returns null

        // When
        val result = useCase(nonExistentGameId)

        // Then
        assertTrue(result is DataResult.Error)
        val error = (result as DataResult.Error).error
        assertTrue(error is AppError.DatabaseError)

        coVerify(exactly = 0) { winlatorEngine.launchGame(any(), any()) }
        coVerify(exactly = 0) { gameRepository.updatePlayTime(any(), any(), any()) }
    }

    /**
     * Pre-launch validation失敗時のエラーテスト
     */
    @Test
    fun `invoke returns error when validation fails`() = runTest {
        // Given
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { validateGameInstallationUseCase(mockGame.id) } returns DataResult.Success(
            ValidationResult(isValid = false, errors = listOf("Game executable not found"))
        )

        // When
        val result = useCase(mockGame.id)

        // Then
        assertTrue(result is DataResult.Error)
        val error = (result as DataResult.Error).error
        assertTrue(error is AppError.FileError)

        coVerify(exactly = 0) { winlatorEngine.launchGame(any(), any()) }
    }

    /**
     * Winlatorエンジンの起動がエラーになる場合のテスト
     */
    @Test
    fun `invoke returns error when winlator engine fails to launch`() = runTest {
        // Given
        val errorMessage = "Winlator initialization failed"
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { validateGameInstallationUseCase(mockGame.id) } returns DataResult.Success(
            ValidationResult(isValid = true, errors = emptyList())
        )
        every { controllerInputRouter.startRouting(any()) } just Runs
        coEvery { winlatorEngine.launchGame(mockGame, null) } returns LaunchResult.Error(errorMessage)

        // When
        val result = useCase(mockGame.id)

        // Then
        assertTrue(result is DataResult.Error)
        val error = (result as DataResult.Error).error
        assertTrue(error is AppError.Unknown)

        // Should stop controller routing on error
        verify { controllerInputRouter.stopRouting() }
        coVerify(exactly = 0) { gameRepository.updatePlayTime(any(), any(), any()) }
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
        coEvery { validateGameInstallationUseCase(importedGame.id) } returns DataResult.Success(
            ValidationResult(isValid = true, errors = emptyList())
        )
        every { controllerInputRouter.startRouting(any()) } just Runs
        coEvery { winlatorEngine.launchGame(importedGame, null) } returns LaunchResult.Success(
            pid = processId,
            processId = "1766386680416_$processId"
        )
        every { windowsEmulator.monitorProcess(any()) } returns flowOf(
            ProcessStatus(isRunning = true, exitCode = null)
        )

        // When
        val result = useCase(importedGame.id)

        // Then
        assertTrue(result is DataResult.Success)
        val launchInfo = (result as DataResult.Success).data
        assertEquals(processId, launchInfo.processId)
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
        coEvery { validateGameInstallationUseCase(mockGame.id) } returns DataResult.Success(
            ValidationResult(isValid = true, errors = emptyList())
        )
        every { controllerInputRouter.startRouting(any()) } just Runs
        coEvery { winlatorEngine.launchGame(mockGame, null) } returns LaunchResult.Success(
            pid = processId,
            processId = "1766386680416_$processId"
        )
        every { windowsEmulator.monitorProcess(any()) } returns flowOf(
            ProcessStatus(isRunning = true, exitCode = null)
        )

        val capturedTimestamp = slot<Long>()
        coEvery { gameRepository.updatePlayTime(mockGame.id, 0, capture(capturedTimestamp)) } just Runs

        // When
        useCase(mockGame.id)
        val afterTimestamp = System.currentTimeMillis()

        // Then
        assertTrue(capturedTimestamp.captured >= beforeTimestamp)
        assertTrue(capturedTimestamp.captured <= afterTimestamp)
    }
}

// Test helper for ValidationResult
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
) {
    fun getUserMessage(): String? = errors.firstOrNull()
}
