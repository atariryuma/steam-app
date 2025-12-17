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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steamdeck.mobile.domain.model.*
import com.steamdeck.mobile.presentation.viewmodel.ControllerUiState
import com.steamdeck.mobile.presentation.viewmodel.ControllerViewModel

/**
 * Controller settings screen.
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

    var showProfileEditor by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf<ControllerProfile?>(null) }

    Scaffold(
        topBar = {
            ControllerSettingsTopBar(
                onBackClick = onBackClick,
                onRefresh = { viewModel.refreshControllers() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
    if (editingProfile != null) {
        ProfileEditorDialog(
            profile = editingProfile!!,
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
            title = { Text("プロファイルを削除") },
            text = { Text("「${profile.name}」を削除してもよろしいですか？この操作は取り消せません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile)
                        showDeleteConfirmation = null
                    }
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControllerSettingsTopBar(
    onBackClick: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = { Text("コントローラー設定") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "更新")
            }
        }
    )
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connected controllers section
        item {
            SectionHeader("接続済みコントローラー")
        }

        items(connectedControllers) { controller ->
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
                SectionHeader("ジョイスティックプレビュー")
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
                    SectionHeader("プロファイル")
                    FilledTonalButton(onClick = onCreateProfile) {
                        Icon(Icons.Default.Add, contentDescription = "追加")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新規作成")
                    }
                }
            }

            if (profiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
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
                                    contentDescription = "設定",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "プロファイルがありません",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "「新規作成」をタップして作成してください",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(profiles) { profile ->
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
        fontWeight = FontWeight.Bold
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
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "コントローラー",
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "左スティック",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AxisIndicator("X", joystickState.leftX, Modifier.weight(1f))
                AxisIndicator("Y", joystickState.leftY, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "右スティック",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AxisIndicator("X", joystickState.rightX, Modifier.weight(1f))
                AxisIndicator("Y", joystickState.rightY, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "トリガー",
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    text = "振動: ${if (profile.vibrationEnabled) "有効" else "無効"} | デッドゾーン: ${String.format("%.1f", profile.deadzone * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "編集")
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "削除")
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
        title = { Text("プロファイル編集: ${profile.name}") },
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
                        Text("デフォルトに戻す")
                    }
                }

                item {
                    HorizontalDivider()
                }

                item {
                    Text(
                        "振動設定",
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
                        Text("振動を有効化")
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
                        "デッドゾーン設定",
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
                Text("保存")
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
            contentDescription = "エラー",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Button(onClick = onRetry) {
            Text("再試行")
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "コントローラーが検出されませんでした",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "コントローラーを接続して「更新」ボタンをタップしてください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
