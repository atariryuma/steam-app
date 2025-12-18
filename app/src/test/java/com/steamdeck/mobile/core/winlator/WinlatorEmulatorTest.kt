package com.steamdeck.mobile.core.winlator

import android.content.Context
import com.steamdeck.mobile.domain.emulator.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * WinlatorEmulatorの単体テスト
 *
 * テスト対象:
 * - 初期化フロー（Box64/Wine抽出）
 * - コンテナ作成とWine prefix初期化
 * - プロセス起動とライフサイクル管理
 * - エラーハンドリング（カスタムException）
 * - プロセスステータス取得
 *
 * Best Practices:
 * - Android Context mocking
 * - File system interaction mocking
 * - Coroutines testing
 * - Exception handling validation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WinlatorEmulatorTest {

    private lateinit var emulator: WinlatorEmulator
    private lateinit var context: Context
    private lateinit var zstdDecompressor: ZstdDecompressor
    private lateinit var processMonitor: ProcessMonitor

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        zstdDecompressor = mockk(relaxed = true)
        processMonitor = mockk(relaxed = true)

        // Mock Context.filesDir
        val mockFilesDir = File("/tmp/test-winlator")
        every { context.filesDir } returns mockFilesDir

        emulator = WinlatorEmulator(context, zstdDecompressor, processMonitor)
    }

    /**
     * エミュレータ情報取得テスト
     */
    @Test
    fun `getEmulatorInfo returns correct information`() {
        // When
        val info = emulator.getEmulatorInfo()

        // Then
        assertEquals("Winlator", info.name)
        assertEquals("10.1.0", info.version)
        assertEquals(EmulatorBackend.WINLATOR, info.backend)
        assertEquals("9.0+", info.wineVersion)
        assertEquals("Box64 0.3.6", info.translationLayer)
        assertTrue(info.capabilities.contains(EmulatorCapability.DIRECT3D_11))
        assertTrue(info.capabilities.contains(EmulatorCapability.X86_64_TRANSLATION))
    }

    /**
     * isAvailable: バイナリが存在しない場合のテスト
     */
    @Test
    fun `isAvailable returns false when binaries do not exist`() = runTest {
        // Given
        // アセットが存在しないと仮定
        every { context.assets.open(any()) } throws java.io.FileNotFoundException()

        // When
        val result = emulator.isAvailable()

        // Then
        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull() ?: true)
    }

    /**
     * コンテナ作成テスト
     */
    @Test
    fun `createContainer creates directory structure`() = runTest {
        // Given
        val config = EmulatorContainerConfig(
            name = "Test Container",
            screenWidth = 1920,
            screenHeight = 1080
        )

        // When
        val result = emulator.createContainer(config)

        // Then
        assertTrue(result.isSuccess)
        val container = result.getOrNull()
        assertNotNull(container)
        assertEquals("Test Container", container?.name)
        assertEquals(config, container?.config)
    }

    /**
     * コンテナ削除: 存在しないコンテナのテスト
     */
    @Test
    fun `deleteContainer fails when container does not exist`() = runTest {
        // Given
        val nonExistentId = "999999"

        // When
        val result = emulator.deleteContainer(nonExistentId)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception should be ContainerNotFoundException",
            exception is ContainerNotFoundException)
    }

    /**
     * launchExecutable: 実行ファイルが存在しない場合のテスト
     */
    @Test
    fun `launchExecutable fails when executable does not exist`() = runTest {
        // Given
        val container = EmulatorContainer(
            id = "1",
            name = "Test",
            config = EmulatorContainerConfig("Test"),
            rootPath = File("/tmp/test"),
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            sizeBytes = 0L
        )

        val nonExistentExe = File("/nonexistent/game.exe")

        // When
        val result = emulator.launchExecutable(container, nonExistentExe)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception?.message?.contains("not found") ?: false)
    }

    /**
     * launchExecutable: Box64が初期化されていない場合のテスト
     */
    @Test
    fun `launchExecutable fails when Box64 not initialized`() = runTest {
        // Given
        val container = EmulatorContainer(
            id = "1",
            name = "Test",
            config = EmulatorContainerConfig("Test"),
            rootPath = File("/tmp/test"),
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            sizeBytes = 0L
        )

        // 実行ファイルは存在するがBox64が存在しない
        val mockExe = mockk<File>(relaxed = true)
        every { mockExe.exists() } returns true
        every { mockExe.absolutePath } returns "/tmp/game.exe"

        // When
        val result = emulator.launchExecutable(container, mockExe)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception should be Box64BinaryNotFoundException",
            exception is Box64BinaryNotFoundException)
    }

    /**
     * getProcessStatus: 存在しないプロセスのテスト
     */
    @Test
    fun `getProcessStatus fails when process does not exist`() = runTest {
        // Given
        val nonExistentProcessId = "999999_999999"

        // When
        val result = emulator.getProcessStatus(nonExistentProcessId)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception should be ProcessNotFoundException",
            exception is ProcessNotFoundException)
    }

    /**
     * killProcess: 存在しないプロセスのテスト
     */
    @Test
    fun `killProcess fails when process does not exist`() = runTest {
        // Given
        val nonExistentProcessId = "999999_999999"

        // When
        val result = emulator.killProcess(nonExistentProcessId, force = false)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception should be ProcessNotFoundException",
            exception is ProcessNotFoundException)
    }

    /**
     * cleanup: 一時ファイルとキャッシュの削除テスト
     */
    @Test
    fun `cleanup removes temporary files and cache`() = runTest {
        // When
        val result = emulator.cleanup()

        // Then
        assertTrue(result.isSuccess)
        val bytesFreed = result.getOrNull()
        assertNotNull(bytesFreed)
        // 最初は何もないので0バイト
        assertEquals(0L, bytesFreed)
    }

    /**
     * name/version プロパティのテスト
     */
    @Test
    fun `name and version properties are correct`() {
        // Then
        assertEquals("Winlator", emulator.name)
        assertEquals("10.1.0", emulator.version)
    }

    /**
     * listContainers: 空リストのテスト
     */
    @Test
    fun `listContainers returns empty list when no containers exist`() = runTest {
        // When
        val result = emulator.listContainers()

        // Then
        assertTrue(result.isSuccess)
        val containers = result.getOrNull()
        assertNotNull(containers)
        assertTrue(containers?.isEmpty() ?: false)
    }

    /**
     * installApplication: 未実装のテスト
     */
    @Test
    fun `installApplication returns not implemented error`() = runTest {
        // Given
        val container = EmulatorContainer(
            id = "1",
            name = "Test",
            config = EmulatorContainerConfig("Test"),
            rootPath = File("/tmp/test"),
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            sizeBytes = 0L
        )
        val installer = File("/tmp/installer.exe")

        // When
        val result = emulator.installApplication(container, installer)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception?.message?.contains("not implemented") ?: false)
    }

    /**
     * カスタムException型の検証テスト
     */
    @Test
    fun `custom exceptions are properly typed`() {
        // WinePrefixException
        val winePrefixEx = WinePrefixException("Wine error")
        assertTrue(winePrefixEx is EmulatorException)
        assertEquals("Wine error", winePrefixEx.message)

        // ProcessLaunchException
        val processLaunchEx = ProcessLaunchException("Launch error")
        assertTrue(processLaunchEx is EmulatorException)
        assertEquals("Launch error", processLaunchEx.message)

        // Box64BinaryNotFoundException
        val box64Ex = Box64BinaryNotFoundException()
        assertTrue(box64Ex is EmulatorException)
        assertTrue(box64Ex.message?.contains("Box64") ?: false)

        // WineBinaryNotFoundException
        val wineEx = WineBinaryNotFoundException()
        assertTrue(wineEx is EmulatorException)
        assertTrue(wineEx.message?.contains("Wine") ?: false)

        // ContainerNotFoundException
        val containerEx = ContainerNotFoundException("container123")
        assertTrue(containerEx is EmulatorException)
        assertTrue(containerEx.message?.contains("container123") ?: false)

        // ProcessNotFoundException
        val processEx = ProcessNotFoundException("process456")
        assertTrue(processEx is EmulatorException)
        assertTrue(processEx.message?.contains("process456") ?: false)
    }

    /**
     * Exception with cause のテスト
     */
    @Test
    fun `exceptions preserve cause chain`() {
        // Given
        val rootCause = IllegalStateException("Root cause")

        // When
        val winePrefixEx = WinePrefixException("Wine failed", rootCause)
        val processLaunchEx = ProcessLaunchException("Launch failed", rootCause)

        // Then
        assertEquals(rootCause, winePrefixEx.cause)
        assertEquals(rootCause, processLaunchEx.cause)
    }

    /**
     * EmulatorContainer拡張関数のテスト
     */
    @Test
    fun `EmulatorContainer extension functions work correctly`() {
        // Given
        val rootPath = File("/tmp/container")
        val container = EmulatorContainer(
            id = "1",
            name = "Test",
            config = EmulatorContainerConfig("Test"),
            rootPath = rootPath,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            sizeBytes = 0L
        )

        // Then
        assertEquals(File(rootPath, "drive_c"), container.getWinePrefix())
        assertEquals(File(rootPath, "drive_c/Program Files"), container.getProgramFiles())
        assertFalse(container.isInitialized()) // ディレクトリが存在しないのでfalse
    }
}
