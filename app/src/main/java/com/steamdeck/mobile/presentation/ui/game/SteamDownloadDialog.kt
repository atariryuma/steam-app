package com.steamdeck.mobile.presentation.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steamdeck.mobile.domain.model.Game

/**
 * Steamゲームダウンロード確認ダイアログ
 *
 * Phase 5B実装版:
 * - Steam CDN直接ダウンロード対応
 * - 代替手段としてファイルインポート誘導
 * - ダウンロード進捗表示へのナビゲーション
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
                    contentDescription = "ダウンロード",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Steamゲームのダウンロード",
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
                                text = "Steam CDNから直接ダウンロード",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "実験的機能: Steam CDNからゲームファイルをダウンロードします。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "⚠️ 大容量のダウンロードになる場合があります。Wi-Fi環境を推奨します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "または代替方法:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "• PCでSteamからダウンロード → ファイル転送",
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
                    Text("ダウンロード開始")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateToImport) {
                        Text("ファイルインポート")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル")
                    }
                }
            }
        )
    }
}
