package com.steamdeck.mobile.presentation.viewmodel

import app.cash.turbine.test
import com.steamdeck.mobile.data.local.preferences.SteamPreferences
import com.steamdeck.mobile.data.remote.steam.SteamRepository
import com.steamdeck.mobile.data.remote.steam.model.SteamPlayer
import com.steamdeck.mobile.domain.usecase.SyncSteamLibraryUseCase
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
 * SettingsViewModelの単体テスト
 *
 * テスト対象:
 * - 設定データの読み込みとFlow監視
 * - Steam認証情報の保存と検証
 * - Steamライブラリの同期処理とSyncState遷移
 * - 設定のクリア処理
 * - エラーハンドリングとメッセージクリア
 *
 * Best Practices:
 * - Flow combine testing with multiple data sources
 * - DataStore preferences mocking
 * - Complex state machine testing (SyncState)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var steamPreferences: SteamPreferences
    private lateinit var steamRepository: SteamRepository
    private lateinit var syncSteamLibraryUseCase: SyncSteamLibraryUseCase
    private lateinit var testDispatcher: TestDispatcher

    // Mock data
    private val mockApiKey = "ABC123DEF456"
    private val mockSteamId = "76561198012345678"
    private val mockUsername = "TestUser"
    private val mockLastSync = System.currentTimeMillis() - 3600_000 // 1 hour ago

    private val mockPlayer = SteamPlayer(
        steamId = mockSteamId,
        personaName = mockUsername,
        profileUrl = "https://steamcommunity.com/id/testuser",
        avatar = "avatar_url",
        avatarMedium = "avatar_medium_url",
        avatarFull = "avatar_full_url",
        timeCreated = System.currentTimeMillis() - 86400_000 * 365 // 1 year ago
    )

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        steamPreferences = mockk(relaxed = true)
        steamRepository = mockk(relaxed = true)
        syncSteamLibraryUseCase = mockk(relaxed = true)

        // Default mock behavior for preferences
        coEvery { steamPreferences.getSteamApiKey() } returns flowOf(mockApiKey)
        coEvery { steamPreferences.getSteamId() } returns flowOf(mockSteamId)
        coEvery { steamPreferences.getSteamUsername() } returns flowOf(mockUsername)
        coEvery { steamPreferences.getLastSyncTimestamp() } returns flowOf(mockLastSync)
        coEvery { steamPreferences.isSteamConfigured() } returns flowOf(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 設定データが正しく読み込まれるテスト
     */
    @Test
    fun `loadSettings emits Success with correct data`() = runTest {
        // Given
        coEvery { steamPreferences.getSteamApiKey() } returns flowOf(mockApiKey)
        coEvery { steamPreferences.getSteamId() } returns flowOf(mockSteamId)
        coEvery { steamPreferences.getSteamUsername() } returns flowOf(mockUsername)
        coEvery { steamPreferences.getLastSyncTimestamp() } returns flowOf(mockLastSync)
        coEvery { steamPreferences.isSteamConfigured() } returns flowOf(true)

        // When
        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is SettingsUiState.Success)

            val successState = state as SettingsUiState.Success
            assertEquals(mockApiKey, successState.data.steamApiKey)
            assertEquals(mockSteamId, successState.data.steamId)
            assertEquals(mockUsername, successState.data.steamUsername)
            assertEquals(mockLastSync, successState.data.lastSyncTimestamp)
            assertTrue(successState.data.isSteamConfigured)
        }
    }

    /**
     * Steam未設定の場合、空のデータが返されるテスト
     */
    @Test
    fun `loadSettings emits empty data when Steam not configured`() = runTest {
        // Given
        coEvery { steamPreferences.getSteamApiKey() } returns flowOf(null)
        coEvery { steamPreferences.getSteamId() } returns flowOf(null)
        coEvery { steamPreferences.getSteamUsername() } returns flowOf(null)
        coEvery { steamPreferences.getLastSyncTimestamp() } returns flowOf(null)
        coEvery { steamPreferences.isSteamConfigured() } returns flowOf(false)

        // When
        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem() as SettingsUiState.Success
            assertEquals("", state.data.steamApiKey)
            assertEquals("", state.data.steamId)
            assertEquals("", state.data.steamUsername)
            assertNull(state.data.lastSyncTimestamp)
            assertFalse(state.data.isSteamConfigured)
        }
    }

    /**
     * Steam認証情報の保存が成功するテスト
     */
    @Test
    fun `saveSteamCredentials saves credentials and fetches player info on success`() = runTest {
        // Given
        val apiKey = "NEW_API_KEY"
        val steamId = "76561198000000001"
        coEvery { steamRepository.getPlayerSummary(apiKey, steamId) } returns Result.success(mockPlayer)
        coEvery { steamPreferences.setSteamApiKey(apiKey) } returns Unit
        coEvery { steamPreferences.setSteamId(steamId) } returns Unit
        coEvery { steamPreferences.setSteamUsername(mockPlayer.personaName) } returns Unit

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.saveSteamCredentials(apiKey, steamId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { steamRepository.getPlayerSummary(apiKey, steamId) }
        coVerify { steamPreferences.setSteamApiKey(apiKey) }
        coVerify { steamPreferences.setSteamId(steamId) }
        coVerify { steamPreferences.setSteamUsername(mockPlayer.personaName) }

        viewModel.uiState.test {
            val state = awaitItem() as SettingsUiState.Success
            assertEquals("Steam認証が完了しました", state.successMessage)
        }
    }

    /**
     * Steam認証で空の値を送信するとエラーになるテスト
     */
    @Test
    fun `saveSteamCredentials emits Error when apiKey or steamId is blank`() = runTest {
        // Given
        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When - blank apiKey
        viewModel.saveSteamCredentials("", mockSteamId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val errorState = awaitItem() as SettingsUiState.Error
            assertEquals("API KeyとSteam IDは必須です", errorState.message)
        }

        // When - blank steamId
        viewModel.saveSteamCredentials(mockApiKey, "")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val errorState = awaitItem() as SettingsUiState.Error
            assertEquals("API KeyとSteam IDは必須です", errorState.message)
        }
    }

    /**
     * Steam API呼び出しが失敗した場合のエラーハンドリングテスト
     */
    @Test
    fun `saveSteamCredentials emits Error when API call fails`() = runTest {
        // Given
        val apiKey = "INVALID_KEY"
        val steamId = "INVALID_ID"
        val errorMessage = "Invalid API key"
        coEvery { steamRepository.getPlayerSummary(apiKey, steamId) } returns Result.failure(
            RuntimeException(errorMessage)
        )

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.saveSteamCredentials(apiKey, steamId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val errorState = awaitItem() as SettingsUiState.Error
            assertTrue(errorState.message.contains("認証に失敗しました"))
        }

        coVerify(exactly = 0) { steamPreferences.setSteamApiKey(any()) }
    }

    /**
     * プレイヤー情報がnullの場合のエラーハンドリングテスト
     */
    @Test
    fun `saveSteamCredentials emits Error when player data is null`() = runTest {
        // Given
        val apiKey = "VALID_KEY"
        val steamId = "VALID_ID"
        coEvery { steamRepository.getPlayerSummary(apiKey, steamId) } returns Result.success(null)

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.saveSteamCredentials(apiKey, steamId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val errorState = awaitItem() as SettingsUiState.Error
            assertEquals("プレイヤー情報が取得できませんでした", errorState.message)
        }
    }

    /**
     * Steamライブラリ同期が成功するテスト
     */
    @Test
    fun `syncSteamLibrary transitions from Idle to Syncing to Success`() = runTest {
        // Given
        val syncedCount = 25
        coEvery { syncSteamLibraryUseCase(mockApiKey, mockSteamId) } returns Result.success(syncedCount)
        coEvery { steamPreferences.setLastSyncTimestamp(any()) } returns Unit

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.syncSteamLibrary()

        // Then
        viewModel.syncState.test {
            assertEquals(SyncState.Idle, awaitItem())

            testDispatcher.scheduler.advanceUntilIdle()

            val syncingState = awaitItem() as SyncState.Syncing
            assertEquals(0f, syncingState.progress, 0.01f)
            assertEquals("同期を開始しています...", syncingState.message)

            val successState = awaitItem() as SyncState.Success
            assertEquals(syncedCount, successState.syncedGamesCount)
        }

        coVerify { syncSteamLibraryUseCase(mockApiKey, mockSteamId) }
        coVerify { steamPreferences.setLastSyncTimestamp(any()) }
    }

    /**
     * Steamライブラリ同期が失敗するテスト
     */
    @Test
    fun `syncSteamLibrary transitions to Error on failure`() = runTest {
        // Given
        val errorMessage = "Network timeout"
        coEvery { syncSteamLibraryUseCase(mockApiKey, mockSteamId) } returns Result.failure(
            RuntimeException(errorMessage)
        )

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.syncSteamLibrary()

        // Then
        viewModel.syncState.test {
            assertEquals(SyncState.Idle, awaitItem())

            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is SyncState.Syncing)

            val errorState = awaitItem() as SyncState.Error
            assertEquals(errorMessage, errorState.message)
        }
    }

    /**
     * Steam未設定時に同期を実行するとエラーになるテスト
     */
    @Test
    fun `syncSteamLibrary emits Error when Steam not configured`() = runTest {
        // Given
        coEvery { steamPreferences.isSteamConfigured() } returns flowOf(false)
        coEvery { steamPreferences.getSteamApiKey() } returns flowOf(null)

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.syncSteamLibrary()

        // Then
        viewModel.syncState.test {
            testDispatcher.scheduler.advanceUntilIdle()

            val errorState = awaitItem() as SyncState.Error
            assertEquals("Steam認証情報が設定されていません", errorState.message)
        }

        coVerify(exactly = 0) { syncSteamLibraryUseCase(any(), any()) }
    }

    /**
     * Steam設定のクリアが正常に動作するテスト
     */
    @Test
    fun `clearSteamSettings clears preferences and updates UI`() = runTest {
        // Given
        coEvery { steamPreferences.clearSteamSettings() } returns Unit

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearSteamSettings()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { steamPreferences.clearSteamSettings() }

        viewModel.uiState.test {
            val state = awaitItem() as SettingsUiState.Success
            assertEquals("Steam設定をクリアしました", state.successMessage)
        }
    }

    /**
     * エラーメッセージのクリアが正常に動作するテスト
     */
    @Test
    fun `clearError transitions from Error to Success`() = runTest {
        // Given
        coEvery { steamRepository.getPlayerSummary(any(), any()) } returns Result.failure(
            RuntimeException("Test error")
        )

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // Trigger error
        viewModel.saveSteamCredentials("invalid", "invalid")
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearError()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is SettingsUiState.Success)
        }
    }

    /**
     * 成功メッセージのクリアが正常に動作するテスト
     */
    @Test
    fun `clearSuccessMessage removes success message from Success state`() = runTest {
        // Given
        coEvery { steamRepository.getPlayerSummary(any(), any()) } returns Result.success(mockPlayer)

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveSteamCredentials(mockApiKey, mockSteamId)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearSuccessMessage()

        // Then
        viewModel.uiState.test {
            val state = awaitItem() as SettingsUiState.Success
            assertNull(state.successMessage)
        }
    }

    /**
     * 同期状態のリセットが正常に動作するテスト
     */
    @Test
    fun `resetSyncState returns to Idle`() = runTest {
        // Given
        coEvery { syncSteamLibraryUseCase(any(), any()) } returns Result.success(10)

        viewModel = SettingsViewModel(steamPreferences, steamRepository, syncSteamLibraryUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.syncSteamLibrary()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.resetSyncState()

        // Then
        viewModel.syncState.test {
            val state = awaitItem()
            assertTrue(state is SyncState.Idle)
        }
    }

    /**
     * SettingsDataのlastSyncFormattedが正しく動作するテスト
     */
    @Test
    fun `SettingsData formats lastSyncTimestamp correctly`() {
        // Test "未同期"
        val noSyncData = SettingsData(
            steamApiKey = "",
            steamId = "",
            steamUsername = "",
            lastSyncTimestamp = null,
            isSteamConfigured = false
        )
        assertEquals("未同期", noSyncData.lastSyncFormatted)

        // Test "1分以内"
        val recentSync = SettingsData(
            steamApiKey = "",
            steamId = "",
            steamUsername = "",
            lastSyncTimestamp = System.currentTimeMillis() - 30_000, // 30 seconds ago
            isSteamConfigured = false
        )
        assertEquals("1分以内", recentSync.lastSyncFormatted)

        // Test minutes
        val minutesAgo = SettingsData(
            steamApiKey = "",
            steamId = "",
            steamUsername = "",
            lastSyncTimestamp = System.currentTimeMillis() - 120_000, // 2 minutes
            isSteamConfigured = false
        )
        assertEquals("2分前", minutesAgo.lastSyncFormatted)

        // Test hours
        val hoursAgo = SettingsData(
            steamApiKey = "",
            steamId = "",
            steamUsername = "",
            lastSyncTimestamp = System.currentTimeMillis() - 7200_000, // 2 hours
            isSteamConfigured = false
        )
        assertEquals("2時間前", hoursAgo.lastSyncFormatted)
    }
}
