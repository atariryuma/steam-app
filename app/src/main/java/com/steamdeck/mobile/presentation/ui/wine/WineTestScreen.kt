package com.steamdeck.mobile.presentation.ui.wine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.presentation.viewmodel.WineTestUiState
import com.steamdeck.mobile.presentation.viewmodel.WineTestViewModel

/**
 * Wine/Winlator integration test screen.
 *
 * Displays status of Wine environment and allows testing basic functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WineTestScreen(
    onNavigateBack: () -> Unit,
    viewModel: WineTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wine Integration Test") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            WineStatusCard(uiState = uiState)

            // Architecture Info Card
            ArchitectureInfoCard()

            // Test Actions
            if (uiState !is WineTestUiState.Testing) {
                TestActionsCard(
                    onCheckWine = viewModel::checkWineAvailability,
                    onTestBox64 = {}, // Deprecated - kept for compatibility
                    onInitialize = viewModel::initializeEmulator,
                    onCreateContainer = viewModel::testCreateContainer,
                    onListContainers = viewModel::listContainers
                )
            }

            // Progress/Results
            when (val state = uiState) {
                is WineTestUiState.Testing -> {
                    TestingProgressCard(message = state.message)
                }
                is WineTestUiState.Success -> {
                    TestResultCard(
                        title = "✓ テスト成功",
                        message = state.message,
                        isSuccess = true
                    )
                }
                is WineTestUiState.Error -> {
                    TestResultCard(
                        title = "✗ エラー",
                        message = state.message,
                        isSuccess = false
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun WineStatusCard(uiState: WineTestUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (uiState) {
                        is WineTestUiState.Success -> Icons.Default.Check
                        is WineTestUiState.Error -> Icons.Default.Close
                        else -> Icons.Default.Info
                    },
                    contentDescription = when (uiState) {
                        is WineTestUiState.Success -> "成功"
                        is WineTestUiState.Error -> "エラー"
                        else -> "情報"
                    },
                    tint = when (uiState) {
                        is WineTestUiState.Success -> MaterialTheme.colorScheme.primary
                        is WineTestUiState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Wine/Winlator Status",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = when (uiState) {
                    is WineTestUiState.Idle -> "準備完了 - テストを開始してください"
                    is WineTestUiState.Testing -> "テスト実行中..."
                    is WineTestUiState.Success -> "Wine環境が利用可能です"
                    is WineTestUiState.Error -> "Wine環境が利用できません"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArchitectureInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📋 Winlator Architecture",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Text(
                text = """
                    • Box64: x86_64 → ARM64 binary translation
                    • Wine: Windows API compatibility layer
                    • Linux Rootfs: Full Linux userland (chroot)
                    • DXVK: DirectX → Vulkan translation
                    • Turnip: Graphics driver (Qualcomm Adreno)
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Text(
                text = "⚠️ Note: Wine binaries are downloaded separately at runtime (~100MB)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TestActionsCard(
    onCheckWine: () -> Unit,
    onTestBox64: () -> Unit,
    onInitialize: () -> Unit = {},
    onCreateContainer: () -> Unit = {},
    onListContainers: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Test Actions",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = onCheckWine,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("1. Check Emulator Availability")
            }

            Button(
                onClick = onInitialize,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2. Initialize Emulator")
            }

            Button(
                onClick = onCreateContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("3. Create Test Container")
            }

            Button(
                onClick = onListContainers,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("4. List Containers")
            }
        }
    }
}

@Composable
private fun TestingProgressCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TestResultCard(
    title: String,
    message: String,
    isSuccess: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSuccess)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSuccess)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
