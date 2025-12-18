package com.steamdeck.mobile.presentation.viewmodel

import app.cash.turbine.test
import com.steamdeck.mobile.core.download.DownloadManager
import com.steamdeck.mobile.data.local.database.dao.DownloadDao
import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * DownloadViewModelの単体テスト
 *
 * テスト対象:
 * - ダウンロード一覧の取得とリアルタイム更新
 * - ダウンロードの一時停止・再開・キャンセル
 * - アクティブダウンロード数のカウント
 * - 完了済みダウンロードのクリア
 * - 新規ダウンロードの開始
 *
 * Best Practices:
 * - Turbine for StateFlow testing
 * - MockK relaxed for reducing boilerplate
 * - Realistic test data with timestamps
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {

    private lateinit var viewModel: DownloadViewModel
    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadDao: DownloadDao
    private lateinit var testDispatcher: TestDispatcher

    // Mock data
    private val mockDownload1 = DownloadEntity(
        id = 1L,
        gameId = 100L,
        fileName = "game1.zip",
        url = "https://example.com/game1.zip",
        status = DownloadStatus.DOWNLOADING,
        downloadedBytes = 5_000_000L,
        totalBytes = 10_000_000L,
        speedBytesPerSecond = 500_000L,
        destinationPath = "/storage/downloads/game1.zip",
        startedTimestamp = System.currentTimeMillis() - 60_000,
        createdAt = System.currentTimeMillis() - 60_000,
        updatedAt = System.currentTimeMillis(),
        completedTimestamp = null,
        errorMessage = null
    )

    private val mockDownload2 = DownloadEntity(
        id = 2L,
        gameId = 101L,
        fileName = "game2.zip",
        url = "https://example.com/game2.zip",
        status = DownloadStatus.PAUSED,
        downloadedBytes = 2_000_000L,
        totalBytes = 20_000_000L,
        speedBytesPerSecond = 0L,
        destinationPath = "/storage/downloads/game2.zip",
        startedTimestamp = System.currentTimeMillis() - 120_000,
        createdAt = System.currentTimeMillis() - 120_000,
        updatedAt = System.currentTimeMillis(),
        completedTimestamp = null,
        errorMessage = null
    )

    private val mockDownload3 = DownloadEntity(
        id = 3L,
        gameId = 102L,
        fileName = "game3.zip",
        url = "https://example.com/game3.zip",
        status = DownloadStatus.COMPLETED,
        downloadedBytes = 15_000_000L,
        totalBytes = 15_000_000L,
        speedBytesPerSecond = 0L,
        destinationPath = "/storage/downloads/game3.zip",
        startedTimestamp = System.currentTimeMillis() - 300_000,
        createdAt = System.currentTimeMillis() - 300_000,
        updatedAt = System.currentTimeMillis() - 60_000,
        completedTimestamp = System.currentTimeMillis() - 60_000,
        errorMessage = null
    )

    private val mockDownloads = listOf(mockDownload1, mockDownload2, mockDownload3)

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        downloadManager = mockk(relaxed = true)
        downloadDao = mockk(relaxed = true)

        // Default mock behavior
        coEvery { downloadDao.getAllDownloads() } returns flowOf(mockDownloads)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * ダウンロード一覧が正しく取得されるテスト
     */
    @Test
    fun `downloads StateFlow emits all downloads from DAO`() = runTest {
        // Given
        coEvery { downloadDao.getAllDownloads() } returns flowOf(mockDownloads)

        // When
        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.downloads.test {
            val downloads = awaitItem()
            assertEquals(3, downloads.size)
            assertEquals(mockDownload1.id, downloads[0].id)
            assertEquals(mockDownload2.id, downloads[1].id)
            assertEquals(mockDownload3.id, downloads[2].id)
        }
    }

    /**
     * ダウンロード一覧が空の場合のテスト
     */
    @Test
    fun `downloads StateFlow emits empty list when no downloads exist`() = runTest {
        // Given
        coEvery { downloadDao.getAllDownloads() } returns flowOf(emptyList())

        // When
        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.downloads.test {
            val downloads = awaitItem()
            assertTrue(downloads.isEmpty())
        }
    }

    /**
     * アクティブダウンロード数が正しくカウントされるテスト
     */
    @Test
    fun `activeDownloads counts DOWNLOADING and PENDING downloads`() = runTest {
        // Given
        val activeDownloads = listOf(
            mockDownload1.copy(status = DownloadStatus.DOWNLOADING),
            mockDownload2.copy(status = DownloadStatus.PENDING),
            mockDownload3.copy(status = DownloadStatus.COMPLETED)
        )
        coEvery { downloadDao.getAllDownloads() } returns flowOf(activeDownloads)

        // When
        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.activeDownloads.test {
            val count = awaitItem()
            assertEquals(2, count) // DOWNLOADING + PENDING
        }
    }

    /**
     * すべてのダウンロードが完了している場合、アクティブ数は0のテスト
     */
    @Test
    fun `activeDownloads returns 0 when all downloads are completed`() = runTest {
        // Given
        val completedDownloads = listOf(
            mockDownload1.copy(status = DownloadStatus.COMPLETED),
            mockDownload2.copy(status = DownloadStatus.COMPLETED)
        )
        coEvery { downloadDao.getAllDownloads() } returns flowOf(completedDownloads)

        // When
        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.activeDownloads.test {
            val count = awaitItem()
            assertEquals(0, count)
        }
    }

    /**
     * ダウンロード一時停止が正しく呼ばれるテスト
     */
    @Test
    fun `pauseDownload calls DownloadManager correctly`() = runTest {
        // Given
        val downloadId = 1L
        coEvery { downloadManager.pauseDownload(downloadId) } just Runs

        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.pauseDownload(downloadId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { downloadManager.pauseDownload(downloadId) }
    }

    /**
     * ダウンロード再開が正しく呼ばれるテスト
     */
    @Test
    fun `resumeDownload calls DownloadManager correctly`() = runTest {
        // Given
        val downloadId = 2L
        coEvery { downloadManager.resumeDownload(downloadId) } just Runs

        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.resumeDownload(downloadId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { downloadManager.resumeDownload(downloadId) }
    }

    /**
     * ダウンロードキャンセルが正しく呼ばれるテスト
     */
    @Test
    fun `cancelDownload calls DownloadManager correctly`() = runTest {
        // Given
        val downloadId = 1L
        coEvery { downloadManager.cancelDownload(downloadId) } just Runs

        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.cancelDownload(downloadId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { downloadManager.cancelDownload(downloadId) }
    }

    /**
     * ダウンロード再試行が再開として実装されていることを確認するテスト
     */
    @Test
    fun `retryDownload calls resumeDownload`() = runTest {
        // Given
        val downloadId = 3L
        coEvery { downloadManager.resumeDownload(downloadId) } just Runs

        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.retryDownload(downloadId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { downloadManager.resumeDownload(downloadId) }
    }

    /**
     * 完了済みダウンロードのクリアが正しく動作するテスト
     */
    @Test
    fun `clearCompleted removes all completed downloads`() = runTest {
        // Given
        val downloads = listOf(
            mockDownload1.copy(status = DownloadStatus.DOWNLOADING),
            mockDownload2.copy(status = DownloadStatus.COMPLETED),
            mockDownload3.copy(status = DownloadStatus.COMPLETED)
        )
        coEvery { downloadDao.getAllDownloads() } returns flowOf(downloads)
        coEvery { downloadDao.deleteDownload(any()) } just Runs

        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearCompleted()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 2) { downloadDao.deleteDownload(any()) }
        coVerify { downloadDao.deleteDownload(mockDownload2.id) }
        coVerify { downloadDao.deleteDownload(mockDownload3.id) }
        coVerify(exactly = 0) { downloadDao.deleteDownload(mockDownload1.id) }
    }

    /**
     * 完了済みダウンロードが存在しない場合、clearCompletedが何もしないテスト
     */
    @Test
    fun `clearCompleted does nothing when no completed downloads exist`() = runTest {
        // Given
        val activeDownloads = listOf(
            mockDownload1.copy(status = DownloadStatus.DOWNLOADING),
            mockDownload2.copy(status = DownloadStatus.PAUSED)
        )
        coEvery { downloadDao.getAllDownloads() } returns flowOf(activeDownloads)
        coEvery { downloadDao.deleteDownload(any()) } just Runs

        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearCompleted()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { downloadDao.deleteDownload(any()) }
    }

    /**
     * 新規ダウンロード開始が正しく動作するテスト
     */
    @Test
    fun `startDownload inserts entity and starts DownloadManager`() = runTest {
        // Given
        val url = "https://example.com/newgame.zip"
        val fileName = "newgame.zip"
        val destinationPath = "/storage/downloads/newgame.zip"
        val gameId = 200L
        val newDownloadId = 10L

        coEvery { downloadDao.insertDownload(any()) } returns newDownloadId
        coEvery { downloadManager.startDownload(any(), any(), any(), any()) } just Runs

        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.startDownload(url, fileName, destinationPath, gameId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify {
            downloadDao.insertDownload(match {
                it.gameId == gameId &&
                    it.fileName == fileName &&
                    it.url == url &&
                    it.status == DownloadStatus.PENDING &&
                    it.destinationPath == destinationPath
            })
        }

        coVerify {
            downloadManager.startDownload(
                downloadId = newDownloadId,
                url = url,
                destinationPath = destinationPath,
                fileName = fileName
            )
        }
    }

    /**
     * gameIdなしで新規ダウンロードを開始できることを確認するテスト
     */
    @Test
    fun `startDownload works without gameId`() = runTest {
        // Given
        val url = "https://example.com/file.zip"
        val fileName = "file.zip"
        val destinationPath = "/storage/downloads/file.zip"
        val newDownloadId = 20L

        coEvery { downloadDao.insertDownload(any()) } returns newDownloadId
        coEvery { downloadManager.startDownload(any(), any(), any(), any()) } just Runs

        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.startDownload(url, fileName, destinationPath, gameId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify {
            downloadDao.insertDownload(match {
                it.gameId == null &&
                    it.fileName == fileName &&
                    it.url == url
            })
        }

        coVerify {
            downloadManager.startDownload(newDownloadId, url, destinationPath, fileName)
        }
    }

    /**
     * ダウンロードの状態がリアルタイムで更新されるテスト
     */
    @Test
    fun `downloads StateFlow updates in real-time`() = runTest {
        // Given
        val initialDownloads = listOf(mockDownload1)
        val updatedDownloads = listOf(
            mockDownload1.copy(downloadedBytes = 8_000_000L)
        )

        coEvery { downloadDao.getAllDownloads() } returnsMany listOf(
            flowOf(initialDownloads),
            flowOf(updatedDownloads)
        )

        // When
        viewModel = DownloadViewModel(downloadManager, downloadDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.downloads.test {
            val initial = awaitItem()
            assertEquals(5_000_000L, initial[0].downloadedBytes)

            // Simulate real-time update
            cancelAndIgnoreRemainingEvents()
        }
    }
}
