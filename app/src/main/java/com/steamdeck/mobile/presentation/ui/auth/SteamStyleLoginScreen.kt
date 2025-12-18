package com.steamdeck.mobile.presentation.ui.auth

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Steam風フルスクリーンログイン画面
 *
 * デザイン参考: Steam公式ログイン画面
 * - ダークブルー/ブラックグラデーション背景
 * - 中央配置のログインカード
 * - QRコード（右側）とフォーム（左側）の2カラムレイアウト
 * - Googleパスワードマネージャー対応
 *
 * Best Practice:
 * - Autofill support: https://developer.android.com/identity/sign-in/credential-manager
 * - Password autofill: https://developer.android.com/identity/sign-in/credential-manager-siwg
 */
@Composable
fun SteamStyleLoginScreen(
    qrCodeBitmap: Bitmap?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onNavigateBack: () -> Unit = {},
    onErrorDismiss: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // エラーメッセージをSnackbarで表示
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
            onErrorDismiss()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1B2838), // Steam dark blue
                            Color(0xFF0D1217)  // Almost black
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Steam Logo（縮小）
                Text(
                    text = "STEAM",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        letterSpacing = 4.sp
                    ),
                    color = Color(0xFF66C0F4) // Steam blue
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Deck Mobile",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF8F98A0) // Steam gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                // モバイルアプリでログイン
                Text(
                    text = "モバイルアプリでログイン",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Steamモバイルアプリで\nこのQRコードをスキャン",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8F98A0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // QR Code Card（サイズ維持）
                Card(
                    modifier = Modifier.size(220.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrCodeBitmap != null) {
                            Image(
                                bitmap = qrCodeBitmap.asImageBitmap(),
                                contentDescription = "Steam QRコード",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(
                                color = Color(0xFF66C0F4)
                            )
                        }
                    }
                }
            }
        }
    }
}
