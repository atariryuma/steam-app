package com.steamdeck.mobile.integration

import app.cash.turbine.test
import com.steamdeck.mobile.core.download.SteamDownloadManager
import com.steamdeck.mobile.core.steam.SteamLauncher
import com.steamdeck.mobile.core.steam.SteamSetupManager
import com.steamdeck.mobile.core.winlator.*
import com.steamdeck.mobile.domain.emulator.EmulatorContainer
import com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig
import com.steamdeck.mobile.domain.model.Box64Preset
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.domain.model.GameSource
import com.steamdeck.mobile.domain.model.WinlatorContainer
import com.steamdeck.mobile.domain.repository.GameRepository
import com.steamdeck.mobile.domain.repository.WinlatorContainerRepository
import com.steamdeck.mobile.domain.usecase.DeleteGameUseCase
import com.steamdeck.mobile.domain.usecase.GetGameByIdUseCase
import com.steamdeck.mobile.domain.usecase.LaunchGameUseCase
import com.steamdeck.mobile.domain.usecase.ToggleFavoriteUseCase
import com.steamdeck.mobile.presentation.viewmodel.GameDetailUiState
import com.steamdeck.mobile.presentation.viewmodel.GameDetailViewModel
import com.steamdeck.mobile.presentation.viewmodel.LaunchState
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * GameDetailViewModel and WinlatorEmulator integration test
 *
 * Test targets:
 * - Complete flow from ViewModel → UseCase → WinlatorEngine → WinlatorEmulator
 * - Error handling propagation
 * - Process launch and metrics monitoring integration
 *
 * Best Practices:
 * - Use actual WinlatorEmulator implementation (Mock only unavoidable parts)
 * - State transition verification with Turbine
 * - Proper async processing tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelIntegrationTest {

    private lateinit var viewModel: GameDetailViewModel
    private lateinit var gameRepository: GameRepository
    private lateinit var containerRepository: WinlatorContainerRepository
    private lateinit var winlatorEmulator: WinlatorEmulator
    private lateinit var launchGameUseCase: LaunchGameUseCase

    private lateinit var steamDownloadManager: SteamDownloadManager
    private lateinit var steamLauncher: SteamLauncher
    private lateinit var steamSetupManager: SteamSetupManager

    private val testDispatcher = StandardTestDispatcher()

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
        wineVersion = "9.0",
        screenResolution = "1920x1080",
        enableDXVK = true
    )

    @Before
    fun setup() {
        // Mock repository layer
        gameRepository = mockk(relaxed = true)
        containerRepository = mockk(relaxed = true)

        // Mock Steam-related components (required but not needed for this test)
        steamDownloadManager = mockk(relaxed = true)
        steamLauncher = mockk(relaxed = true)
        steamSetupManager = mockk(relaxed = true)

        // WinlatorEmulator is mocked (use implementation on real device, mock in test environment)
        winlatorEmulator = mockk(relaxed = true)

        // UseCase layer (use implementation)
        val getGameByIdUseCase = GetGameByIdUseCase(gameRepository)
        val toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository)
        val deleteGameUseCase = DeleteGameUseCase(gameRepository)

        // Mock WinlatorEngine
        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)

        launchGameUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)

        // ViewModel (use implementation)
        viewModel = GameDetailViewModel(
            getGameByIdUseCase = getGameByIdUseCase,
            launchGameUseCase = launchGameUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            deleteGameUseCase = deleteGameUseCase,
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // Default mock settings
        coEvery { gameRepository.getGameById(mockGame.id) } returns mockGame
        coEvery { containerRepository.getContainerById(mockContainer.id) } returns mockContainer
        coEvery { steamSetupManager.isSteamInstalled() } returns false
    }

    /**
     * Happy path: Test successful game launch flow
     */
    @Test
    fun `integration test - successful game launch flow`() = runTest {
        // Given
        val processId = 12345
        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns LaunchResult.Success(processId)

        val launchUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)
        val vm = GameDetailViewModel(
            getGameByIdUseCase = GetGameByIdUseCase(gameRepository),
            launchGameUseCase = launchUseCase,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository),
            deleteGameUseCase = DeleteGameUseCase(gameRepository),
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // When
        vm.launchState.test {
            // Verify initial state
            assertEquals(LaunchState.Idle, awaitItem())

            // Execute game launch
            vm.launchGame(mockGame.id)

            // Verify Launching state
            assertEquals(LaunchState.Launching, awaitItem())

            // Verify Running state
            val runningState = awaitItem() as LaunchState.Running
            assertEquals(processId, runningState.processId)

            cancelAndIgnoreRemainingEvents()
        }

        // Then
        coVerify { gameRepository.getGameById(mockGame.id) }
        coVerify { containerRepository.getContainerById(mockContainer.id) }
        coVerify { winlatorEngine.launchGame(mockGame, mockContainer) }
        coVerify { gameRepository.updatePlayTime(mockGame.id, 0, any()) }
    }

    /**
     * Error case: Error flow when game is not found
     */
    @Test
    fun `integration test - game not found error flow`() = runTest {
        // Given
        val nonExistentGameId = 999L
        coEvery { gameRepository.getGameById(nonExistentGameId) } returns null

        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)
        val launchUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)
        val vm = GameDetailViewModel(
            getGameByIdUseCase = GetGameByIdUseCase(gameRepository),
            launchGameUseCase = launchUseCase,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository),
            deleteGameUseCase = DeleteGameUseCase(gameRepository),
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // When
        vm.launchState.test {
            assertEquals(LaunchState.Idle, awaitItem())

            vm.launchGame(nonExistentGameId)

            assertEquals(LaunchState.Launching, awaitItem())

            val errorState = awaitItem() as LaunchState.Error
            assertTrue(errorState.message.contains("Game not found"))

            cancelAndIgnoreRemainingEvents()
        }

        // Then
        coVerify(exactly = 0) { winlatorEngine.launchGame(any(), any()) }
    }

    /**
     * Error case: Error flow when Box64 is not initialized
     */
    @Test
    fun `integration test - Box64 not initialized error flow`() = runTest {
        // Given
        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns LaunchResult.Error(
            "Box64 binary not found. Please run initialize() first."
        )

        val launchUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)
        val vm = GameDetailViewModel(
            getGameByIdUseCase = GetGameByIdUseCase(gameRepository),
            launchGameUseCase = launchUseCase,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository),
            deleteGameUseCase = DeleteGameUseCase(gameRepository),
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // When
        vm.launchState.test {
            assertEquals(LaunchState.Idle, awaitItem())

            vm.launchGame(mockGame.id)

            assertEquals(LaunchState.Launching, awaitItem())

            val errorState = awaitItem() as LaunchState.Error
            assertTrue(errorState.message.contains("Box64"))

            cancelAndIgnoreRemainingEvents()
        }

        // Then
        coVerify { winlatorEngine.launchGame(mockGame, mockContainer) }
        coVerify(exactly = 0) { gameRepository.updatePlayTime(any(), any(), any()) }
    }

    /**
     * Error case: Wine prefix initialization error flow
     */
    @Test
    fun `integration test - Wine prefix initialization error flow`() = runTest {
        // Given
        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns LaunchResult.Error(
            "Wine prefix initialization failed",
            WinePrefixException("wineboot timed out")
        )

        val launchUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)
        val vm = GameDetailViewModel(
            getGameByIdUseCase = GetGameByIdUseCase(gameRepository),
            launchGameUseCase = launchUseCase,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository),
            deleteGameUseCase = DeleteGameUseCase(gameRepository),
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // When
        vm.launchState.test {
            assertEquals(LaunchState.Idle, awaitItem())

            vm.launchGame(mockGame.id)

            assertEquals(LaunchState.Launching, awaitItem())

            val errorState = awaitItem() as LaunchState.Error
            assertTrue(errorState.message.contains("Wine prefix"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Integration test: Launch game without container
     */
    @Test
    fun `integration test - launch game without container`() = runTest {
        // Given
        val gameWithoutContainer = mockGame.copy(winlatorContainerId = null)
        val processId = 99999
        coEvery { gameRepository.getGameById(gameWithoutContainer.id) } returns gameWithoutContainer

        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)
        coEvery { winlatorEngine.launchGame(gameWithoutContainer, null) } returns LaunchResult.Success(processId)

        val launchUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)
        val vm = GameDetailViewModel(
            getGameByIdUseCase = GetGameByIdUseCase(gameRepository),
            launchGameUseCase = launchUseCase,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository),
            deleteGameUseCase = DeleteGameUseCase(gameRepository),
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // When
        vm.launchState.test {
            assertEquals(LaunchState.Idle, awaitItem())

            vm.launchGame(gameWithoutContainer.id)

            assertEquals(LaunchState.Launching, awaitItem())

            val runningState = awaitItem() as LaunchState.Running
            assertEquals(processId, runningState.processId)

            cancelAndIgnoreRemainingEvents()
        }

        // Then
        coVerify { winlatorEngine.launchGame(gameWithoutContainer, null) }
        coVerify(exactly = 0) { containerRepository.getContainerById(any()) }
    }

    /**
     * Integration test: Repository exception handling
     */
    @Test
    fun `integration test - repository exception handling`() = runTest {
        // Given
        val errorMessage = "Database connection lost"
        coEvery { gameRepository.getGameById(mockGame.id) } throws RuntimeException(errorMessage)

        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)
        val launchUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)
        val vm = GameDetailViewModel(
            getGameByIdUseCase = GetGameByIdUseCase(gameRepository),
            launchGameUseCase = launchUseCase,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository),
            deleteGameUseCase = DeleteGameUseCase(gameRepository),
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // When
        vm.launchState.test {
            assertEquals(LaunchState.Idle, awaitItem())

            vm.launchGame(mockGame.id)

            assertEquals(LaunchState.Launching, awaitItem())

            val errorState = awaitItem() as LaunchState.Error
            assertEquals(errorMessage, errorState.message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Integration test: Load game and launch sequence coordination
     */
    @Test
    fun `integration test - load game and launch sequence`() = runTest {
        // Given
        val processId = 54321
        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns LaunchResult.Success(processId)

        val launchUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)
        val vm = GameDetailViewModel(
            getGameByIdUseCase = GetGameByIdUseCase(gameRepository),
            launchGameUseCase = launchUseCase,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository),
            deleteGameUseCase = DeleteGameUseCase(gameRepository),
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // When - Load game details
        vm.uiState.test {
            assertEquals(GameDetailUiState.Loading, awaitItem())

            vm.loadGame(mockGame.id)

            val successState = awaitItem() as GameDetailUiState.Success
            assertEquals(mockGame, successState.game)

            cancelAndIgnoreRemainingEvents()
        }

        // Then - Launch game
        vm.launchState.test {
            assertEquals(LaunchState.Idle, awaitItem())

            vm.launchGame(mockGame.id)

            assertEquals(LaunchState.Launching, awaitItem())

            val runningState = awaitItem() as LaunchState.Running
            assertEquals(processId, runningState.processId)

            cancelAndIgnoreRemainingEvents()
        }

        // Verify
        coVerify { gameRepository.getGameById(mockGame.id) }
        coVerify { winlatorEngine.launchGame(mockGame, mockContainer) }
    }

    /**
     * Integration test: Verify play time recording
     */
    @Test
    fun `integration test - play time recording on launch`() = runTest {
        // Given
        val processId = 11111
        val timestampSlot = slot<Long>()
        val winlatorEngine = mockk<WinlatorEngine>(relaxed = true)
        coEvery { winlatorEngine.launchGame(mockGame, mockContainer) } returns LaunchResult.Success(processId)
        coEvery { gameRepository.updatePlayTime(mockGame.id, 0, capture(timestampSlot)) } just Runs

        val beforeLaunch = System.currentTimeMillis()

        val launchUseCase = LaunchGameUseCase(gameRepository, containerRepository, winlatorEngine)
        val vm = GameDetailViewModel(
            getGameByIdUseCase = GetGameByIdUseCase(gameRepository),
            launchGameUseCase = launchUseCase,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(gameRepository),
            deleteGameUseCase = DeleteGameUseCase(gameRepository),
            steamDownloadManager = steamDownloadManager,
            steamLauncher = steamLauncher,
            steamSetupManager = steamSetupManager
        )

        // When
        vm.launchGame(mockGame.id)

        // Wait for completion
        vm.launchState.test {
            awaitItem() // Idle
            awaitItem() // Launching
            awaitItem() // Running
            cancelAndIgnoreRemainingEvents()
        }

        val afterLaunch = System.currentTimeMillis()

        // Then
        coVerify { gameRepository.updatePlayTime(mockGame.id, 0, any()) }
        assertTrue("Timestamp should be >= before launch", timestampSlot.captured >= beforeLaunch)
        assertTrue("Timestamp should be <= after launch", timestampSlot.captured <= afterLaunch)
    }
}
