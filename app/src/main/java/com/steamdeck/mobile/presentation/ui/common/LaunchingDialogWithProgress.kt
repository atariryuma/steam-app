package com.steamdeck.mobile.presentation.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Launching dialog with progress indicator and timer
 *
 * Shows initialization progress for Winlator environment
 * Displays elapsed time and timeout countdown
 *
 * @param message Current status message
 * @param elapsedSeconds Elapsed seconds since launch started
 * @param timeoutSeconds Total timeout duration (default: 90s)
 */
@Composable
fun LaunchingDialogWithProgress(
 message: String,
 elapsedSeconds: Int,
 timeoutSeconds: Int = 90
) {
 AlertDialog(
  onDismissRequest = { /* Cannot be dismissed */ },
  title = { Text("Launching...") },
  text = {
   Column {
    Text(
     text = message,
     style = MaterialTheme.typography.bodyMedium
    )

    Spacer(modifier = Modifier.height(16.dp))

    LinearProgressIndicator(
     modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
     text = "$elapsedSeconds / $timeoutSeconds seconds",
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (elapsedSeconds < 60) {
     Spacer(modifier = Modifier.height(8.dp))
     Text(
      text = "First launch may take 30-60 seconds to initialize Winlator.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    } else {
     Spacer(modifier = Modifier.height(8.dp))
     Text(
      text = "Still initializing... This is taking longer than expected.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error
     )
    }
   }
  },
  confirmButton = { /* No button - cannot be dismissed */ }
 )
}
