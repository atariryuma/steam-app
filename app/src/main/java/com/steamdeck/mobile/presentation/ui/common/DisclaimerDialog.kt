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
 * Disclaimerダイアログ
 *
 * Steam利用規約違反リスク ついてユーザー Warning
 *
 * Best Practice:
 * - 初回launch時 表示（DataStore フラグ管理）
 * - Disagree場合 アプリend
 * - 重要な法的情報 ため、スクロール可能 
 */
@Composable
fun DisclaimerDialog(
 onAccept: () -> Unit,
 onDecline: () -> Unit,
 modifier: Modifier = Modifier
) {
 AlertDialog(
  onDismissRequest = { /* ダイアログ外タップDisabled */ },
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
     text = "本アプリケーション Valve Corporation 提携、承認、また 推奨されていません。",
     style = MaterialTheme.typography.bodyMedium.copy(
      fontWeight = FontWeight.Bold
     ),
     color = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
     text = "Steam®、Steamロゴ、Steam Deck™、および関連doマーク 、" +
       "米国およびそ 他 国 おけるValve Corporation 商標および/また " +
       "登録商標 す。",
     style = MaterialTheme.typography.bodySmall
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
     text = "本アプリusedoこ 、以下認識した みなされます:",
     style = MaterialTheme.typography.bodyMedium.copy(
      fontWeight = FontWeight.Bold
     )
    )

    Spacer(modifier = Modifier.height(8.dp))

    val risks = listOf(
     "これ 非公式 サードパーティアプリケーション す",
     "Steam利用規約 違反do可能性 あります",
     "Steamアカウント stopまた BANされる可能性 あります",
     "全て リスク ユーザー自身 負うも します"
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
     text = "本アプリ 「現状 まま」提供され、いかなる保証もありません。",
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
    Text("リスク理解し同意します")
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
