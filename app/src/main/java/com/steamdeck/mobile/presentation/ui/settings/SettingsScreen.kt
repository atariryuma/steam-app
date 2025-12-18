package com.steamdeck.mobile.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.presentation.viewmodel.SettingsUiState
import com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel
import com.steamdeck.mobile.presentation.viewmodel.SteamInstallState
import com.steamdeck.mobile.presentation.viewmodel.SyncState

/**
 * Settings画面 - BackboneOne風デザイン
 *
 * Best Practices:
 * - NavigationRail for side navigation (3-7 items recommended)
 * - List-detail canonical layout for tablet optimization
 * - Steam color scheme with Material3
 * - No TopAppBar for immersive full-screen experience
 *
 * References:
 * - https://developer.android.com/develop/ui/compose/components/navigation-rail
 * - https://codelabs.developers.google.com/codelabs/adaptive-material-guidance
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWineTest: () -> Unit = {},
    onNavigateToControllerSettings: () -> Unit = {},
    onNavigateToSteamLogin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val steamInstallState by viewModel.steamInstallState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSection by remember { mutableIntStateOf(0) }

    // エラー・成功メッセージのスナックバー表示
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SettingsUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.clearError()
            }
            is SettingsUiState.Success -> {
                state.successMessage?.let { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short
                    )
                    viewModel.clearSuccessMessage()
                }
            }
            else -> {}
        }
    }

    // 同期完了メッセージのスナックバー表示
    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "${state.syncedGamesCount}個のゲームを同期しました",
                    duration = SnackbarDuration.Short
                )
                viewModel.resetSyncState()
            }
            is SyncState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetSyncState()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is SettingsUiState.Loading -> {
                LoadingContent()
            }
            is SettingsUiState.Success -> {
                // BackboneOne風レイアウト: NavigationRail + Content
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左サイドバー（NavigationRail）
                    SettingsNavigationRail(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it },
                        onNavigateBack = onNavigateBack
                    )

                    // 右側コンテンツ
                    SettingsContent(
                        selectedSection = selectedSection,
                        data = state.data,
                        syncState = syncState,
                        steamInstallState = steamInstallState,
                        onSyncLibrary = viewModel::syncSteamLibrary,
                        onClearSettings = viewModel::clearSteamSettings,
                        onNavigateToWineTest = onNavigateToWineTest,
                        onNavigateToControllerSettings = onNavigateToControllerSettings,
                        onNavigateToSteamLogin = onNavigateToSteamLogin,
                        onSaveApiKey = viewModel::saveSteamApiKey,
                        onInstallSteam = viewModel::installSteamClient,
                        onOpenSteam = viewModel::openSteamClient,
                        onUninstallSteam = viewModel::uninstallSteamClient
                    )
                }
            }
            is SettingsUiState.Error -> {
                LoadingContent()
            }
        }

        // Snackbar表示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * NavigationRail - 左サイドバーナビゲーション
 *
 * Best Practice: 3-7 items recommended by Material3 guidelines
 */
@Composable
private fun SettingsNavigationRail(
    selectedSection: Int,
    onSectionSelected: (Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        header = {
            // 戻るボタン
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "戻る",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Steam認証
        NavigationRailItem(
            selected = selectedSection == 0,
            onClick = { onSectionSelected(0) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Steam認証"
                )
            },
            label = { Text("認証") }
        )

        // Steam Client
        NavigationRailItem(
            selected = selectedSection == 1,
            onClick = { onSectionSelected(1) },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Steam Client"
                )
            },
            label = { Text("Client") }
        )

        // ライブラリ同期
        NavigationRailItem(
            selected = selectedSection == 2,
            onClick = { onSectionSelected(2) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "ライブラリ"
                )
            },
            label = { Text("同期") }
        )

        // コントローラー
        NavigationRailItem(
            selected = selectedSection == 3,
            onClick = { onSectionSelected(3) },
            icon = {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = "コントローラー"
                )
            },
            label = { Text("操作") }
        )

        // Wine/Winlator
        NavigationRailItem(
            selected = selectedSection == 4,
            onClick = { onSectionSelected(4) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Wine"
                )
            },
            label = { Text("Wine") }
        )

        // アプリ設定
        NavigationRailItem(
            selected = selectedSection == 5,
            onClick = { onSectionSelected(5) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "アプリ"
                )
            },
            label = { Text("アプリ") }
        )
    }
}

/**
 * SettingsContent - 選択されたセクションの詳細表示
 *
 * Best Practice: Max-width for large screens to avoid stretching
 */
@Composable
private fun SettingsContent(
    selectedSection: Int,
    data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
    syncState: SyncState,
    steamInstallState: SteamInstallState,
    onSyncLibrary: () -> Unit,
    onClearSettings: () -> Unit,
    onNavigateToWineTest: () -> Unit,
    onNavigateToControllerSettings: () -> Unit,
    onNavigateToSteamLogin: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onInstallSteam: (Long) -> Unit,
    onOpenSteam: (Long) -> Unit,
    onUninstallSteam: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // セクションタイトル
            Text(
                text = when (selectedSection) {
                    0 -> "Steam認証"
                    1 -> "Steam Client"
                    2 -> "ライブラリ同期"
                    3 -> "コントローラー設定"
                    4 -> "Wine/Winlator統合"
                    5 -> "アプリ設定"
                    else -> ""
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // セクション別コンテンツ
            when (selectedSection) {
                0 -> SteamAuthContent(
                    data = data,
                    onClear = onClearSettings,
                    onNavigateToSteamLogin = onNavigateToSteamLogin
                )
                1 -> SteamClientContent(
                    steamInstallState = steamInstallState,
                    defaultContainerId = 1L,
                    onInstall = onInstallSteam,
                    onOpen = onOpenSteam,
                    onUninstall = onUninstallSteam
                )
                2 -> LibrarySyncContent(
                    data = data,
                    syncState = syncState,
                    onSync = onSyncLibrary
                )
                3 -> ControllerContent(
                    onNavigateToControllerSettings = onNavigateToControllerSettings
                )
                4 -> WineTestContent(
                    onNavigateToWineTest = onNavigateToWineTest
                )
                5 -> AppSettingsContent()
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "設定を読み込み中...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ========================================
// セクション別コンテンツ（BackboneOne風カード）
// ========================================

@Composable
private fun SteamAuthContent(
    data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
    onClear: () -> Unit,
    onNavigateToSteamLogin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 認証状態表示
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (data.isSteamConfigured) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = if (data.isSteamConfigured) "認証済み" else "未認証",
                    tint = if (data.isSteamConfigured)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (data.isSteamConfigured) "✓ ログイン済み" else "未ログイン",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (data.isSteamConfigured)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            if (data.isSteamConfigured) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "アカウント: ${data.steamUsername}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Steam ID: ${data.steamId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Steamモバイルアプリで簡単ログイン",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // QRコードログイン
            Button(
                onClick = onNavigateToSteamLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "QRログイン"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (data.isSteamConfigured) "再ログイン" else "QRコードでログイン",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // ログアウトボタン（認証済みの場合のみ）
            if (data.isSteamConfigured) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "ログアウト"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ログアウト")
                }
            }
        }
    }
}

@Composable
private fun SteamClientContent(
    steamInstallState: SteamInstallState,
    defaultContainerId: Long,
    onInstall: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onUninstall: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = steamInstallState) {
                is SteamInstallState.Idle,
                is SteamInstallState.Checking -> {
                    CircularProgressIndicator()
                    Text(
                        text = "確認中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is SteamInstallState.NotInstalled -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "未インストール",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "未インストール",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "Wine環境にSteam Clientをインストールすることで、Steam経由でゲームを起動できます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "⚠️ ダウンロードサイズ: 約100MB\n⏱️ インストール時間: 2〜3分",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { onInstall(defaultContainerId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "インストール"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Steam Clientをインストール")
                    }
                }

                is SteamInstallState.Installing -> {
                    SteamInstallProgressContent(state = state)
                }

                is SteamInstallState.Installed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "インストール済み",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "✓ インストール済み",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "パス: ${state.installPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { onOpen(defaultContainerId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Steam Client起動"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Steam Clientを開く")
                    }

                    OutlinedButton(
                        onClick = { onUninstall(defaultContainerId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "アンインストール"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("アンインストール")
                    }
                }

                is SteamInstallState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "エラー",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "エラー",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    Button(
                        onClick = { onInstall(defaultContainerId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "再試行"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("再試行")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySyncContent(
    data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
    syncState: SyncState,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "最終同期: ${data.lastSyncFormatted}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 同期状態表示
            when (val state = syncState) {
                is SyncState.Syncing -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                else -> {}
            }

            // 同期ボタン
            FilledTonalButton(
                onClick = onSync,
                enabled = data.isSteamConfigured && syncState !is SyncState.Syncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "同期"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (syncState is SyncState.Syncing) "同期中..." else "ライブラリを同期"
                )
            }

            if (!data.isSteamConfigured) {
                Text(
                    text = "※ Steam認証が必要です",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ControllerContent(
    onNavigateToControllerSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ゲームコントローラーのボタンマッピングとプロファイル管理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onNavigateToControllerSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = "コントローラー設定"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("コントローラー設定を開く")
            }
        }
    }
}

@Composable
private fun WineTestContent(
    onNavigateToWineTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🚧 Windowsゲーム実行環境（実験的機能）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onNavigateToWineTest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "テスト"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wine環境をテスト")
            }

            Text(
                text = "※ Wine環境はダウンロードが必要です (~100MB)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AppSettingsContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🚧 テーマ切り替え、言語設定などは今後実装予定です",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SteamInstallProgressContent(state: SteamInstallState.Installing) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Steam Clientをインストール中...",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${(state.progress * 100).toInt()}% - ${state.message}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "⚠️ インストール中は画面を閉じないでください",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
