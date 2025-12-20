package com.steamdeck.mobile.presentation.ui.download

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.R
import com.steamdeck.mobile.data.local.database.entity.DownloadEntity
import com.steamdeck.mobile.data.local.database.entity.DownloadStatus
import com.steamdeck.mobile.presentation.viewmodel.DownloadViewModel

/**
 * Download Management Screen - BackboneOne-style design
 *
 * Best Practices:
 * - No TopAppBar for immersive full-screen experience
 * - Material3 LinearProgressIndicator for determinate progress
 * - LazyColumn for efficient list rendering
 * - Card elevation: 2dp, padding: 20dp
 * - Steam color scheme with Material3
 *
 * References:
 * - https://developer.android.com/develop/ui/compose/lists
 * - https://m3.material.io/develop/android/jetpack-compose
 */
@Composable
fun DownloadScreen(
 viewModel: DownloadViewModel = hiltViewModel(),
 onNavigateBack: () -> Unit
) {
 val downloads by viewModel.downloads.collectAsState()
 val activeDownloads by viewModel.activeDownloads.collectAsState()

 Column(modifier = Modifier.fillMaxSize()) {
  // BackboneOne-style custom header
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
      contentDescription = "Back",
      tint = MaterialTheme.colorScheme.primary
     )
    }
    Text(
     text = stringResource(R.string.drawer_item_downloads),
     style = MaterialTheme.typography.headlineMedium,
     fontWeight = FontWeight.Bold,
     color = MaterialTheme.colorScheme.primary
    )
   }

   // Clear completed button
   IconButton(
    onClick = { viewModel.clearCompleted() },
    enabled = downloads.any { it.status == DownloadStatus.COMPLETED }
   ) {
    Icon(
     imageVector = Icons.Default.Clear,
     contentDescription = "Clear Completed",
     tint = MaterialTheme.colorScheme.onSurface
    )
   }
  }

  // Active downloads count display
  if (activeDownloads > 0) {
   Column(modifier = Modifier.fillMaxWidth()) {
    LinearProgressIndicator(
     modifier = Modifier.fillMaxWidth()
    )
    Text(
     text = stringResource(R.string.download_active_count, activeDownloads),
     style = MaterialTheme.typography.labelMedium,
     color = MaterialTheme.colorScheme.primary,
     modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
   }
  }

  // Download list
  if (downloads.isEmpty()) {
   EmptyDownloadsPlaceholder()
  } else {
   LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
   ) {
    items(
     items = downloads,
     key = { it.id }
    ) { download ->
     DownloadItem(
      download = download,
      onPause = { viewModel.pauseDownload(download.id) },
      onResume = { viewModel.resumeDownload(download.id) },
      onCancel = { viewModel.cancelDownload(download.id) },
      onRetry = { viewModel.retryDownload(download.id) }
     )
    }
   }
  }
 }
}

@Composable
private fun EmptyDownloadsPlaceholder() {
 Box(
  modifier = Modifier.fillMaxSize(),
  contentAlignment = Alignment.Center
 ) {
  Column(
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Icon(
    imageVector = Icons.Default.Info,
    contentDescription = "Info",
    modifier = Modifier.size(64.dp),
    tint = MaterialTheme.colorScheme.outline
   )
   Text(
    text = stringResource(R.string.download_empty_title),
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.outline
   )
  }
 }
}

/**
 * Download item - BackboneOne-style card
 */
@Composable
private fun DownloadItem(
 download: DownloadEntity,
 onPause: () -> Unit,
 onResume: () -> Unit,
 onCancel: () -> Unit,
 onRetry: () -> Unit
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
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   // File name & Status
   Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
   ) {
    Column(modifier = Modifier.weight(1f)) {
     Text(
      text = download.fileName,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
     )
     Spacer(modifier = Modifier.height(4.dp))
     Text(
      text = formatDownloadSize(download.downloadedBytes, download.totalBytes),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }

    // ステータスアイコン
    DownloadStatusIcon(download.status)
   }

   // プログレスバー
   when (download.status) {
    DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED -> {
     val progress = if (download.totalBytes > 0) {
      (download.downloadedBytes.toFloat() / download.totalBytes.toFloat()).coerceIn(0f, 1f)
     } else {
      0f
     }

     if (!progress.isNaN()) {
      LinearProgressIndicator(
       progress = { progress },
       modifier = Modifier.fillMaxWidth(),
      )

      Row(
       modifier = Modifier.fillMaxWidth(),
       horizontalArrangement = Arrangement.SpaceBetween
      ) {
       Text(
        text = "${(progress * 100).toInt()}%",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
       )
       if (download.status == DownloadStatus.DOWNLOADING) {
        Text(
         text = formatSpeed(download.speedBytesPerSecond),
         style = MaterialTheme.typography.bodyMedium,
         color = MaterialTheme.colorScheme.onSurfaceVariant
        )
       }
      }
     }
    }

    DownloadStatus.PENDING -> {
     LinearProgressIndicator(
      modifier = Modifier.fillMaxWidth()
     )
    }

    else -> {
     // COMPLETED, FAILED, CANCELLED - no progress bar
    }
   }

   // Action buttons
   Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically
   ) {
    when (download.status) {
     DownloadStatus.DOWNLOADING -> {
      IconButton(onClick = onPause) {
       Icon(Icons.Default.Clear, contentDescription = "Pause")
      }
      IconButton(onClick = onCancel) {
       Icon(Icons.Default.Close, contentDescription = "cancel")
      }
     }

     DownloadStatus.PAUSED -> {
      IconButton(onClick = onResume) {
       Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
      }
      IconButton(onClick = onCancel) {
       Icon(Icons.Default.Close, contentDescription = "cancel")
      }
     }

     DownloadStatus.FAILED -> {
      TextButton(onClick = onRetry) {
       Icon(Icons.Default.Refresh, contentDescription = "retry")
       Spacer(modifier = Modifier.width(4.dp))
       Text("Retry")
      }
      IconButton(onClick = onCancel) {
       Icon(Icons.Default.Delete, contentDescription = "delete")
      }
     }

     DownloadStatus.COMPLETED -> {
      Icon(
       Icons.Default.CheckCircle,
       contentDescription = "Complete",
       tint = MaterialTheme.colorScheme.primary
      )
      IconButton(onClick = onCancel) {
       Icon(Icons.Default.Delete, contentDescription = "delete")
      }
     }

     else -> {
      IconButton(onClick = onCancel) {
       Icon(Icons.Default.Delete, contentDescription = "delete")
      }
     }
    }
   }

   // Error message
   if (download.status == DownloadStatus.FAILED && download.errorMessage != null) {
    Text(
     text = stringResource(R.string.download_error_prefix, download.errorMessage),
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.error
    )
   }
  }
 }
}

@Composable
private fun DownloadStatusIcon(status: DownloadStatus) {
 when (status) {
  DownloadStatus.PENDING -> Icon(
   Icons.Default.Info,
   contentDescription = "Waiting",
   tint = MaterialTheme.colorScheme.outline
  )

  DownloadStatus.DOWNLOADING -> Icon(
   Icons.Default.ArrowForward,
   contentDescription = "Downloading",
   tint = MaterialTheme.colorScheme.primary
  )

  DownloadStatus.PAUSED -> Icon(
   Icons.Default.Clear,
   contentDescription = "Pause",
   tint = MaterialTheme.colorScheme.secondary
  )

  DownloadStatus.COMPLETED -> Icon(
   Icons.Default.CheckCircle,
   contentDescription = "Complete",
   tint = MaterialTheme.colorScheme.primary
  )

  DownloadStatus.FAILED -> Icon(
   Icons.Default.Warning,
   contentDescription = "Failed",
   tint = MaterialTheme.colorScheme.error
  )

  DownloadStatus.CANCELLED -> Icon(
   Icons.Default.Close,
   contentDescription = "cancel",
   tint = MaterialTheme.colorScheme.outline
  )
 }
}

private fun formatDownloadSize(downloaded: Long, total: Long): String {
 return "${formatBytes(downloaded)} / ${formatBytes(total)}"
}

private fun formatBytes(bytes: Long): String {
 return when {
  bytes < 1024 -> "$bytes B"
  bytes < 1024 * 1024 -> "${bytes / 1024} KB"
  bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
  else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
 }
}

private fun formatSpeed(bytesPerSecond: Long): String {
 return when {
  bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
  bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
  else -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
 }
}
