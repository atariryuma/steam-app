package com.steamdeck.mobile.presentation.ui.winlator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.presentation.viewmodel.WinlatorInitViewModel

/**
 * Winlator初期化ダイアログ
 *
 * 初回起動時にWinlator（Wine + Box64 + Rootfs）を展開・初期化する。
 * 展開サイズ: 約53MB
 * 所要時間: 2-3分
 *
 * Material3 Best Practices:
 * - Non-dismissible dialog (必須初期化)
 * - LinearProgressIndicator with determinate progress
 * - Clear status messages
 */
@Composable
fun WinlatorInitDialog(
    onComplete: () -> Unit,
    onError: (String) -> Unit,
    viewModel: WinlatorInitViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Handle completion/error states
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is WinlatorInitUiState.Completed -> {
                onComplete()
            }
            is WinlatorInitUiState.Error -> {
                onError(state.message)
            }
            else -> {}
        }
    }

    // Auto-start initialization
    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    Dialog(
        onDismissRequest = {}, // Non-dismissible
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Winlatorを初期化中",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                when (val state = uiState) {
                    is WinlatorInitUiState.Initializing -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            text = state.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (state.progress < 0.5f) {
                            Text(
                                text = "初回のみ2-3分かかります...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is WinlatorInitUiState.Idle -> {
                        CircularProgressIndicator()
                        Text(
                            text = "準備中...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    is WinlatorInitUiState.CheckingAvailability -> {
                        CircularProgressIndicator()
                        Text(
                            text = "Winlatorの状態を確認中...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    is WinlatorInitUiState.AlreadyInitialized -> {
                        Text(
                            text = "✓ Winlatorは既に初期化済みです",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is WinlatorInitUiState.Completed -> {
                        Text(
                            text = "✓ 初期化完了",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is WinlatorInitUiState.Error -> {
                        Text(
                            text = "エラーが発生しました",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { onError(state.message) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
    }
}
