package com.steamdeck.mobile.presentation.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * ゲーム追加ダイアログ - BackboneOne風フルスクリーンダイアログ
 *
 * 横画面最適化:
 * - フルスクリーンDialog（usePlatformDefaultWidth = false）
 * - 2カラムレイアウト（横画面では左右分割）
 * - スクロール可能
 *
 * ベストプラクティス:
 * - 状態所有権の明確化（親が選択パスを管理、子が表示状態のみ管理）
 * - Derived stateでパス表示を管理
 * - Material3 Card design: elevation 2dp, padding 20dp
 *
 * @param onDismiss ダイアログを閉じる
 * @param onConfirm ゲーム情報を確定 (name, executablePath, installPath)
 * @param onSelectExecutable 実行ファイル選択ボタンが押された
 * @param onSelectInstallFolder インストールフォルダ選択ボタンが押された
 * @param selectedExecutablePath 選択された実行ファイルパス（親から渡される、変更不可）
 * @param selectedInstallPath 選択されたインストールパス（親から渡される、変更不可）
 */
@Composable
fun AddGameDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, executablePath: String, installPath: String) -> Unit,
    onSelectExecutable: () -> Unit = {},
    onSelectInstallFolder: () -> Unit = {},
    selectedExecutablePath: String = "",
    selectedInstallPath: String = ""
) {
    // ダイアログ内部の状態
    var gameName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // 表示用のパス文字列（Derived state）
    val displayExecutablePath = remember(selectedExecutablePath) {
        if (selectedExecutablePath.isNotBlank()) {
            if (selectedExecutablePath.startsWith("content://")) {
                selectedExecutablePath.substringAfterLast("/").take(40)
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
                selectedInstallPath.substringAfterLast("/").take(40)
            } else {
                selectedInstallPath
            }
        } else {
            ""
        }
    }

    // フルスクリーンDialog（横画面対応）
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // フルスクリーン化
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
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
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "戻る",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "ゲームを追加",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 追加ボタン（ヘッダー内）
                    Button(
                        onClick = {
                            if (gameName.isNotBlank() &&
                                selectedExecutablePath.isNotBlank() &&
                                selectedInstallPath.isNotBlank()) {
                                onConfirm(gameName, selectedExecutablePath, selectedInstallPath)
                            } else {
                                showError = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("追加")
                    }
                }

                // コンテンツエリア（スクロール可能）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ゲーム名入力カード
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
                            Text(
                                text = "ゲーム名",
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
                                placeholder = { Text("例: Portal 2") },
                                isError = showError && gameName.isBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    // ファイル選択カード
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
                            Text(
                                text = "実行ファイル",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = displayExecutablePath,
                                onValueChange = { },
                                placeholder = { Text("ファイル選択ボタンから選択") },
                                isError = showError && selectedExecutablePath.isBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = onSelectExecutable) {
                                        Icon(Icons.Default.Folder, contentDescription = "ファイル選択")
                                    }
                                }
                            )
                        }
                    }

                    // フォルダ選択カード
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
                            Text(
                                text = "インストールフォルダ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = displayInstallPath,
                                onValueChange = { },
                                placeholder = { Text("フォルダ選択ボタンから選択") },
                                isError = showError && selectedInstallPath.isBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = onSelectInstallFolder) {
                                        Icon(Icons.Default.Folder, contentDescription = "フォルダ選択")
                                    }
                                }
                            )
                        }
                    }

                    // エラーメッセージ
                    if (showError) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "すべての項目を入力してください",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }

                    // 説明カード
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                                text = "注意事項",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "• Windows .exeファイルを指定してください\n• インストールパスはゲームのルートフォルダです\n• ファイルピッカーでファイル/フォルダを選択してください",
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
