package com.steamdeck.mobile.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steamdeck.mobile.R
import com.steamdeck.mobile.domain.model.*
import com.steamdeck.mobile.presentation.viewmodel.ControllerUiState
import com.steamdeck.mobile.presentation.viewmodel.ControllerViewModel

/**
 * Controller settings screen - BackboneOnestyle design
 *
 * Best Practices:
 * - No TopAppBar for immersive full-screen experience
 * - Custom header with back button and refresh action
 * - Material3 Card styling: elevation 2dp, padding 20dp, shapes.large
 * - LazyColumn with 24dp contentPadding, 16dp item spacing
 *
 * Displays connected controllers, button mapping configuration, and profile management.
 */
@Composable
fun ControllerSettingsScreen(
 viewModel: ControllerViewModel = hiltViewModel(),
 onBackClick: () -> Unit = {}
) {
 val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 val connectedControllers by viewModel.connectedControllers.collectAsStateWithLifecycle()
 val activeController by viewModel.activeController.collectAsStateWithLifecycle()
 val profiles by viewModel.profiles.collectAsStateWithLifecycle()
 val editingProfile by viewModel.editingProfile.collectAsStateWithLifecycle()
 val joystickState by viewModel.joystickState.collectAsStateWithLifecycle()

 var showDeleteConfirmation by remember { mutableStateOf<ControllerProfile?>(null) }

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
    IconButton(onClick = onBackClick) {
     Icon(
      imageVector = Icons.Default.ArrowBack,
      contentDescription = "return",
      tint = MaterialTheme.colorScheme.primary
     )
    }
    Text(
     text = "controllersettings",
     style = MaterialTheme.typography.headlineMedium,
     fontWeight = FontWeight.Bold,
     color = MaterialTheme.colorScheme.primary
    )
   }

   IconButton(onClick = { viewModel.refreshControllers() }) {
    Icon(
     imageVector = Icons.Default.Refresh,
     contentDescription = "Update",
     tint = MaterialTheme.colorScheme.onSurface
    )
   }
  }

  // コンテンツエリア
  Box(modifier = Modifier.fillMaxSize()) {
   when {
    uiState is ControllerUiState.Loading -> {
     CircularProgressIndicator(
      modifier = Modifier.align(Alignment.Center)
     )
    }
    uiState is ControllerUiState.Error -> {
     ErrorMessage(
      message = (uiState as ControllerUiState.Error).message,
      onRetry = { viewModel.refreshControllers() },
      modifier = Modifier.align(Alignment.Center)
     )
    }
    connectedControllers.isEmpty() -> {
     NoControllersMessage(
      modifier = Modifier.align(Alignment.Center)
     )
    }
    else -> {
     ControllerSettingsContent(
      connectedControllers = connectedControllers,
      activeController = activeController,
      profiles = profiles,
      joystickState = joystickState,
      onControllerSelect = { viewModel.setActiveController(it) },
      onProfileSelect = { viewModel.startEditProfile(it) },
      onCreateProfile = { viewModel.startCreateProfile() },
      onDeleteProfile = { showDeleteConfirmation = it }
     )
    }
   }
  }
 }

 // Profile editor dialog
 editingProfile?.let { profile ->
  ProfileEditorDialog(
   profile = profile,
   onDismiss = { viewModel.cancelEditProfile() },
   onSave = { viewModel.saveProfile() },
   onUpdateButtonMapping = { key, action -> viewModel.updateButtonAction(key, action) },
   onUpdateVibration = { viewModel.updateVibration(it) },
   onUpdateDeadzone = { viewModel.updateDeadzone(it) },
   onResetToDefault = { viewModel.resetToDefault() }
  )
 }

 // Delete confirmation dialog
 showDeleteConfirmation?.let { profile ->
  AlertDialog(
   onDismissRequest = { showDeleteConfirmation = null },
   title = { Text(stringResource(R.string.controller_delete_profile_title)) },
   text = { Text(stringResource(R.string.dialog_delete_game_message)) },
   confirmButton = {
    TextButton(
     onClick = {
      viewModel.deleteProfile(profile)
      showDeleteConfirmation = null
     }
    ) {
     Text(stringResource(R.string.button_delete))
    }
   },
   dismissButton = {
    TextButton(onClick = { showDeleteConfirmation = null }) {
     Text(stringResource(R.string.button_cancel))
    }
   }
  )
 }
}

@Composable
private fun ControllerSettingsContent(
 connectedControllers: List<Controller>,
 activeController: Controller?,
 profiles: List<ControllerProfile>,
 joystickState: JoystickState,
 onControllerSelect: (Controller) -> Unit,
 onProfileSelect: (ControllerProfile) -> Unit,
 onCreateProfile: () -> Unit,
 onDeleteProfile: (ControllerProfile) -> Unit
) {
 LazyColumn(
  modifier = Modifier.fillMaxSize(),
  contentPadding = PaddingValues(24.dp),
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  // Connected controllers section
  item {
   SectionHeader(stringResource(R.string.controller_section_connected))
  }

  items(
   items = connectedControllers,
   key = { controller -> controller.deviceId } // Stable key for proper list tracking
  ) { controller ->
   ControllerCard(
    controller = controller,
    isActive = controller.deviceId == activeController?.deviceId,
    onClick = { onControllerSelect(controller) }
   )
  }

  // Joystick state preview (if controller active)
  if (activeController != null) {
   item {
    Spacer(modifier = Modifier.height(8.dp))
    SectionHeader(stringResource(R.string.controller_section_joystick))
   }

   item {
    JoystickPreview(joystickState = joystickState)
   }
  }

  // Profiles section
  if (activeController != null) {
   item {
    Spacer(modifier = Modifier.height(8.dp))
    Row(
     modifier = Modifier.fillMaxWidth(),
     horizontalArrangement = Arrangement.SpaceBetween,
     verticalAlignment = Alignment.CenterVertically
    ) {
     SectionHeader(stringResource(R.string.controller_section_profiles))
     FilledTonalButton(onClick = onCreateProfile) {
      Icon(Icons.Default.Add, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(stringResource(R.string.controller_button_new_profile))
     }
    }
   }

   if (profiles.isEmpty()) {
    item {
     Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
       containerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      shape = MaterialTheme.shapes.large,
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
     ) {
      Box(
       modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp),
       contentAlignment = Alignment.Center
      ) {
       Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
       ) {
        Icon(
         Icons.Default.Settings,
         contentDescription = null,
         modifier = Modifier.size(48.dp),
         tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
         stringResource(R.string.controller_no_profiles),
         style = MaterialTheme.typography.bodyLarge,
         color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
         stringResource(R.string.controller_tap_to_create),
         style = MaterialTheme.typography.bodySmall,
         color = MaterialTheme.colorScheme.onSurfaceVariant
        )
       }
      }
     }
    }
   } else {
    items(
     items = profiles,
     key = { profile -> profile.id } // Stable key for proper list tracking
    ) { profile ->
     ProfileCard(
      profile = profile,
      onEdit = { onProfileSelect(profile) },
      onDelete = { onDeleteProfile(profile) }
     )
    }
   }
  }
 }
}

@Composable
private fun SectionHeader(text: String) {
 Text(
  text = text,
  style = MaterialTheme.typography.titleMedium,
  fontWeight = FontWeight.Bold,
  color = MaterialTheme.colorScheme.primary
 )
}

@Composable
private fun ControllerCard(
 controller: Controller,
 isActive: Boolean,
 onClick: () -> Unit
) {
 Card(
  onClick = onClick,
  modifier = Modifier.fillMaxWidth(),
  colors = if (isActive) {
   CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer
   )
  } else {
   CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant
   )
  },
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Row(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   Icon(
    imageVector = Icons.Default.PlayArrow,
    contentDescription = "controller",
    modifier = Modifier.size(48.dp),
    tint = if (isActive) {
     MaterialTheme.colorScheme.onPrimaryContainer
    } else {
     MaterialTheme.colorScheme.onSurfaceVariant
    }
   )

   Spacer(modifier = Modifier.width(16.dp))

   Column(modifier = Modifier.weight(1f)) {
    Text(
     text = controller.name,
     style = MaterialTheme.typography.titleMedium,
     fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
     text = "${controller.type.displayName} (ID: ${controller.deviceId})",
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onSurfaceVariant
    )
   }

   if (isActive) {
    Icon(
     Icons.Default.Check,
     contentDescription = "アクティブ",
     tint = MaterialTheme.colorScheme.primary
    )
   }
  }
 }
}

@Composable
private fun JoystickPreview(joystickState: JoystickState) {
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
   verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
   Text(
    "Left Stick",
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold
   )
   Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    AxisIndicator("X", joystickState.leftX, Modifier.weight(1f))
    AxisIndicator("Y", joystickState.leftY, Modifier.weight(1f))
   }

   Spacer(modifier = Modifier.height(8.dp))

   Text(
    "Right Stick",
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold
   )
   Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    AxisIndicator("X", joystickState.rightX, Modifier.weight(1f))
    AxisIndicator("Y", joystickState.rightY, Modifier.weight(1f))
   }

   Spacer(modifier = Modifier.height(8.dp))

   Text(
    "Trigger",
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold
   )
   Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    AxisIndicator("L2", joystickState.leftTrigger, Modifier.weight(1f))
    AxisIndicator("R2", joystickState.rightTrigger, Modifier.weight(1f))
   }
  }
 }
}

@Composable
private fun AxisIndicator(label: String, value: Float, modifier: Modifier = Modifier) {
 Column(modifier = modifier) {
  Text(
   text = label,
   style = MaterialTheme.typography.labelSmall
  )
  LinearProgressIndicator(
   progress = { (value + 1f) / 2f },
   modifier = Modifier.fillMaxWidth()
  )
  Text(
   text = String.format("%.2f", value),
   style = MaterialTheme.typography.bodySmall,
   color = MaterialTheme.colorScheme.onSurfaceVariant
  )
 }
}

@Composable
private fun ProfileCard(
 profile: ControllerProfile,
 onEdit: () -> Unit,
 onDelete: () -> Unit
) {
 Card(
  modifier = Modifier.fillMaxWidth(),
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
   verticalAlignment = Alignment.CenterVertically
  ) {
   Column(modifier = Modifier.weight(1f)) {
    Text(
     text = profile.name,
     style = MaterialTheme.typography.titleMedium,
     fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
     text = "Vibration: ${if (profile.vibrationEnabled) "Enabled" else "Disabled"} | Deadzone: ${String.format("%.1f", profile.deadzone * 100)}%",
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onSurfaceVariant
    )
   }

   IconButton(onClick = onEdit) {
    Icon(Icons.Default.Edit, contentDescription = "Edit")
   }

   IconButton(onClick = onDelete) {
    Icon(Icons.Default.Delete, contentDescription = "delete")
   }
  }
 }
}

@Composable
private fun ProfileEditorDialog(
 profile: ControllerProfile,
 onDismiss: () -> Unit,
 onSave: () -> Unit,
 onUpdateButtonMapping: (String, GameAction) -> Unit,
 onUpdateVibration: (Boolean) -> Unit,
 onUpdateDeadzone: (Float) -> Unit,
 onResetToDefault: () -> Unit
) {
 AlertDialog(
  onDismissRequest = onDismiss,
  title = { Text(stringResource(R.string.controller_edit_profile_title, profile.name)) },
  text = {
   LazyColumn(
    verticalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    item {
     Text(
      "ボタンマッピング",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold
     )
    }

    item {
     FilledTonalButton(onClick = onResetToDefault) {
      Text("default 戻す")
     }
    }

    item {
     HorizontalDivider()
    }

    item {
     Text(
      "Vibrationsettings",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold
     )
    }

    item {
     Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
     ) {
      Text("VibrationEnabled化")
      Switch(
       checked = profile.vibrationEnabled,
       onCheckedChange = onUpdateVibration
      )
     }
    }

    item {
     HorizontalDivider()
    }

    item {
     Text(
      "Deadzonesettings",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold
     )
    }

    item {
     Column {
      Slider(
       value = profile.deadzone,
       onValueChange = onUpdateDeadzone,
       valueRange = 0f..0.5f
      )
      Text(
       text = "${String.format("%.1f", profile.deadzone * 100)}%",
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
      )
     }
    }
   }
  },
  confirmButton = {
   Button(onClick = onSave) {
    Text("save")
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
private fun ErrorMessage(
 message: String,
 onRetry: () -> Unit,
 modifier: Modifier = Modifier
) {
 Column(
  modifier = modifier.padding(32.dp),
  horizontalAlignment = Alignment.CenterHorizontally,
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  Icon(
   Icons.Default.Warning,
   contentDescription = "Error",
   modifier = Modifier.size(64.dp),
   tint = MaterialTheme.colorScheme.error
  )
  Text(
   text = message,
   style = MaterialTheme.typography.titleLarge,
   fontWeight = FontWeight.Bold,
   color = MaterialTheme.colorScheme.error
  )
  Button(onClick = onRetry) {
   Text("retry")
  }
 }
}

@Composable
private fun NoControllersMessage(modifier: Modifier = Modifier) {
 Column(
  modifier = modifier.padding(32.dp),
  horizontalAlignment = Alignment.CenterHorizontally,
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  Icon(
   Icons.Default.Info,
   contentDescription = "情報",
   modifier = Modifier.size(64.dp),
   tint = MaterialTheme.colorScheme.outline
  )
  Text(
   text = "controller 検出されません した",
   style = MaterialTheme.typography.titleLarge,
   fontWeight = FontWeight.Bold,
   color = MaterialTheme.colorScheme.outline
  )
  Text(
   text = "controllerconnectionして「Update」ボタンタップplease",
   style = MaterialTheme.typography.bodyMedium,
   color = MaterialTheme.colorScheme.onSurfaceVariant
  )
 }
}
