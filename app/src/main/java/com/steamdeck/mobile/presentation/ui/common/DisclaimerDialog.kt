package com.steamdeck.mobile.presentation.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Disclaimer dialog
 *
 * Warning to users about Steam ToS violation risks
 *
 * Best Practice:
 * - Display on first launch (flag managed via DataStore)
 * - Close app if user disagrees
 * - Scrollable for important legal information
 */
@Composable
fun DisclaimerDialog(
 onAccept: () -> Unit,
 onDecline: () -> Unit,
 modifier: Modifier = Modifier
) {
 AlertDialog(
  onDismissRequest = { /* Tap outside dialog disabled */ },
  icon = {
   Icon(
    imageVector = Icons.Default.Warning,
    contentDescription = null,
    tint = Color(0xFFFF9800) // Orange warning
   )
  },
  title = {
   Text(
    text = "Important Legal Notice",
    style = MaterialTheme.typography.headlineSmall.copy(
     fontWeight = FontWeight.Bold
    )
   )
  },
  text = {
   Column(
    modifier = Modifier
     .fillMaxWidth()
     .verticalScroll(rememberScrollState())
   ) {
    Text(
     text = "This application is not affiliated with, endorsed, or sponsored by Valve Corporation.",
     style = MaterialTheme.typography.bodyMedium.copy(
      fontWeight = FontWeight.Bold
     ),
     color = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
     text = "Steam®, the Steam logo, Steam Deck™, and related marks are " +
       "trademarks and/or registered trademarks of Valve Corporation in the " +
       "United States and other countries.",
     style = MaterialTheme.typography.bodySmall
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
     text = "By using this app, you acknowledge the following:",
     style = MaterialTheme.typography.bodyMedium.copy(
      fontWeight = FontWeight.Bold
     )
    )

    Spacer(modifier = Modifier.height(8.dp))

    val risks = listOf(
     "This is an unofficial third-party application",
     "This may violate Steam Terms of Service",
     "Your Steam account may be suspended or banned",
     "All risks are assumed by the user"
    )

    risks.forEach { risk ->
     Text(
      text = "• $risk",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(start = 8.dp, top = 4.dp)
     )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
     text = "This app is provided \"as is\" without any warranty.",
     style = MaterialTheme.typography.bodySmall.copy(
      fontWeight = FontWeight.Bold
     )
    )
   }
  },
  confirmButton = {
   Button(
    onClick = onAccept,
    colors = ButtonDefaults.buttonColors(
     containerColor = MaterialTheme.colorScheme.error
    )
   ) {
    Text("I Understand and Accept the Risks")
   }
  },
  dismissButton = {
   TextButton(onClick = onDecline) {
    Text("Disagree")
   }
  },
  modifier = modifier
 )
}
