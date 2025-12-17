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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    onLogin: (email: String, password: String, rememberMe: Boolean) -> Unit,
    onQrCodeLogin: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    onRegenerateQrCode: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onErrorDismiss: () -> Unit = {}
) {
    // State for form inputs
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showQrCode by remember { mutableStateOf(true) } // デフォルトでQRコード表示

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }

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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Steam Logo placeholder
                Text(
                    text = "STEAM",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 48.sp,
                        letterSpacing = 4.sp
                    ),
                    color = Color(0xFF66C0F4) // Steam blue
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Deck Mobile",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF8F98A0) // Steam gray
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Main login card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A).copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                    ) {
                        // Left side: Login form
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text(
                                text = "サインイン",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Email field
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = {
                                    Text(
                                        "Steamアカウント名またはメールアドレス",
                                        color = Color(0xFF8F98A0)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = Color(0xFF32353C),
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF66C0F4),
                                    unfocusedBorderColor = Color(0xFF4A5A68),
                                    cursorColor = Color(0xFF66C0F4),
                                    focusedLabelColor = Color(0xFF66C0F4),
                                    unfocusedLabelColor = Color(0xFF8F98A0)
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { passwordFocusRequester.requestFocus() }
                                ),
                                enabled = !isLoading
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Password field
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = {
                                    Text("パスワード", color = Color(0xFF8F98A0))
                                },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) {
                                                Icons.Default.Visibility
                                            } else {
                                                Icons.Default.VisibilityOff
                                            },
                                            contentDescription = if (passwordVisible) {
                                                "パスワードを隠す"
                                            } else {
                                                "パスワードを表示"
                                            },
                                            tint = Color(0xFF8F98A0)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(passwordFocusRequester)
                                    .background(
                                        color = Color(0xFF32353C),
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF66C0F4),
                                    unfocusedBorderColor = Color(0xFF4A5A68),
                                    cursorColor = Color(0xFF66C0F4),
                                    focusedLabelColor = Color(0xFF66C0F4),
                                    unfocusedLabelColor = Color(0xFF8F98A0)
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (email.isNotBlank() && password.isNotBlank()) {
                                            onLogin(email, password, rememberMe)
                                        }
                                    }
                                ),
                                enabled = !isLoading
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Remember me checkbox
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF66C0F4),
                                        uncheckedColor = Color(0xFF4A5A68),
                                        checkmarkColor = Color.White
                                    ),
                                    enabled = !isLoading
                                )
                                Text(
                                    text = "このコンピューターでアカウントを記憶する",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF8F98A0)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Sign in button
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onLogin(email, password, rememberMe)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5C7E10), // Steam green
                                    disabledContainerColor = Color(0xFF4A5A68)
                                ),
                                shape = RoundedCornerShape(4.dp),
                                enabled = !isLoading && email.isNotBlank() && password.isNotBlank()
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "サインイン",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Help link
                            TextButton(
                                onClick = onForgotPassword,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading
                            ) {
                                Text(
                                    text = "ログインできませんか？",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF66C0F4)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Note about QR code
                            Text(
                                text = "または右側のQRコードをスキャン →",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8F98A0),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right side: QR code (always visible)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "モバイルアプリでログイン",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Steamモバイルアプリで\nこのQRコードをスキャン",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF8F98A0),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // QR Code display
                            Card(
                                modifier = Modifier.size(220.dp),
                                shape = RoundedCornerShape(8.dp),
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
                                        CircularProgressIndicator(
                                            color = Color(0xFF66C0F4)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(
                                onClick = onRegenerateQrCode,
                                enabled = !isLoading
                            ) {
                                Text(
                                    text = "QRコードを再生成",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF66C0F4)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer note
                Text(
                    text = "Steam Deck Mobile は非公式アプリです\nValve Corporation とは関係ありません",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4A5A68),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
