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
 * 免責事項ダイアログ
 *
 * Steam利用規約違反リスクについてユーザーに警告
 *
 * Best Practice:
 * - 初回起動時に表示（DataStoreでフラグ管理）
 * - 同意しない場合はアプリを終了
 * - 重要な法的情報のため、スクロール可能に
 */
@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = { /* ダイアログ外タップ無効 */ },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF9800) // Orange warning
            )
        },
        title = {
            Text(
                text = "重要な法的通知",
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
                    text = "本アプリケーションはValve Corporationと提携、承認、または推奨されていません。",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Steam®、Steamロゴ、Steam Deck™、および関連するマークは、" +
                            "米国およびその他の国におけるValve Corporationの商標および/または" +
                            "登録商標です。",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "本アプリを使用することで、以下を認識したとみなされます:",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                val risks = listOf(
                    "これは非公式のサードパーティアプリケーションです",
                    "Steam利用規約に違反する可能性があります",
                    "Steamアカウントが停止またはBANされる可能性があります",
                    "全てのリスクはユーザー自身が負うものとします"
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
                    text = "本アプリは「現状のまま」提供され、いかなる保証もありません。",
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
                Text("リスクを理解し同意します")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("同意しない")
            }
        },
        modifier = modifier
    )
}
