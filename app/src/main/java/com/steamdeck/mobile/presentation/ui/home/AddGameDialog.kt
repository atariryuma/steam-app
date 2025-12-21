package com.steamdeck.mobile.presentation.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.steamdeck.mobile.R

// Dialog UI constants for maintainability
private object AddGameDialogDefaults {
    val CardElevation = 2.dp
    val CardPadding = 20.dp
    val PathDisplayMaxLength = 40
}

/**
 * Add Game Dialog - BackboneOne-style fullscreen dialog
 *
 * Landscape optimized:
 * - Fullscreen Dialog (usePlatformDefaultWidth = false)
 * - 2-column layout (landscape left/right split)
 * - Scrollable content
 *
 * Best Practices:
 * - Clear state ownership (parent manages selection paths, child manages display state only)
 * - Derived state for path display management
 * - Material3 Card design: elevation 2dp, padding 20dp
 * - String resources for all user-facing text
 *
 * @param onDismiss Dialog close callback
 * @param onConfirm Game info confirmation (name, executablePath, installPath)
 * @param onSelectExecutable Executable selection button pressed
 * @param onSelectInstallFolder Installation folder selection button pressed
 * @param selectedExecutablePath Selected executable file path (passed from parent, immutable)
 * @param selectedInstallPath Selected installation path (passed from parent, immutable)
 * @param initialGameName Auto-suggested game name from filename (optional)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameDialog(
 onDismiss: () -> Unit,
 onConfirm: (name: String, executablePath: String, installPath: String) -> Unit,
 onSelectExecutable: () -> Unit = {},
 onSelectInstallFolder: () -> Unit = {},
 selectedExecutablePath: String = "",
 selectedInstallPath: String = "",
 initialGameName: String = ""
) {
 // Dialog internal state
 var gameName by remember(initialGameName) { mutableStateOf(initialGameName) }
 var showError by remember { mutableStateOf(false) }

 // Display path strings (Derived state)
 val displayExecutablePath = remember(selectedExecutablePath) {
  if (selectedExecutablePath.isNotBlank()) {
   if (selectedExecutablePath.startsWith("content://")) {
    selectedExecutablePath.substringAfterLast("/")
     .take(AddGameDialogDefaults.PathDisplayMaxLength)
   } else {
    selectedExecutablePath
   }
  } else {
   ""
  }
 }

 val displayInstallPath = remember(selectedInstallPath) {
  if (selectedInstallPath.isNotBlank()) {
   if (selectedInstallPath.startsWith("content://")) {
    selectedInstallPath.substringAfterLast("/")
     .take(AddGameDialogDefaults.PathDisplayMaxLength)
   } else {
    selectedInstallPath
   }
  } else {
   ""
  }
 }

 // Fullscreen Dialog (landscape support)
 Dialog(
  onDismissRequest = onDismiss,
  properties = DialogProperties(
   usePlatformDefaultWidth = false, // Enable fullscreen
   dismissOnBackPress = true,
   dismissOnClickOutside = false
  )
 ) {
  Surface(
   modifier = Modifier.fillMaxSize(),
   color = MaterialTheme.colorScheme.background
  ) {
   Scaffold(
    topBar = {
     TopAppBar(
      title = {
       Text(
        text = stringResource(R.string.dialog_add_game_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
       )
      },
      navigationIcon = {
       IconButton(onClick = onDismiss) {
        Icon(
         imageVector = Icons.AutoMirrored.Filled.ArrowBack,
         contentDescription = stringResource(R.string.content_desc_back)
        )
       }
      },
      actions = {
       // Add button
       Button(
        onClick = {
         if (gameName.isNotBlank() &&
          selectedExecutablePath.isNotBlank()) {
          // Install path is optional - will be auto-derived from executable path if not provided
          onConfirm(gameName, selectedExecutablePath, selectedInstallPath)
         } else {
          showError = true
         }
        },
        modifier = Modifier.padding(end = 8.dp)
       ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.dialog_add_game_button))
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
    // Content area (scrollable)
    Column(
     modifier = Modifier
      .fillMaxSize()
      .padding(paddingValues)
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
     verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
     // Game Name input card
     Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
       containerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      shape = MaterialTheme.shapes.large,
      elevation = CardDefaults.cardElevation(
       defaultElevation = AddGameDialogDefaults.CardElevation
      )
     ) {
      Column(
       modifier = Modifier
        .fillMaxWidth()
        .padding(AddGameDialogDefaults.CardPadding),
       verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
       Text(
        text = stringResource(R.string.dialog_add_game_name_label),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
       )

       OutlinedTextField(
        value = gameName,
        onValueChange = {
         gameName = it
         showError = false
        },
        placeholder = { Text(stringResource(R.string.dialog_add_game_name_placeholder)) },
        isError = showError && gameName.isBlank(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
       )
      }
     }

     // File selection card
     Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
       containerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      shape = MaterialTheme.shapes.large,
      elevation = CardDefaults.cardElevation(
       defaultElevation = AddGameDialogDefaults.CardElevation
      )
     ) {
      Column(
       modifier = Modifier
        .fillMaxWidth()
        .padding(AddGameDialogDefaults.CardPadding),
       verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
       Text(
        text = stringResource(R.string.dialog_add_game_executable_label),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
       )

       OutlinedTextField(
        value = displayExecutablePath,
        onValueChange = { },
        placeholder = {
         Text(stringResource(R.string.dialog_add_game_executable_placeholder))
        },
        isError = showError && selectedExecutablePath.isBlank(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        readOnly = true,
        trailingIcon = {
         IconButton(onClick = onSelectExecutable) {
          Icon(
           Icons.Default.Folder,
           contentDescription = stringResource(R.string.content_desc_select_file)
          )
         }
        }
       )
      }
     }

     // Folder selection card
     Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
       containerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      shape = MaterialTheme.shapes.large,
      elevation = CardDefaults.cardElevation(
       defaultElevation = AddGameDialogDefaults.CardElevation
      )
     ) {
      Column(
       modifier = Modifier
        .fillMaxWidth()
        .padding(AddGameDialogDefaults.CardPadding),
       verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
       Text(
        text = stringResource(R.string.dialog_add_game_install_label),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
       )

       OutlinedTextField(
        value = displayInstallPath,
        onValueChange = { },
        placeholder = {
         Text(stringResource(R.string.dialog_add_game_install_placeholder))
        },
        isError = false,  // Install path is optional
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        readOnly = true,
        trailingIcon = {
         IconButton(onClick = onSelectInstallFolder) {
          Icon(
           Icons.Default.Folder,
           contentDescription = stringResource(R.string.content_desc_select_folder)
          )
         }
        }
       )
      }
     }

     // Error message
     if (showError) {
      Card(
       modifier = Modifier.fillMaxWidth(),
       colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer
       ),
       shape = MaterialTheme.shapes.large,
       elevation = CardDefaults.cardElevation(
        defaultElevation = AddGameDialogDefaults.CardElevation
       )
      ) {
       Text(
        text = stringResource(R.string.dialog_add_game_error),
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(AddGameDialogDefaults.CardPadding)
       )
      }
     }

     // Notes card
     Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
       containerColor = MaterialTheme.colorScheme.secondaryContainer
      ),
      shape = MaterialTheme.shapes.large,
      elevation = CardDefaults.cardElevation(
       defaultElevation = AddGameDialogDefaults.CardElevation
      )
     ) {
      Column(
       modifier = Modifier
        .fillMaxWidth()
        .padding(AddGameDialogDefaults.CardPadding),
       verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
       Text(
        text = stringResource(R.string.dialog_add_game_notes_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer
       )
       Text(
        text = stringResource(R.string.dialog_add_game_notes_content),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer
       )
      }
     }
    }
   }
  }
 }
}
