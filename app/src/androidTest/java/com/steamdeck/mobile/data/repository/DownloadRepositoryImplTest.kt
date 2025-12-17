package com.steamdeck.mobile.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.steamdeck.mobile.data.local.database.SteamDeckDatabase
import com.steamdeck.mobile.data.local.database.dao.DownloadDao
import com.steamdeck.mobile.domain.model.Download
import com.steamdeck.mobile.domain.model.DownloadStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DownloadRepositoryImplの統合テスト
 *
 * Room In-Memory Databaseを使用した実際のDB操作テスト
 *
 * テスト対象:
 * - ダウンロードの挿入・取得・更新・削除
 * - Flowによるリアルタイム更新
 * - ダウンロード進捗の更新
 * - ステータス別のフィルタリング
 *
 * Best Practices 2025:
 * - In-memory database for isolation
 * - Turbine for Flow testing
 * - Real-world download scenarios
 * - Progress tracking validation
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DownloadRepositoryImplTest {

    private lateinit var database: SteamDeckDatabase
    private lateinit var downloadDao: DownloadDao
    private lateinit var repository: DownloadRepositoryImpl

    private val mockDownload1 = Download(
        id = 0, // Auto-generated
        gameId = 100L,
        fileName = "game1.zip",
        url = "https://example.com/game1.zip",
        status = DownloadStatus.DOWNLOADING,
        downloadedBytes = 5_000_000L,
        totalBytes = 10_000_000L,
        speedBytesPerSecond = 500_000L,
        destinationPath = "/downloads/game1.zip",
        startedTimestamp = System.currentTimeMillis() - 60_000,
        createdAt = System.currentTimeMillis() - 60_000,
        updatedAt = System.currentTimeMillis(),
        completedTimestamp = null,
        errorMessage = null
    )

    private val mockDownload2 = Download(
        id = 0,
        gameId = 101L,
        fileName = "game2.zip",
        url = "https://example.com/game2.zip",
        status = DownloadStatus.PAUSED,
        downloadedBytes = 2_000_000L,
        totalBytes = 20_000_000L,
        speedBytesPerSecond = 0L,
        destinationPath = "/downloads/game2.zip",
        startedTimestamp = System.currentTimeMillis() - 120_000,
        createdAt = System.currentTimeMillis() - 120_000,
        updatedAt = System.currentTimeMillis(),
        completedTimestamp = null,
        errorMessage = null
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            SteamDeckDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        downloadDao = database.downloadDao()
        repository = DownloadRepositoryImpl(downloadDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * ダウンロードの挿入と取得が正しく動作するテスト
     */
    @Test
    fun insertAndRetrieveDownload() = runTest {
        // When
        val insertedId = repository.insertDownload(mockDownload1)
        val retrievedDownload = repository.getDownloadById(insertedId)

        // Then
        assertNotNull(retrievedDownload)
        assertEquals(insertedId, retrievedDownload?.id)
        assertEquals("game1.zip", retrievedDownload?.fileName)
        assertEquals(DownloadStatus.DOWNLOADING, retrievedDownload?.status)
        assertEquals(5_000_000L, retrievedDownload?.downloadedBytes)
        assertEquals(10_000_000L, retrievedDownload?.totalBytes)
    }

    /**
     * getAllDownloadsがFlowで全ダウンロードを返すテスト
     */
    @Test
    fun getAllDownloadsReturnsAllInsertedDownloads() = runTest {
        // Given
        repository.insertDownload(mockDownload1)
        repository.insertDownload(mockDownload2)

        // When & Then
        repository.getAllDownloads().test {
            val downloads = awaitItem()
            assertEquals(2, downloads.size)
            assertTrue(downloads.any { it.fileName == "game1.zip" })
            assertTrue(downloads.any { it.fileName == "game2.zip" })
        }
    }

    /**
     * ダウンロードの更新が正しく動作するテスト
     */
    @Test
    fun updateDownloadModifiesExistingDownload() = runTest {
        // Given
        val insertedId = repository.insertDownload(mockDownload1)
        val insertedDownload = repository.getDownloadById(insertedId)!!

        // When
        val updatedDownload = insertedDownload.copy(
            status = DownloadStatus.COMPLETED,
            downloadedBytes = 10_000_000L,
            completedTimestamp = System.currentTimeMillis()
        )
        repository.updateDownload(updatedDownload)

        // Then
        val retrievedDownload = repository.getDownloadById(insertedId)
        assertEquals(DownloadStatus.COMPLETED, retrievedDownload?.status)
        assertEquals(10_000_000L, retrievedDownload?.downloadedBytes)
        assertNotNull(retrievedDownload?.completedTimestamp)
    }

    /**
     * ダウンロードの削除が正しく動作するテスト
     */
    @Test
    fun deleteDownloadRemovesDownloadFromDatabase() = runTest {
        // Given
        val insertedId = repository.insertDownload(mockDownload1)
        val insertedDownload = repository.getDownloadById(insertedId)!!

        // When
        repository.deleteDownload(insertedDownload)

        // Then
        val retrievedDownload = repository.getDownloadById(insertedId)
        assertNull(retrievedDownload)

        repository.getAllDownloads().test {
            val downloads = awaitItem()
            assertTrue(downloads.isEmpty())
        }
    }

    /**
     * ダウンロード進捗の更新が正しく動作するテスト
     */
    @Test
    fun updateDownloadProgressUpdatesProgressAndSpeed() = runTest {
        // Given
        val insertedId = repository.insertDownload(mockDownload1)

        // When
        val newDownloadedBytes = 7_000_000L
        val newSpeed = 800_000L
        repository.updateDownloadProgress(insertedId, newDownloadedBytes, newSpeed)

        // Then
        val updatedDownload = repository.getDownloadById(insertedId)
        assertEquals(newDownloadedBytes, updatedDownload?.downloadedBytes)
        assertEquals(newSpeed, updatedDownload?.speedBytesPerSecond)
    }

    /**
     * ダウンロードステータスの更新が正しく動作するテスト
     */
    @Test
    fun updateDownloadStatusChangesStatus() = runTest {
        // Given
        val insertedId = repository.insertDownload(mockDownload1)

        // When - Pause download
        repository.updateDownloadStatus(insertedId, DownloadStatus.PAUSED)

        // Then
        val pausedDownload = repository.getDownloadById(insertedId)
        assertEquals(DownloadStatus.PAUSED, pausedDownload?.status)

        // When - Resume download
        repository.updateDownloadStatus(insertedId, DownloadStatus.DOWNLOADING)

        // Then
        val resumedDownload = repository.getDownloadById(insertedId)
        assertEquals(DownloadStatus.DOWNLOADING, resumedDownload?.status)
    }

    /**
     * エラーメッセージの設定が正しく動作するテスト
     */
    @Test
    fun updateDownloadErrorSetsErrorMessage() = runTest {
        // Given
        val insertedId = repository.insertDownload(mockDownload1)

        // When
        val errorMessage = "Network connection lost"
        repository.updateDownloadError(insertedId, errorMessage)

        // Then
        val failedDownload = repository.getDownloadById(insertedId)
        assertEquals(DownloadStatus.FAILED, failedDownload?.status)
        assertEquals(errorMessage, failedDownload?.errorMessage)
    }

    /**
     * ゲームIDによるダウンロード取得が正しく動作するテスト
     */
    @Test
    fun getDownloadsByGameIdReturnsCorrectDownloads() = runTest {
        // Given
        val gameId = 100L
        repository.insertDownload(mockDownload1.copy(gameId = gameId))
        repository.insertDownload(mockDownload2.copy(gameId = gameId))
        repository.insertDownload(mockDownload1.copy(id = 0, gameId = 999L, fileName = "other.zip"))

        // When & Then
        repository.getDownloadsByGameId(gameId).test {
            val downloads = awaitItem()
            assertEquals(2, downloads.size)
            assertTrue(downloads.all { it.gameId == gameId })
        }
    }

    /**
     * ステータス別のダウンロード取得が正しく動作するテスト
     */
    @Test
    fun getDownloadsByStatusFiltersByStatus() = runTest {
        // Given
        repository.insertDownload(mockDownload1.copy(status = DownloadStatus.DOWNLOADING))
        repository.insertDownload(mockDownload2.copy(status = DownloadStatus.PAUSED))
        repository.insertDownload(mockDownload1.copy(id = 0, fileName = "completed.zip", status = DownloadStatus.COMPLETED))

        // When & Then - DOWNLOADING
        repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).test {
            val downloads = awaitItem()
            assertEquals(1, downloads.size)
            assertEquals(DownloadStatus.DOWNLOADING, downloads[0].status)
        }

        // When & Then - PAUSED
        repository.getDownloadsByStatus(DownloadStatus.PAUSED).test {
            val downloads = awaitItem()
            assertEquals(1, downloads.size)
            assertEquals(DownloadStatus.PAUSED, downloads[0].status)
        }

        // When & Then - COMPLETED
        repository.getDownloadsByStatus(DownloadStatus.COMPLETED).test {
            val downloads = awaitItem()
            assertEquals(1, downloads.size)
            assertEquals(DownloadStatus.COMPLETED, downloads[0].status)
        }
    }

    /**
     * Flowがリアルタイムで更新を通知することを確認するテスト
     */
    @Test
    fun getAllDownloadsFlowEmitsUpdatesWhenDownloadsChange() = runTest {
        // Given
        val flow = repository.getAllDownloads()

        flow.test {
            // Initial state: empty
            val emptyList = awaitItem()
            assertTrue(emptyList.isEmpty())

            // Insert first download
            repository.insertDownload(mockDownload1)
            val oneDownload = awaitItem()
            assertEquals(1, oneDownload.size)

            // Insert second download
            repository.insertDownload(mockDownload2)
            val twoDownloads = awaitItem()
            assertEquals(2, twoDownloads.size)
        }
    }

    /**
     * 進捗がリアルタイムで更新されるテスト
     */
    @Test
    fun downloadProgressUpdatesAreEmittedInRealTime() = runTest {
        // Given
        val insertedId = repository.insertDownload(mockDownload1)
        val flow = repository.getAllDownloads()

        flow.test {
            // Skip initial emission
            val initial = awaitItem()
            assertEquals(5_000_000L, initial[0].downloadedBytes)

            // Update progress
            repository.updateDownloadProgress(insertedId, 6_000_000L, 600_000L)

            // Should emit updated download
            val updated = awaitItem()
            assertEquals(6_000_000L, updated[0].downloadedBytes)
            assertEquals(600_000L, updated[0].speedBytesPerSecond)
        }
    }

    /**
     * ダウンロード完了時にタイムスタンプが記録されるテスト
     */
    @Test
    fun completedDownloadsHaveCompletionTimestamp() = runTest {
        // Given
        val insertedId = repository.insertDownload(mockDownload1)

        // When
        val completionTimestamp = System.currentTimeMillis()
        val completedDownload = mockDownload1.copy(
            id = insertedId,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = 10_000_000L,
            completedTimestamp = completionTimestamp
        )
        repository.updateDownload(completedDownload)

        // Then
        val retrievedDownload = repository.getDownloadById(insertedId)
        assertEquals(DownloadStatus.COMPLETED, retrievedDownload?.status)
        assertEquals(completionTimestamp, retrievedDownload?.completedTimestamp)
    }

    /**
     * 存在しないダウンロードIDを検索した場合nullが返されるテスト
     */
    @Test
    fun getDownloadByIdReturnsNullForNonExistentId() = runTest {
        // When
        val nonExistentDownload = repository.getDownloadById(99999L)

        // Then
        assertNull(nonExistentDownload)
    }

    /**
     * 大量のダウンロードを処理できることを確認するテスト
     */
    @Test
    fun handlesLargeNumberOfDownloads() = runTest {
        // Given
        val largeDownloadList = (1..50).map { index ->
            mockDownload1.copy(
                id = 0,
                fileName = "game$index.zip",
                gameId = index.toLong()
            )
        }

        // When
        largeDownloadList.forEach { download ->
            repository.insertDownload(download)
        }

        // Then
        repository.getAllDownloads().test {
            val downloads = awaitItem()
            assertEquals(50, downloads.size)
        }
    }

    /**
     * 進捗計算が正確であることを確認するテスト
     */
    @Test
    fun downloadProgressPercentageIsAccurate() = runTest {
        // Given
        val download = mockDownload1.copy(
            downloadedBytes = 5_000_000L,
            totalBytes = 10_000_000L
        )
        val insertedId = repository.insertDownload(download)

        // When
        val retrievedDownload = repository.getDownloadById(insertedId)

        // Then
        val expectedProgress = 50 // 50%
        val actualProgress = (retrievedDownload!!.downloadedBytes * 100) / retrievedDownload.totalBytes
        assertEquals(expectedProgress, actualProgress.toInt())
    }

    /**
     * 複数のダウンロードステータスをテストする総合テスト
     */
    @Test
    fun comprehensiveDownloadLifecycleTest() = runTest {
        // Given - Insert download in PENDING state
        val pendingDownload = mockDownload1.copy(status = DownloadStatus.PENDING)
        val downloadId = repository.insertDownload(pendingDownload)

        // Verify PENDING
        var download = repository.getDownloadById(downloadId)
        assertEquals(DownloadStatus.PENDING, download?.status)

        // Transition to DOWNLOADING
        repository.updateDownloadStatus(downloadId, DownloadStatus.DOWNLOADING)
        download = repository.getDownloadById(downloadId)
        assertEquals(DownloadStatus.DOWNLOADING, download?.status)

        // Update progress
        repository.updateDownloadProgress(downloadId, 3_000_000L, 300_000L)
        download = repository.getDownloadById(downloadId)
        assertEquals(3_000_000L, download?.downloadedBytes)

        // Pause
        repository.updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
        download = repository.getDownloadById(downloadId)
        assertEquals(DownloadStatus.PAUSED, download?.status)

        // Resume
        repository.updateDownloadStatus(downloadId, DownloadStatus.DOWNLOADING)
        download = repository.getDownloadById(downloadId)
        assertEquals(DownloadStatus.DOWNLOADING, download?.status)

        // Complete
        val completionTime = System.currentTimeMillis()
        repository.updateDownload(
            download!!.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = 10_000_000L,
                completedTimestamp = completionTime
            )
        )
        download = repository.getDownloadById(downloadId)
        assertEquals(DownloadStatus.COMPLETED, download?.status)
        assertEquals(10_000_000L, download?.downloadedBytes)
        assertEquals(completionTime, download?.completedTimestamp)
    }

    /**
     * キャンセルされたダウンロードのテスト
     */
    @Test
    fun cancelledDownloadsHaveCorrectStatus() = runTest {
        // Given
        val insertedId = repository.insertDownload(mockDownload1)

        // When
        repository.updateDownloadStatus(insertedId, DownloadStatus.CANCELLED)

        // Then
        val cancelledDownload = repository.getDownloadById(insertedId)
        assertEquals(DownloadStatus.CANCELLED, cancelledDownload?.status)
    }
}
