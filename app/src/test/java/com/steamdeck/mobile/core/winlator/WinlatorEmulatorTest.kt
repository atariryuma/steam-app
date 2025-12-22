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
 * WinlatorEmulator unit test
 *
 * Test targets:
 * - Initialization flow (Box64/Wine extraction)
 * - Container creation and Wine prefix initialization
 * - Process launch and lifecycle management
 * - Error handling (custom Exceptions)
 * - Process status retrieval
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
     * Test emulator information retrieval
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
     * Test isAvailable when binaries do not exist
     */
    @Test
    fun `isAvailable returns false when binaries do not exist`() = runTest {
        // Given
        // Assume assets do not exist
        every { context.assets.open(any()) } throws java.io.FileNotFoundException()

        // When
        val result = emulator.isAvailable()

        // Then
        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull() ?: true)
    }

    /**
     * Test container creation
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
     * Test container deletion when container does not exist
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
     * Test launchExecutable when executable does not exist
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
     * Test launchExecutable when Box64 is not initialized
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

        // Executable exists but Box64 does not exist
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
     * Test getProcessStatus when process does not exist
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
     * Test killProcess when process does not exist
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
     * Test cleanup removes temporary files and cache
     */
    @Test
    fun `cleanup removes temporary files and cache`() = runTest {
        // When
        val result = emulator.cleanup()

        // Then
        assertTrue(result.isSuccess)
        val bytesFreed = result.getOrNull()
        assertNotNull(bytesFreed)
        // Initially nothing exists so 0 bytes
        assertEquals(0L, bytesFreed)
    }

    /**
     * Test name/version properties
     */
    @Test
    fun `name and version properties are correct`() {
        // Then
        assertEquals("Winlator", emulator.name)
        assertEquals("10.1.0", emulator.version)
    }

    /**
     * Test listContainers returns empty list
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
     * Test installApplication not implemented error
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
     * Test custom exception types validation
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
     * Test exception with cause
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
     * Test EmulatorContainer extension functions
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
        assertFalse(container.isInitialized()) // false because directory does not exist
    }
}
