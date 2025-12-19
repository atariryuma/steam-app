package com.steamdeck.mobile.presentation.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.presentation.viewmodel.GameDetailUiState
import com.steamdeck.mobile.presentation.viewmodel.GameDetailViewModel
import com.steamdeck.mobile.presentation.viewmodel.LaunchState
import com.steamdeck.mobile.presentation.viewmodel.SteamLaunchState

/**
 * ゲーム詳細画面 - BackboneOne風デザイン
 *
 * Best Practices:
 * - No TopAppBar for immersive full-screen experience
 * - Large banner image with gradient overlay
 * - Steam color scheme with Material3
 * - Card elevation: 2dp
 * - Padding: 24dp (sections), 20dp (cards)
 *
 * References:
 * - https://m3.material.io/develop/android/jetpack-compose
 */
@Composable
fun GameDetailScreen(
    gameId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToImport: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: GameDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val launchState by viewModel.launchState.collectAsState()
    val steamLaunchState by viewModel.steamLaunchState.collectAsState()
    val isSteamInstalled by viewModel.isSteamInstalled.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLaunchErrorDialog by remember { mutableStateOf(false) }
    var showSteamLaunchErrorDialog by remember { mutableStateOf(false) }
    var launchErrorMessage by remember { mutableStateOf("") }
    var steamLaunchErrorMessage by remember { mutableStateOf("") }

    // ゲーム詳細を読み込み
    LaunchedEffect(gameId) {
        viewModel.loadGame(gameId)
    }

    // 削除完了時に戻る
    LaunchedEffect(uiState) {
        if (uiState is GameDetailUiState.Deleted) {
            onNavigateBack()
        }
    }

    // 起動エラーを監視
    LaunchedEffect(launchState) {
        if (launchState is LaunchState.Error) {
            launchErrorMessage = (launchState as LaunchState.Error).message
            showLaunchErrorDialog = true
        }
    }

    // Steam起動エラーを監視
    LaunchedEffect(steamLaunchState) {
        if (steamLaunchState is SteamLaunchState.Error) {
            steamLaunchErrorMessage = (steamLaunchState as SteamLaunchState.Error).message
            showSteamLaunchErrorDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is GameDetailUiState.Loading -> {
                LoadingContent()
            }
            is GameDetailUiState.Success -> {
                GameDetailContent(
                    game = state.game,
                    isSteamInstalled = isSteamInstalled,
                    onLaunchGame = { viewModel.launchGame(gameId) },
                    onLaunchViaSteam = { viewModel.launchGameViaSteam(gameId) },
                    onOpenSteamClient = { viewModel.openSteamClient(gameId) },
                    onNavigateBack = onNavigateBack,
                    onNavigateToSettings = onNavigateToSettings,
                    onToggleFavorite = { viewModel.toggleFavorite(state.game.id, !state.game.isFavorite) },
                    onDeleteGame = { showDeleteDialog = true }
                )

                // 削除確認ダイアログ
                if (showDeleteDialog) {
                    DeleteConfirmDialog(
                        gameName = state.game.name,
                        onConfirm = {
                            viewModel.deleteGame(state.game)
                            showDeleteDialog = false
                        },
                        onDismiss = { showDeleteDialog = false }
                    )
                }

                // 起動中ダイアログ (Winlator初期化含む)
                if (launchState is LaunchState.Launching) {
                    LaunchingDialog()
                }

                // 起動エラーダイアログ
                if (showLaunchErrorDialog) {
                    LaunchErrorDialog(
                        message = launchErrorMessage,
                        onDismiss = { showLaunchErrorDialog = false }
                    )
                }

                // Steam起動エラーダイアログ
                if (showSteamLaunchErrorDialog) {
                    SteamLaunchErrorDialog(
                        message = steamLaunchErrorMessage,
                        onDismiss = {
                            showSteamLaunchErrorDialog = false
                            viewModel.resetSteamLaunchState()
                        },
                        onNavigateToSettings = {
                            showSteamLaunchErrorDialog = false
                            onNavigateToSettings()
                        }
                    )
                }
            }
            is GameDetailUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onNavigateBack = onNavigateBack
                )
            }
            is GameDetailUiState.Deleted -> {
                // 削除完了後はLaunchedEffectで戻る
            }
        }
    }
}

@Composable
fun GameDetailContent(
    game: Game,
    isSteamInstalled: Boolean,
    onLaunchGame: () -> Unit,
    onLaunchViaSteam: () -> Unit,
    onOpenSteamClient: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // BackboneOne風 大画面バナー with オーバーレイヘッダー
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            // バナー画像
            AsyncImage(
                model = game.bannerPath ?: game.iconPath,
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // グラデーションオーバーレイ
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f
                        )
                    )
            )

            // 上部ヘッダー（戻るボタン + アクション）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "戻る",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "ゲーム設定",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "お気に入り",
                            tint = if (game.isFavorite) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = onDeleteGame) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "削除",
                            tint = Color.White
                        )
                    }
                }
            }

            // 下部ゲームタイトル
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (game.playTimeMinutes > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = game.playTimeFormatted,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Steam規約準拠: 公式Steamクライアント経由でのダウンロードを促す
            if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM &&
                game.executablePath.isBlank()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "ゲームダウンロード",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            "このゲームをプレイするには、Winlator内のSteam公式クライアントからダウンロードしてください。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Button(
                            onClick = onOpenSteamClient,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Steam Clientを開く")
                        }
                    }
                }
            }

            // スプリット起動ボタン
            SplitLaunchButton(
                game = game,
                isSteamInstalled = isSteamInstalled,
                onDirectLaunch = onLaunchGame,
                onSteamLaunch = onLaunchViaSteam,
                onOpenSteamClient = onOpenSteamClient,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            )

            // ゲーム情報カード
            InfoCard(
                title = "ゲーム情報",
                items = listOf(
                    "プレイ時間" to game.playTimeFormatted,
                    "最終プレイ日時" to game.lastPlayedFormatted,
                    "ソース" to when (game.source) {
                        com.steamdeck.mobile.domain.model.GameSource.STEAM -> "Steam"
                        com.steamdeck.mobile.domain.model.GameSource.IMPORTED -> "インポート"
                    }
                )
            )

            // ファイルパスカード
            InfoCard(
                title = "ファイルパス",
                items = listOf(
                    "実行ファイル" to game.executablePath,
                    "インストールパス" to game.installPath
                )
            )

            if (game.steamAppId != null) {
                InfoCard(
                    title = "Steam情報",
                    items = listOf(
                        "Steam App ID" to game.steamAppId.toString()
                    )
                )
            }
        }
    }
}

/**
 * 情報カード - BackboneOne風デザイン
 */
@Composable
fun InfoCard(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    gameName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = "警告") },
        title = { Text("ゲームを削除") },
        text = { Text("「$gameName」を削除してもよろしいですか?\nこの操作は取り消せません。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun LaunchingDialog() {
    AlertDialog(
        onDismissRequest = {}, // Non-dismissible
        icon = { CircularProgressIndicator() },
        title = { Text("ゲームを起動中...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("初回起動時はWinlatorの初期化に2-3分かかる場合があります。")
                Text("しばらくお待ちください。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {} // No button, non-dismissible
    )
}

@Composable
fun LaunchErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = "情報") },
        title = { Text("ゲームを起動できません") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "読み込み中...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorContent(
    message: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "エラー",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "エラーが発生しました",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onNavigateBack) {
                Text("戻る")
            }
        }
    }
}

/**
 * スプリット起動ボタン
 *
 * Steam-first ロジック: Steamがインストールされていて、ゲームがSteam App IDを持つ場合は Steam 経由で起動
 */
@Composable
fun SplitLaunchButton(
    game: Game,
    isSteamInstalled: Boolean,
    onDirectLaunch: () -> Unit,
    onSteamLaunch: () -> Unit,
    onOpenSteamClient: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdownMenu by remember { mutableStateOf(false) }
    val isEnabled = game.executablePath.isNotBlank()

    // Steam-first ロジック: Steamインストール済み & Steam App ID がある場合はSteam優先
    val shouldUseSteam = isSteamInstalled && game.steamAppId != null

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // プライマリボタン (80%)
        Button(
            onClick = {
                if (shouldUseSteam) {
                    onSteamLaunch()
                } else {
                    onDirectLaunch()
                }
            },
            modifier = Modifier.weight(0.8f),
            enabled = isEnabled,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                bottomStart = 20.dp,
                topEnd = 0.dp,
                bottomEnd = 0.dp
            )
        ) {
            Icon(
                imageVector = if (shouldUseSteam) Icons.Default.SportsEsports else Icons.Default.PlayArrow,
                contentDescription = if (shouldUseSteam) "Steam起動" else "直接起動"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (shouldUseSteam) "Steamで起動" else "ゲームを起動")
        }

        // ドロップダウンメニューボタン (20%)
        Button(
            onClick = { showDropdownMenu = true },
            modifier = Modifier.weight(0.2f),
            enabled = isEnabled,
            shape = RoundedCornerShape(
                topStart = 0.dp,
                bottomStart = 0.dp,
                topEnd = 20.dp,
                bottomEnd = 20.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "起動オプション"
            )
        }

    }

    // ドロップダウンメニュー（Row の外側に配置）
    Box {
        DropdownMenu(
            expanded = showDropdownMenu,
            onDismissRequest = { showDropdownMenu = false }
        ) {
                // 直接起動 (Winlator)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("直接起動 (Winlator)")
                        }
                    },
                    onClick = {
                        showDropdownMenu = false
                        onDirectLaunch()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = "直接起動")
                    },
                    enabled = isEnabled
                )

                HorizontalDivider()

                // Steam経由で起動
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Steam経由で起動")
                            if (!isSteamInstalled) {
                                Text(
                                    text = "(未インストール)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (game.steamAppId == null) {
                                Text(
                                    text = "(Steam App ID未設定)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    onClick = {
                        showDropdownMenu = false
                        if (isSteamInstalled && game.steamAppId != null) {
                            onSteamLaunch()
                        } else {
                            onNavigateToSettings()
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Default.SportsEsports, contentDescription = "Steam起動")
                    },
                    enabled = isSteamInstalled && game.steamAppId != null && isEnabled
                )

                // Steam Clientを開く
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Steam Clientを開く")
                            if (!isSteamInstalled) {
                                Text(
                                    text = "(未インストール)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    onClick = {
                        showDropdownMenu = false
                        if (isSteamInstalled) {
                            onOpenSteamClient()
                        } else {
                            onNavigateToSettings()
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Default.SportsEsports, contentDescription = "Steam Client")
                    },
                    enabled = isSteamInstalled && isEnabled
                )

                HorizontalDivider()

            // 設定を開く
            DropdownMenuItem(
                text = { Text("設定を開く") },
                onClick = {
                    showDropdownMenu = false
                    onNavigateToSettings()
                },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = "設定")
                }
            )
        }
    }
}

/**
 * Steam起動エラーダイアログ
 */
@Composable
fun SteamLaunchErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "エラー",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Steam起動エラー")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(message)

                if (message.contains("インストール") || message.contains("コンテナ")) {
                    Text(
                        text = "\n💡 設定画面からSteam Clientをインストールするか、Winlatorコンテナを設定してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (message.contains("インストール") || message.contains("コンテナ")) {
                FilledTonalButton(onClick = onNavigateToSettings) {
                    Text("設定を開く")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
