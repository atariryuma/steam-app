package com.steamdeck.mobile.presentation.ui.container

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.domain.emulator.EmulatorContainer
import com.steamdeck.mobile.presentation.viewmodel.ContainerUiState
import com.steamdeck.mobile.presentation.viewmodel.ContainerViewModel

/**
 * Winlatorコンテナ管理画面 - BackboneOne風デザイン
 *
 * Best Practices:
 * - No TopAppBar for immersive full-screen experience
 * - Custom header with back button and add button
 * - Material3 Card styling: elevation 2dp, padding 20dp, shapes.large
 * - LazyColumn with 24dp contentPadding, 16dp item spacing
 *
 * 機能:
 * - コンテナ一覧表示
 * - 新規コンテナ作成
 * - コンテナ編集
 * - コンテナ削除
 */
@Composable
fun ContainerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ContainerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedContainer by remember { mutableStateOf<EmulatorContainer?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Load containers on first composition
    LaunchedEffect(Unit) {
        viewModel.loadContainers()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // BackboneOne風カスタムヘッダー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "戻る",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "コンテナ管理",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = { showCreateDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新規作成",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // コンテンツエリア
        when (val state = uiState) {
            is ContainerUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ContainerUiState.Success -> {
                if (state.containers.isEmpty()) {
                    EmptyContainersPlaceholder(
                        onCreateClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = state.containers,
                            key = { it.id }
                        ) { container ->
                            ContainerItem(
                                container = container,
                                onClick = { selectedContainer = container },
                                onDeleteClick = {
                                    selectedContainer = container
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }

            is ContainerUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
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
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.loadContainers() }) {
                            Text("再試行")
                        }
                    }
                }
            }
        }

        // Create container dialog
        if (showCreateDialog) {
            CreateContainerDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    viewModel.createContainer(name)
                    showCreateDialog = false
                }
            )
        }

        // Delete confirmation dialog
        if (showDeleteDialog && selectedContainer != null) {
            DeleteContainerDialog(
                containerName = selectedContainer!!.name,
                onDismiss = {
                    showDeleteDialog = false
                    selectedContainer = null
                },
                onConfirm = {
                    viewModel.deleteContainer(selectedContainer!!.id)
                    showDeleteDialog = false
                    selectedContainer = null
                }
            )
        }
    }
}

@Composable
private fun EmptyContainersPlaceholder(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "空",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "コンテナがありません",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "コンテナを作成してゲームを実行できます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCreateClick) {
                Icon(Icons.Default.Add, "作成")
                Spacer(modifier = Modifier.width(8.dp))
                Text("コンテナを作成")
            }
        }
    }
}

@Composable
private fun ContainerItem(
    container: EmulatorContainer,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = container.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "サイズ: ${formatSize(container.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(container.lastUsedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${container.config.screenWidth}x${container.config.screenHeight}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CreateContainerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var containerName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Add, "新規作成") },
        title = { Text("新しいコンテナを作成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "コンテナ名を入力してください",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = containerName,
                    onValueChange = { containerName = it },
                    label = { Text("コンテナ名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(containerName) },
                enabled = containerName.isNotBlank()
            ) {
                Text("作成")
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
private fun DeleteContainerDialog(
    containerName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "警告",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("コンテナを削除") },
        text = {
            Text("「$containerName」を削除しますか？\n\nこの操作は取り消せません。")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
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

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        days == 0L -> "今日"
        days == 1L -> "昨日"
        days < 7 -> "${days}日前"
        days < 30 -> "${days / 7}週間前"
        else -> "${days / 30}ヶ月前"
    }
}
