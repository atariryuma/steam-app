package com.steamdeck.mobile.presentation.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.presentation.viewmodel.HomeUiState
import com.steamdeck.mobile.presentation.viewmodel.HomeViewModel

/**
 * ホーム画面（ゲームライブラリ）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGameClick: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showSearchBar by remember { mutableStateOf(false) }
    var showAddGameDialog by remember { mutableStateOf(false) }

    // ファイル選択状態
    // Note: Storage Access FrameworkではURIをそのまま使用します
    // ファイルパスに変換することは推奨されません（セキュリティ上の理由）
    var executableUri by remember { mutableStateOf<Uri?>(null) }
    var installFolderUri by remember { mutableStateOf<Uri?>(null) }

    // 実行ファイル選択用のランチャー
    val executableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // 永続的な読み取り権限を取得
                // これにより、アプリ再起動後もファイルにアクセス可能
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                executableUri = uri
            } catch (e: SecurityException) {
                // 権限取得に失敗した場合でも、URIは一時的に使用可能
                executableUri = uri
                android.util.Log.w("HomeScreen", "Could not take persistable permission", e)
            }
        }
    }

    // インストールフォルダ選択用のランチャー
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // フォルダに対する永続的な読み書き権限を取得
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                installFolderUri = uri
            } catch (e: SecurityException) {
                installFolderUri = uri
                android.util.Log.w("HomeScreen", "Could not take persistable permission", e)
            }
        }
    }

    Scaffold(
        topBar = {
            if (showSearchBar) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::searchGames,
                    onClose = {
                        showSearchBar = false
                        viewModel.searchGames("")
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("ゲームライブラリ") },
                    actions = {
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, contentDescription = "検索")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "更新")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "設定")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddGameDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "ゲーム追加")
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }
            is HomeUiState.Success -> {
                GameGrid(
                    games = state.games,
                    onGameClick = onGameClick,
                    onToggleFavorite = viewModel::toggleFavorite,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is HomeUiState.Empty -> {
                EmptyContent(modifier = Modifier.padding(paddingValues))
            }
            is HomeUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }

    // ゲーム追加ダイアログ
    if (showAddGameDialog) {
        AddGameDialog(
            onDismiss = {
                showAddGameDialog = false
                executableUri = null
                installFolderUri = null
            },
            onConfirm = { name, execPath, instPath ->
                viewModel.addGame(name, execPath, instPath)
                showAddGameDialog = false
                executableUri = null
                installFolderUri = null
            },
            onSelectExecutable = {
                // 実行ファイル選択（全てのファイルタイプ）
                executableLauncher.launch(arrayOf("*/*"))
            },
            onSelectInstallFolder = {
                // フォルダ選択
                folderLauncher.launch(null)
            },
            selectedExecutablePath = executableUri?.toString() ?: "",
            selectedInstallPath = installFolderUri?.toString() ?: ""
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("ゲームを検索...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "閉じる")
            }
        },
        modifier = modifier
    )
}

@Composable
fun GameGrid(
    games: List<Game>,
    onGameClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(games, key = { it.id }) { game ->
            GameCard(
                game = game,
                onClick = { onGameClick(game.id) },
                onToggleFavorite = { onToggleFavorite(game.id, !game.isFavorite) }
            )
        }
    }
}

@Composable
fun GameCard(
    game: Game,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // ゲームバナー
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                AsyncImage(
                    model = game.bannerPath ?: game.iconPath,
                    contentDescription = game.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // お気に入りアイコン
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "お気に入り",
                        tint = if (game.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ゲーム情報
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "プレイ時間: ${game.playTimeFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
fun EmptyContent(modifier: Modifier = Modifier) {
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
                contentDescription = "空のライブラリ",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "ゲームがありません",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "「+」ボタンからゲームを追加してください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
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
            Button(onClick = onRetry) {
                Text("再試行")
            }
        }
    }
}
