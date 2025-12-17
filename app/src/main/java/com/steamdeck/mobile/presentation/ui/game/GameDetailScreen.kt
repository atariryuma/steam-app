package com.steamdeck.mobile.presentation.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.presentation.viewmodel.GameDetailUiState
import com.steamdeck.mobile.presentation.viewmodel.GameDetailViewModel
import com.steamdeck.mobile.presentation.viewmodel.LaunchState

/**
 * ゲーム詳細画面
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLaunchErrorDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var launchErrorMessage by remember { mutableStateOf("") }

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
        if (launchState is com.steamdeck.mobile.presentation.viewmodel.LaunchState.Error) {
            launchErrorMessage = (launchState as com.steamdeck.mobile.presentation.viewmodel.LaunchState.Error).message
            showLaunchErrorDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ゲーム詳細") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    when (val state = uiState) {
                        is GameDetailUiState.Success -> {
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "ゲーム設定")
                            }
                            IconButton(
                                onClick = { viewModel.toggleFavorite(state.game.id, !state.game.isFavorite) }
                            ) {
                                Icon(
                                    imageVector = if (state.game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "お気に入り",
                                    tint = if (state.game.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除")
                            }
                        }
                        else -> {}
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is GameDetailUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }
            is GameDetailUiState.Success -> {
                GameDetailContent(
                    game = state.game,
                    onLaunchGame = { viewModel.launchGame(gameId) },
                    onDownloadGame = { showDownloadDialog = true },
                    modifier = Modifier.padding(paddingValues)
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

                // ダウンロードダイアログ
                if (showDownloadDialog) {
                    SteamDownloadDialog(
                        game = state.game,
                        onDismiss = { showDownloadDialog = false },
                        onNavigateToImport = {
                            showDownloadDialog = false
                            onNavigateToImport()
                        },
                        onStartDownload = { appId ->
                            viewModel.startDownload(appId)
                        }
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
            }
            is GameDetailUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.padding(paddingValues)
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
    onLaunchGame: () -> Unit,
    onDownloadGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ゲームバナー
        AsyncImage(
            model = game.bannerPath ?: game.iconPath,
            contentDescription = game.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ゲームタイトル
            Text(
                text = game.name,
                style = MaterialTheme.typography.headlineMedium
            )

            // ダウンロードボタン（Steamゲーム & 未ダウンロードの場合のみ表示）
            if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM &&
                game.executablePath.isBlank()
            ) {
                Button(
                    onClick = onDownloadGame,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = "ダウンロード")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ゲームをダウンロード")
                }
            }

            // 起動ボタン
            Button(
                onClick = onLaunchGame,
                modifier = Modifier.fillMaxWidth(),
                enabled = game.executablePath.isNotBlank() // 実行ファイルがある場合のみ有効
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "起動")
                Spacer(modifier = Modifier.width(8.dp))
                Text("ゲームを起動")
            }

            HorizontalDivider()

            // ゲーム情報
            InfoSection(
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

            HorizontalDivider()

            // ファイルパス
            InfoSection(
                title = "ファイルパス",
                items = listOf(
                    "実行ファイル" to game.executablePath,
                    "インストールパス" to game.installPath
                )
            )

            if (game.steamAppId != null) {
                HorizontalDivider()
                InfoSection(
                    title = "Steam情報",
                    items = listOf(
                        "Steam App ID" to game.steamAppId.toString()
                    )
                )
            }
        }
    }
}

@Composable
fun InfoSection(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
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
                    style = MaterialTheme.typography.bodyMedium
                )
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
        CircularProgressIndicator()
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
