package com.steamdeck.mobile.presentation.ui.container

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.R
import com.steamdeck.mobile.domain.emulator.EmulatorContainer
import com.steamdeck.mobile.presentation.ui.common.AnimationDefaults
import com.steamdeck.mobile.presentation.viewmodel.ContainerUiState
import com.steamdeck.mobile.presentation.viewmodel.ContainerViewModel

/**
 * Winlator Container Management Screen - Material3 design
 *
 * Best Practices:
 * - Material3 TopAppBar for consistency
 * - Scaffold structure
 * - Material3 Card styling: elevation 2dp, padding 20dp, shapes.large
 * - LazyColumn with 24dp contentPadding, 16dp item spacing
 *
 * Features:
 * - Display container list
 * - Create new container
 * - Edit container
 * - Delete container
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerScreen(
 onNavigateBack: () -> Unit,
 viewModel: ContainerViewModel = hiltViewModel()
) {
 val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 var showCreateDialog by remember { mutableStateOf(false) }
 var selectedContainer by remember { mutableStateOf<EmulatorContainer?>(null) }
 var showDeleteDialog by remember { mutableStateOf(false) }

 // Load containers on first composition
 LaunchedEffect(Unit) {
  viewModel.loadContainers()
 }

 Scaffold(
  topBar = {
   TopAppBar(
    title = {
     Text(
      text = "Container Management",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
     )
    },
    navigationIcon = {
     IconButton(onClick = onNavigateBack) {
      Icon(
       imageVector = Icons.AutoMirrored.Filled.ArrowBack,
       contentDescription = stringResource(R.string.content_desc_back)
      )
     }
    },
    actions = {
     IconButton(onClick = { showCreateDialog = true }) {
      Icon(
       imageVector = Icons.Default.Add,
       contentDescription = stringResource(R.string.content_desc_create)
      )
     }
    },
    colors = TopAppBarDefaults.topAppBarColors(
     containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
     titleContentColor = MaterialTheme.colorScheme.primary,
     navigationIconContentColor = MaterialTheme.colorScheme.onSurface
    )
   )
  }
 ) { paddingValues ->
  // Content area
  when (val state = uiState) {
   is ContainerUiState.Loading -> {
    Box(
     modifier = Modifier
      .fillMaxSize()
      .padding(paddingValues),
     contentAlignment = Alignment.Center
    ) {
     CircularProgressIndicator()
    }
   }

   is ContainerUiState.Creating -> {
    Box(
     modifier = Modifier
      .fillMaxSize()
      .padding(paddingValues),
     contentAlignment = Alignment.Center
    ) {
     Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
     ) {
      CircularProgressIndicator()
      Text(
       text = state.message,
       style = MaterialTheme.typography.bodyLarge,
       color = MaterialTheme.colorScheme.onSurface
      )
      Text(
       text = "Please wait...",
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
      )
     }
    }
   }

   is ContainerUiState.Success -> {
    if (state.containers.isEmpty()) {
     EmptyContainersPlaceholder(
      onCreateClick = { showCreateDialog = true },
      modifier = Modifier
       .fillMaxSize()
       .padding(paddingValues)
     )
    } else {
     LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
       start = 24.dp,
       end = 24.dp,
       top = 24.dp + paddingValues.calculateTopPadding(),
       bottom = 24.dp
      ),
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
     modifier = Modifier
      .fillMaxSize()
      .padding(paddingValues),
     contentAlignment = Alignment.Center
    ) {
     Column(
      modifier = Modifier.padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
     ) {
      Icon(
       imageVector = Icons.Default.Error,
       contentDescription = stringResource(R.string.content_desc_error),
       modifier = Modifier.size(64.dp),
       tint = MaterialTheme.colorScheme.error
      )
      Text(
       text = "An error occurred",
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
       Text("Retry")
      }
     }
    }
   }
  }

  // Create container dialog
  AnimatedVisibility(
   visible = showCreateDialog,
   enter = AnimationDefaults.DialogEnter,
   exit = AnimationDefaults.DialogExit
  ) {
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
   AnimatedVisibility(
    visible = showDeleteDialog,
    enter = AnimationDefaults.DialogEnter,
    exit = AnimationDefaults.DialogExit
   ) {
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
    contentDescription = stringResource(R.string.content_desc_empty),
    modifier = Modifier.size(64.dp),
    tint = MaterialTheme.colorScheme.outline
   )
   Text(
    text = stringResource(R.string.container_empty_title),
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.outline
   )
   Text(
    text = stringResource(R.string.container_empty_message),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
   Button(onClick = onCreateClick) {
    Icon(Icons.Default.Add, stringResource(R.string.content_desc_create))
    Spacer(modifier = Modifier.width(8.dp))
    Text(stringResource(R.string.container_create_button))
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
      text = "Size: ${formatSize(container.sizeBytes)}",
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
     contentDescription = stringResource(R.string.content_desc_delete),
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
  icon = { Icon(Icons.Default.Add, stringResource(R.string.content_desc_create)) },
  title = { Text(stringResource(R.string.container_create_dialog_title)) },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
     text = stringResource(R.string.container_create_dialog_message),
     style = MaterialTheme.typography.bodyMedium
    )
    OutlinedTextField(
     value = containerName,
     onValueChange = { containerName = it },
     label = { Text(stringResource(R.string.container_name_label)) },
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
    Text(stringResource(R.string.button_create))
   }
  },
  dismissButton = {
   TextButton(onClick = onDismiss) {
    Text(stringResource(R.string.button_cancel))
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
    contentDescription = stringResource(R.string.content_desc_warning),
    tint = MaterialTheme.colorScheme.error
   )
  },
  title = { Text("Delete Container") },
  text = {
   Text("Delete \"$containerName\"?\n\nThis action cannot be undone.")
  },
  confirmButton = {
   Button(
    onClick = onConfirm,
    colors = ButtonDefaults.buttonColors(
     containerColor = MaterialTheme.colorScheme.error
    )
   ) {
    Text("Delete")
   }
  },
  dismissButton = {
   TextButton(onClick = onDismiss) {
    Text("Cancel")
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
  days == 0L -> "Today"
  days == 1L -> "Yesterday"
  days < 7 -> "${days} days ago"
  days < 30 -> "${days / 7} weeks ago"
  else -> "${days / 30} months ago"
 }
}
