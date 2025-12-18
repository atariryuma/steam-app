package com.steamdeck.mobile.core.winlator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ProcessMonitorの単体テスト
 *
 * テスト対象:
 * - プロセスメトリクス監視のフロー
 * - /proc filesystem からのデータ読み取り
 * - CPU使用率計算
 * - メモリ使用量計算
 * - uptime計算
 *
 * Best Practices:
 * - Flow testing with kotlinx-coroutines-test
 * - File I/O mocking considerations
 * - Metrics accuracy validation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProcessMonitorTest {

    private lateinit var processMonitor: ProcessMonitor

    @Before
    fun setup() {
        processMonitor = ProcessMonitor()
    }

    /**
     * 自プロセス（テストプロセス）の監視テスト
     *
     * Note: 実際のプロセスを監視するため、値の範囲のみ検証
     */
    @Test
    fun `startMonitoring emits metrics for running process`() = runTest {
        // Given
        val currentPid = android.os.Process.myPid()

        // When
        val metrics = processMonitor.startMonitoring(currentPid, intervalMs = 100)
            .take(3) // 3回分のメトリクスを取得
            .toList()

        // Then
        assertEquals(3, metrics.size)

        // すべてのメトリクスが同じPIDを持つ
        metrics.forEach { metric ->
            assertEquals(currentPid, metric.pid)

            // CPU使用率は0-100%の範囲
            assertTrue("CPU percent should be >= 0", metric.cpuPercent >= 0f)
            assertTrue("CPU percent should be <= 100", metric.cpuPercent <= 100f)

            // メモリ使用量は正の値
            assertTrue("Memory should be >= 0", metric.memoryMB >= 0)

            // uptimeは増加していく
            assertTrue("Uptime should be >= 0", metric.uptimeMs >= 0)
        }

        // uptimeが時間とともに増加することを確認
        assertTrue("Uptime should increase over time",
            metrics[2].uptimeMs >= metrics[0].uptimeMs)
    }

    /**
     * 存在しないプロセスの監視テスト
     *
     * 監視は自動的に停止する
     */
    @Test
    fun `startMonitoring stops when process does not exist`() = runTest {
        // Given - 存在しないPID
        val nonExistentPid = 999999

        // When
        val metrics = processMonitor.startMonitoring(nonExistentPid, intervalMs = 50)
            .take(5) // 最大5回試行
            .toList()

        // Then - すぐに終了するため、メトリクスは0個
        assertEquals(0, metrics.size)
    }

    /**
     * メトリクスのデータ構造テスト
     */
    @Test
    fun `ProcessMetrics has correct data structure`() {
        // Given
        val metrics = ProcessMetrics(
            pid = 1234,
            cpuPercent = 25.5f,
            memoryMB = 128,
            uptimeMs = 5000L
        )

        // Then
        assertEquals(1234, metrics.pid)
        assertEquals(25.5f, metrics.cpuPercent, 0.01f)
        assertEquals(128, metrics.memoryMB)
        assertEquals(5000L, metrics.uptimeMs)
    }

    /**
     * /proc/[pid]/stat ファイルが存在するかのテスト
     *
     * Note: 環境依存のため、自プロセスのみ検証
     */
    @Test
    fun `proc stat file exists for current process`() {
        // Given
        val currentPid = android.os.Process.myPid()
        val statFile = File("/proc/$currentPid/stat")

        // Then
        assertTrue("Stat file should exist for current process", statFile.exists())
        assertTrue("Stat file should be readable", statFile.canRead())
    }

    /**
     * /proc/[pid]/status ファイルが存在するかのテスト
     */
    @Test
    fun `proc status file exists for current process`() {
        // Given
        val currentPid = android.os.Process.myPid()
        val statusFile = File("/proc/$currentPid/status")

        // Then
        assertTrue("Status file should exist for current process", statusFile.exists())
        assertTrue("Status file should be readable", statusFile.canRead())

        // VmRSS行が存在することを確認
        val content = statusFile.readText()
        assertTrue("Status file should contain VmRSS", content.contains("VmRSS:"))
    }

    /**
     * カスタム更新間隔でのモニタリングテスト
     */
    @Test
    fun `startMonitoring respects custom interval`() = runTest {
        // Given
        val currentPid = android.os.Process.myPid()
        val customInterval = 200L
        val startTime = System.currentTimeMillis()

        // When
        val metrics = processMonitor.startMonitoring(currentPid, intervalMs = customInterval)
            .take(2)
            .toList()

        val elapsedTime = System.currentTimeMillis() - startTime

        // Then
        assertEquals(2, metrics.size)

        // 最低でも1回の間隔分の時間が経過している
        assertTrue("Elapsed time should be >= interval",
            elapsedTime >= customInterval)
    }

    /**
     * メモリ使用量が正の値であることを確認するテスト
     */
    @Test
    fun `memory usage is positive for running process`() = runTest {
        // Given
        val currentPid = android.os.Process.myPid()

        // When
        val metrics = processMonitor.startMonitoring(currentPid)
            .take(1)
            .toList()

        // Then
        assertTrue("Should have at least one metric", metrics.isNotEmpty())
        val metric = metrics.first()
        assertTrue("Memory should be positive", metric.memoryMB > 0)
    }

    /**
     * CPU使用率が合理的な範囲内であることを確認するテスト
     */
    @Test
    fun `cpu usage is within reasonable range`() = runTest {
        // Given
        val currentPid = android.os.Process.myPid()

        // When
        val metrics = processMonitor.startMonitoring(currentPid, intervalMs = 100)
            .take(5)
            .toList()

        // Then
        metrics.forEach { metric ->
            // CPU使用率は0-100%の範囲内
            assertTrue("CPU should be >= 0%: ${metric.cpuPercent}",
                metric.cpuPercent >= 0f)
            assertTrue("CPU should be <= 100%: ${metric.cpuPercent}",
                metric.cpuPercent <= 100f)

            // NaNやInfinityでないことを確認
            assertFalse("CPU should not be NaN", metric.cpuPercent.isNaN())
            assertFalse("CPU should not be Infinite", metric.cpuPercent.isInfinite())
        }
    }
}
