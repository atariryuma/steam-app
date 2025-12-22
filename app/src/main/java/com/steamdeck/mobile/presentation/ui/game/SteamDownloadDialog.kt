package com.steamdeck.mobile.presentation.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steamdeck.mobile.R
import com.steamdeck.mobile.domain.model.Game

/**
 * Steam game download confirmation dialog
 *
 * Phase 5B implementation:
 * - Steam CDN direct download support
 * - Guide to file import as alternative
 * - Navigate to download progress display
 */
@Composable
fun SteamDownloadDialog(
 game: Game,
 onDismiss: () -> Unit,
 onNavigateToImport: () -> Unit,
 onStartDownload: (Long) -> Unit = {}
) {
 var showDownloadOption by remember { mutableStateOf(true) }
 if (showDownloadOption) {
  AlertDialog(
   onDismissRequest = onDismiss,
   icon = {
    Icon(
     imageVector = Icons.Default.CloudDownload,
     contentDescription = stringResource(R.string.content_desc_download),
     tint = MaterialTheme.colorScheme.primary
    )
   },
   title = {
    Text(
     text = "Steam game download",
     style = MaterialTheme.typography.titleLarge
    )
   },
   text = {
    Column(
     modifier = Modifier.fillMaxWidth(),
     verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
     Text(
      text = "${game.name}",
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Bold
     )

     Card(
      colors = CardDefaults.cardColors(
       containerColor = MaterialTheme.colorScheme.primaryContainer
      )
     ) {
      Column(
       modifier = Modifier.padding(16.dp),
       verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
       Text(
        text = "Download directly from Steam CDN",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
       )
       Text(
        text = "Experimental feature: Download game files from Steam CDN.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer
       )
       Text(
        text = "⚠️ Large downloads may occur. Wi-Fi connection recommended.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
       )
      }
     }

     HorizontalDivider()

     Text(
      text = "Alternative method:",
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold
     )

     Column(
      verticalArrangement = Arrangement.spacedBy(4.dp)
     ) {
      Text(
       text = "• Download from PC Steam → Transfer files",
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
      )
     }
    }
   },
   confirmButton = {
    Button(
     onClick = {
      game.steamAppId?.let { onStartDownload(it) }
      onDismiss()
     }
    ) {
     Icon(Icons.Default.CloudDownload, contentDescription = null)
     Spacer(modifier = Modifier.width(8.dp))
     Text("Start download")
    }
   },
   dismissButton = {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     TextButton(onClick = onNavigateToImport) {
      Text("Import file")
     }
     TextButton(onClick = onDismiss) {
      Text("Cancel")
     }
    }
   }
  )
 }
}
