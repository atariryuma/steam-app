package com.steamdeck.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdeck.mobile.domain.emulator.EmulatorContainerConfig
import com.steamdeck.mobile.domain.emulator.WindowsEmulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Wine/Winlator integration testing.
 *
 * Now uses the abstraction layer (WindowsEmulator interface)
 * instead of direct Winlator implementation.
 */
@HiltViewModel
class WineTestViewModel @Inject constructor(
    private val emulator: WindowsEmulator
) : ViewModel() {

    private val _uiState = MutableStateFlow<WineTestUiState>(WineTestUiState.Idle)
    val uiState: StateFlow<WineTestUiState> = _uiState.asStateFlow()

    /**
     * Check if emulator is available and properly configured.
     */
    fun checkWineAvailability() {
        viewModelScope.launch {
            _uiState.value = WineTestUiState.Testing("Checking ${emulator.name} availability...")

            emulator.isAvailable().fold(
                onSuccess = { isAvailable ->
                    if (isAvailable) {
                        val info = emulator.getEmulatorInfo()
                        _uiState.value = WineTestUiState.Success(
                            """
                            ✓ ${info.name} ${info.version} is available!

                            Backend: ${info.backend}
                            Wine: ${info.wineVersion ?: "N/A"}
                            Translation: ${info.translationLayer ?: "N/A"}
                            Graphics: ${info.graphicsBackend ?: "N/A"}

                            Capabilities:
                            ${info.capabilities.joinToString("\n") { "• $it" }}

                            Ready for initialization.
                            """.trimIndent()
                        )
                    } else {
                        _uiState.value = WineTestUiState.Error(
                            """
                            ${emulator.name} is not available.

                            Required components:
                            • Wine binaries (~100MB download)
                            • Box64/FEX translator
                            • Linux rootfs environment
                            • Graphics drivers (DXVK/VKD3D)

                            Click "Initialize Emulator" to set up.
                            """.trimIndent()
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = WineTestUiState.Error(
                        "Error checking availability: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Initialize the emulator environment.
     */
    fun initializeEmulator() {
        viewModelScope.launch {
            _uiState.value = WineTestUiState.Testing("Initializing ${emulator.name}...")

            emulator.initialize { progress, message ->
                _uiState.value = WineTestUiState.Testing(
                    "$message (${(progress * 100).toInt()}%)"
                )
            }.fold(
                onSuccess = {
                    _uiState.value = WineTestUiState.Success(
                        """
                        ✓ ${emulator.name} initialized successfully!

                        You can now:
                        • Create containers
                        • Install Windows applications
                        • Launch games

                        See Settings for container management.
                        """.trimIndent()
                    )
                },
                onFailure = { error ->
                    _uiState.value = WineTestUiState.Error(
                        """
                        Initialization failed: ${error.message}

                        Note: Full Winlator integration requires:
                        1. Wine binary download (~100MB)
                        2. zstd decompression tool
                        3. Root/proot for chroot environment

                        Current implementation is partial.
                        See WINLATOR_ARCHITECTURE_FINDINGS.md
                        """.trimIndent()
                    )
                }
            )
        }
    }

    /**
     * Test container creation.
     */
    fun testCreateContainer() {
        viewModelScope.launch {
            _uiState.value = WineTestUiState.Testing("Creating test container...")

            val config = EmulatorContainerConfig(
                name = "TestContainer_${System.currentTimeMillis()}"
            )

            emulator.createContainer(config).fold(
                onSuccess = { container ->
                    _uiState.value = WineTestUiState.Success(
                        """
                        ✓ Container created successfully!

                        ID: ${container.id}
                        Name: ${container.name}
                        Path: ${container.rootPath}
                        Size: ${container.sizeBytes / 1024}KB
                        Initialized: ${container.isInitialized()}

                        Container is ready for use.
                        """.trimIndent()
                    )
                },
                onFailure = { error ->
                    _uiState.value = WineTestUiState.Error(
                        "Container creation failed: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * List all containers.
     */
    fun listContainers() {
        viewModelScope.launch {
            _uiState.value = WineTestUiState.Testing("Loading containers...")

            emulator.listContainers().fold(
                onSuccess = { containers ->
                    if (containers.isEmpty()) {
                        _uiState.value = WineTestUiState.Success(
                            "No containers found.\n\nCreate one to get started."
                        )
                    } else {
                        val containerList = containers.joinToString("\n\n") { container ->
                            """
                            📦 ${container.name}
                            ID: ${container.id}
                            Size: ${container.sizeBytes / 1024 / 1024}MB
                            Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(container.createdAt)}
                            """.trimIndent()
                        }
                        _uiState.value = WineTestUiState.Success(
                            "Found ${containers.size} container(s):\n\n$containerList"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = WineTestUiState.Error(
                        "Failed to list containers: ${error.message}"
                    )
                }
            )
        }
    }
}

/**
 * UI state for Wine testing.
 */
sealed class WineTestUiState {
    /**
     * Initial idle state.
     */
    data object Idle : WineTestUiState()

    /**
     * Test is running.
     */
    data class Testing(val message: String) : WineTestUiState()

    /**
     * Test completed successfully.
     */
    data class Success(val message: String) : WineTestUiState()

    /**
     * Test failed with error.
     */
    data class Error(val message: String) : WineTestUiState()
}
