package com.steamdeck.mobile.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.presentation.viewmodel.SettingsUiState
import com.steamdeck.mobile.presentation.viewmodel.SettingsViewModel
import com.steamdeck.mobile.presentation.viewmodel.SyncState

/**
 * Settings画面
 *
 * Steam認証、ライブラリ同期、アプリ設定を管理
 * Fullscreen mode - No TopAppBar for maximum screen space
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
    val snackbarHostState = remember { SnackbarHostState() }

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
                SettingsContent(
                    data = state.data,
                    syncState = syncState,
                    onSyncLibrary = viewModel::syncSteamLibrary,
                    onClearSettings = viewModel::clearSteamSettings,
                    onNavigateToWineTest = onNavigateToWineTest,
                    onNavigateToControllerSettings = onNavigateToControllerSettings,
                    onNavigateToSteamLogin = onNavigateToSteamLogin,
                    onSaveApiKey = viewModel::saveSteamApiKey
                )
            }
            is SettingsUiState.Error -> {
                // エラーはスナックバーで表示済み
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

@Composable
private fun SettingsContent(
    data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
    syncState: SyncState,
    onSyncLibrary: () -> Unit,
    onClearSettings: () -> Unit,
    onNavigateToWineTest: () -> Unit,
    onNavigateToControllerSettings: () -> Unit,
    onNavigateToSteamLogin: () -> Unit,
    onSaveApiKey: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Steam認証セクション
        SteamAuthSection(
            data = data,
            onClear = onClearSettings,
            onNavigateToSteamLogin = onNavigateToSteamLogin
        )

        // ライブラリ同期セクション
        LibrarySyncSection(
            data = data,
            syncState = syncState,
            onSync = onSyncLibrary
        )

        // コントローラー設定セクション
        ControllerSection(onNavigateToControllerSettings = onNavigateToControllerSettings)

        // Wine/Winlator テストセクション
        WineTestSection(onNavigateToWineTest = onNavigateToWineTest)

        // アプリ設定セクション（将来実装）
        AppSettingsSection()
    }
}

@Composable
private fun SteamAuthSection(
    data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
    onClear: () -> Unit,
    onNavigateToSteamLogin: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // セクションタイトル
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (data.isSteamConfigured) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = if (data.isSteamConfigured) "認証済み" else "未認証",
                    tint = if (data.isSteamConfigured)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Steam認証",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // 認証状態表示
            if (data.isSteamConfigured) {
                Text(
                    text = "✓ ログイン済み",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "アカウント: ${data.steamUsername}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Steam ID: ${data.steamId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = "未ログイン",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Steamモバイルアプリで簡単ログイン",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // QRコードログイン
            Button(
                onClick = onNavigateToSteamLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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
private fun LibrarySyncSection(
    data: com.steamdeck.mobile.presentation.viewmodel.SettingsData,
    syncState: SyncState,
    onSync: () -> Unit
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
            // セクションタイトル
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "同期",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ライブラリ同期",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 最終同期日時
            Text(
                text = "最終同期: ${data.lastSyncFormatted}",
                style = MaterialTheme.typography.bodyMedium,
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
                            style = MaterialTheme.typography.bodySmall,
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
private fun ControllerSection(
    onNavigateToControllerSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = "コントローラー",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "コントローラー設定",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = "ゲームコントローラーのボタンマッピングとプロファイル管理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
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
private fun WineTestSection(
    onNavigateToWineTest: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "警告",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Wine/Winlator 統合",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Text(
                text = "🚧 Windowsゲーム実行環境（実験的機能）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
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
private fun AppSettingsSection() {
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
                text = "アプリ設定",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "🚧 テーマ切り替え、言語設定などは今後実装予定です",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SteamApiKeySection(
    onSaveApiKey: (String) -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Steam Web API Key（オプション）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = "QR認証でログイン済みの場合は不要です。API Keyがあればライブラリ同期が可能になります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            TextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key (32文字)") },
                placeholder = { Text("例: 1A2B3C4D5E6F7A8B9C0D1E2F3A4B5C6D") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Button(
                onClick = {
                    onSaveApiKey(apiKeyInput.trim())
                    apiKeyInput = "" // 保存後にクリア
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKeyInput.isNotBlank()
            ) {
                Text("API Keyを保存")
            }

            Text(
                text = "💡 取得方法: https://steamcommunity.com/dev/apikey",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
