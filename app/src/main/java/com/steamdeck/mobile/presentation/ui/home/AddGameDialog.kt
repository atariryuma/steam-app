package com.steamdeck.mobile.presentation.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ゲーム追加ダイアログ
 *
 * ユーザーが手動でゲームを追加するためのダイアログ
 *
 * ベストプラクティス適用:
 * - 状態所有権の明確化（親が選択パスを管理、子が表示状態のみ管理）
 * - LaunchedEffectの適切な使用（keyはダイアログの表示/非表示のみ）
 * - Derived stateでパス表示を管理
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
    // ダイアログ内部の状態（ダイアログが閉じるとリセットされる）
    var gameName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // 表示用のパス文字列
    // ベストプラクティス: Derived state - 親から渡されたパスを表示用にフォーマット
    // URIの場合は最後の部分だけ表示、ユーザー入力の場合はそのまま表示
    val displayExecutablePath = remember(selectedExecutablePath) {
        if (selectedExecutablePath.isNotBlank()) {
            // content://スキームの場合はファイル名だけ抽出して表示
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
            // content://スキームの場合はディレクトリ名だけ抽出して表示
            if (selectedInstallPath.startsWith("content://")) {
                selectedInstallPath.substringAfterLast("/").take(40)
            } else {
                selectedInstallPath
            }
        } else {
            ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "ゲーム追加",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "ゲームを追加",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ゲーム名入力
                OutlinedTextField(
                    value = gameName,
                    onValueChange = {
                        gameName = it
                        showError = false
                    },
                    label = { Text("ゲーム名") },
                    placeholder = { Text("例: Portal 2") },
                    isError = showError && gameName.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 実行ファイルパス選択
                // Note: 読み取り専用。ユーザーはボタンからファイルピッカーを起動する
                OutlinedTextField(
                    value = displayExecutablePath,
                    onValueChange = { /* 読み取り専用 */ },
                    label = { Text("実行ファイル") },
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

                // インストールパス選択
                // Note: 読み取り専用。ユーザーはボタンからフォルダピッカーを起動する
                OutlinedTextField(
                    value = displayInstallPath,
                    onValueChange = { /* 読み取り専用 */ },
                    label = { Text("インストールフォルダ") },
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

                if (showError) {
                    Text(
                        text = "すべての項目を入力してください",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 説明テキスト
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "注意:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "• Windows .exeファイルを指定してください\n• インストールパスはゲームのルートフォルダです",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // バリデーション: すべての項目が入力されているか確認
                    if (gameName.isNotBlank() &&
                        selectedExecutablePath.isNotBlank() &&
                        selectedInstallPath.isNotBlank()) {
                        // 親から渡された実際のパス（URI）を使用
                        onConfirm(gameName, selectedExecutablePath, selectedInstallPath)
                    } else {
                        showError = true
                    }
                }
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
