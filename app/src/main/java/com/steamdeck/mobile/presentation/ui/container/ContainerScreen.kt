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
 * Winlatorコンテナ管理画面 - BackboneOnestyle design
 *
 * Best Practices:
 * - No TopAppBar for immersive full-screen experience
 * - Custom header with back button and add button
 * - Material3 Card styling: elevation 2dp, padding 20dp, shapes.large
 * - LazyColumn with 24dp contentPadding, 16dp item spacing
 *
 * 機能:
 * - コンテナlist表示
 * - 新規コンテナcreate
 * - コンテナ編集
 * - コンテナdelete
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
  // BackboneOne風customヘッダー
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
      contentDescription = "return",
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
     contentDescription = "新規create",
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
       text = "エラー 発生しました",
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
       Text("retry")
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
  selectedContainer?.let { container ->
   if (showDeleteDialog) {
    DeleteContainerDialog(
     containerName = container.name,
     onDismiss = {
      showDeleteDialog = false
      selectedContainer = null
     },
     onConfirm = {
      viewModel.deleteContainer(container.id)
      showDeleteDialog = false
      selectedContainer = null
     }
    )
   }
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
    text = "コンテナ ありません",
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.outline
   )
   Text(
    text = "コンテナcreateしてgame実行 きます",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
   Button(onClick = onCreateClick) {
    Icon(Icons.Default.Add, "create")
    Spacer(modifier = Modifier.width(8.dp))
    Text("コンテナcreate")
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
     contentDescription = "delete",
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
  icon = { Icon(Icons.Default.Add, "新規create") },
  title = { Text("新しいコンテナcreate") },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
     text = "コンテナ名入力please",
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
    Text("create")
   }
  },
  dismissButton = {
   TextButton(onClick = onDismiss) {
    Text("cancel")
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
  title = { Text("コンテナdelete") },
  text = {
   Text("「$containerName」deleteしますか？\n\nこ 操作 取り消せません。")
  },
  confirmButton = {
   Button(
    onClick = onConfirm,
    colors = ButtonDefaults.buttonColors(
     containerColor = MaterialTheme.colorScheme.error
    )
   ) {
    Text("delete")
   }
  },
  dismissButton = {
   TextButton(onClick = onDismiss) {
    Text("cancel")
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
