package com.steamdeck.mobile.presentation.ui.auth

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Steam風ログイン画面
 *
 * レイアウト:
 * - QRコード表示（優先）
 * - パスワードログイン（折りたたみ）
 *
 * Best Practice: Material3 Design System
 * Fullscreen mode - No TopAppBar for maximum screen space
 * Reference: https://m3.material.io/develop
 */
@Composable
fun SteamLoginScreen(
    qrCodeBitmap: Bitmap?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLogin: (username: String, password: String, rememberMe: Boolean) -> Unit,
    onForgotPassword: () -> Unit = {},
    onRegenerateQrCode: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onErrorDismiss: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // エラーメッセージをSnackbarで表示
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            // Snackbarを表示して、表示完了後にエラーをクリア
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
            // Snackbar表示完了後にエラー状態をクリア
            onErrorDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // QRコードセクション（優先表示）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "モバイルSteamアプリでログイン",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // QRコード表示
                    Card(
                        modifier = Modifier.size(280.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        )
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
                                CircularProgressIndicator()
                            }
                        }
                    }

                    Text(
                        text = "Steamモバイルアプリでこの\nQRコードをスキャンしてログイン",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // QRコード再生成ボタン
                    OutlinedButton(
                        onClick = onRegenerateQrCode,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("QRコードを再生成")
                    }
                }
            }

            // パスワードログインセクション（折りたたみ）
            var showPasswordLogin by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = { showPasswordLogin = !showPasswordLogin },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showPasswordLogin)
                                "パスワードログインを非表示"
                            else
                                "パスワードでログイン（非推奨）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (showPasswordLogin) {
                        // アカウント名入力
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("アカウント名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        // パスワード入力
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("パスワード") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Default.Visibility
                                        } else {
                                            Icons.Default.VisibilityOff
                                        },
                                        contentDescription = if (passwordVisible) "パスワードを隠す" else "パスワードを表示"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        // アカウントを記憶するチェックボックス
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                enabled = !isLoading
                            )
                            Text(
                                text = "このアカウントを記憶する",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // ログインボタン
                        Button(
                            onClick = { onLogin(username, password, rememberMe) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("ログイン")
                            }
                        }

                        // パスワード忘れリンク
                        TextButton(
                            onClick = onForgotPassword,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text(
                                text = "ログインできませんか。助けてください",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Snackbar表示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
